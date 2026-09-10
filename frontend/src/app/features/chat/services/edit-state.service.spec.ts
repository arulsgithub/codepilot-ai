import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { WritableSignal, computed, signal } from '@angular/core';

import { EditStateService } from './edit-state.service';
import { EditService } from '../../../core/services/edit.service';
import { ChatStateService } from './chat-state.service';
import {
  ApplyEditsResponse,
  EditPlanResponse,
  EditPreview,
} from '../../../core/models/edit.model';

function preview(overrides: Partial<EditPreview> = {}): EditPreview {
  return {
    relativeFilePath: 'src/main/java/com/codepilot/chat/service/ChatService.java',
    valid: true,
    problem: null,
    unifiedDiff: '--- a/ChatService.java\n+++ b/ChatService.java\n@@ -1,3 +1,4 @@\n a\n-b\n+c\n',
    searchText: 'b',
    replaceText: 'c',
    lastModifiedMs: 1757612345678,
    ...overrides,
  };
}

const VALID_PLAN: EditPlanResponse = {
  summary: 'Add null guard to ChatService.chat',
  applicable: true,
  previews: [preview()],
};

const BLOCKED_PLAN: EditPlanResponse = {
  summary: 'Attempted change',
  applicable: false,
  previews: [
    preview(),
    preview({
      relativeFilePath: 'src/Missing.java',
      valid: false,
      problem: 'SEARCH text not found in file (the model may have invented or abbreviated it)',
      unifiedDiff: null,
      lastModifiedMs: 0,
    }),
  ],
};

describe('EditStateService', () => {
  let service: EditStateService;
  let planSpy: jasmine.Spy;
  let applySpy: jasmine.Spy;
  let repositoryRoot: WritableSignal<string | null>;

  beforeEach(() => {
    planSpy = jasmine.createSpy('planEdits');
    applySpy = jasmine.createSpy('applyEdits');
    repositoryRoot = signal<string | null>('E:\\repos\\codepilot');

    TestBed.configureTestingModule({
      providers: [
        EditStateService,
        { provide: EditService, useValue: { planEdits: planSpy, applyEdits: applySpy } },
        {
          provide: ChatStateService,
          useValue: {
            repositoryRoot: repositoryRoot.asReadonly(),
            repositoryAttached: computed(() => repositoryRoot() !== null),
          },
        },
      ],
    });

    service = TestBed.inject(EditStateService);
  });

  it('moves idle → planning → reviewing and stores the plan', () => {
    planSpy.and.returnValue(of(VALID_PLAN));

    service.planEdits('add null-checking to ChatService.chat');

    expect(planSpy).toHaveBeenCalledWith(
      'E:\\repos\\codepilot',
      'add null-checking to ChatService.chat'
    );
    expect(service.state()).toBe('reviewing');
    expect(service.plan()).toEqual(VALID_PLAN);
    expect(service.canApply()).toBeTrue();
  });

  it('does not plan when no repository is attached', () => {
    repositoryRoot.set(null);
    service.planEdits('do something');
    expect(planSpy).not.toHaveBeenCalled();
    expect(service.state()).toBe('idle');
  });

  it('keeps Apply disabled when the plan is not applicable (an invalid preview)', () => {
    planSpy.and.returnValue(of(BLOCKED_PLAN));

    service.planEdits('change two files');

    expect(service.state()).toBe('reviewing');
    expect(service.plan()!.previews[1].valid).toBeFalse();
    expect(service.canApply()).toBeFalse();
  });

  it('does not send an apply request while the plan is not applicable', () => {
    planSpy.and.returnValue(of(BLOCKED_PLAN));
    service.planEdits('change two files');

    service.applyPlan();

    expect(applySpy).not.toHaveBeenCalled();
  });

  it('echoes every preview back verbatim on apply and consumes the plan on success', () => {
    planSpy.and.returnValue(of(VALID_PLAN));
    service.planEdits('add null-checking');

    const success: ApplyEditsResponse = {
      applied: true,
      message: 'Applied edits to 1 file(s); 4 chunk(s) re-indexed',
      changedFiles: ['src/main/java/com/codepilot/chat/service/ChatService.java'],
      backupLocation: 'E:\\repos\\codepilot\\.codepilot-backups\\20260910-143022',
      problems: [],
    };
    applySpy.and.returnValue(of(success));

    service.applyPlan();

    expect(applySpy).toHaveBeenCalledTimes(1);
    const request = applySpy.calls.mostRecent().args[0];
    expect(request.repositoryRoot).toBe('E:\\repos\\codepilot');
    expect(request.edits).toEqual([
      {
        relativeFilePath: 'src/main/java/com/codepilot/chat/service/ChatService.java',
        searchText: 'b',
        replaceText: 'c',
        expectedLastModifiedMs: 1757612345678,
      },
    ]);

    expect(service.state()).toBe('applied');
    expect(service.applyResult()).toEqual(success);
    // The plan is consumed so the UI cannot offer Apply a second time.
    expect(service.plan()).toBeNull();
    expect(service.canApply()).toBeFalse();
  });

  it('treats a 409 as a conflict, surfaces every problem, and does not enter the generic error state', () => {
    planSpy.and.returnValue(of(VALID_PLAN));
    service.planEdits('add null-checking');

    const problems = [
      'ChatService.java: file changed on disk since the edit was planned - re-plan the edit',
    ];
    applySpy.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: {
              applied: false,
              message: 'Edits were not applied',
              changedFiles: [],
              backupLocation: null,
              problems,
            },
          })
      )
    );

    service.applyPlan();

    expect(service.state()).toBe('conflict');
    expect(service.conflictProblems()).toEqual(problems);
    expect(service.errorMessage()).toBeNull();
  });

  it('re-plans with the same instruction via planAgain()', () => {
    planSpy.and.returnValue(of(VALID_PLAN));
    service.planEdits('add null-checking to ChatService.chat');
    planSpy.calls.reset();

    service.planAgain();

    expect(planSpy).toHaveBeenCalledWith(
      'E:\\repos\\codepilot',
      'add null-checking to ChatService.chat'
    );
  });

  it('enters the error state (not conflict) for a non-409 apply failure', () => {
    planSpy.and.returnValue(of(VALID_PLAN));
    service.planEdits('add null-checking');

    applySpy.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    service.applyPlan();

    expect(service.state()).toBe('error');
    expect(service.errorMessage()).toBeTruthy();
    expect(service.conflictProblems()).toEqual([]);
  });

  it('surfaces a plan failure as the error state', () => {
    planSpy.and.returnValue(throwError(() => new HttpErrorResponse({ status: 0 })));

    service.planEdits('do something');

    expect(service.state()).toBe('error');
    expect(service.errorMessage()).toContain('Unable to reach');
  });

  it('discard() clears everything back to idle', () => {
    planSpy.and.returnValue(of(VALID_PLAN));
    service.planEdits('add null-checking');

    service.discard();

    expect(service.state()).toBe('idle');
    expect(service.plan()).toBeNull();
    expect(service.applyResult()).toBeNull();
    expect(service.instruction()).toBe('');
  });

  it('ignores a second planEdits while one request is still in flight', () => {
    const pending = new Subject<EditPlanResponse>();
    planSpy.and.returnValue(pending.asObservable());

    service.planEdits('first');
    service.planEdits('second');

    expect(planSpy).toHaveBeenCalledTimes(1);
    expect(service.state()).toBe('planning');
  });
});
