import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { WritableSignal, computed, signal } from '@angular/core';

import { EditStateService } from './edit-state.service';
import { EditService } from '../../../core/services/edit.service';
import { ChatStateService } from './chat-state.service';
import { RepositorySourceType } from '../../../core/models/repository.model';
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
  let repositoryId: WritableSignal<string | null>;
  let repositorySourceType: WritableSignal<RepositorySourceType | null>;

  beforeEach(() => {
    planSpy = jasmine.createSpy('planEdits');
    applySpy = jasmine.createSpy('applyEdits');
    repositoryRoot = signal<string | null>('E:\\repos\\codepilot');
    // Default: a hand-attached path (no registry id) — the legacy flow.
    repositoryId = signal<string | null>(null);
    repositorySourceType = signal<RepositorySourceType | null>(null);

    TestBed.configureTestingModule({
      providers: [
        EditStateService,
        { provide: EditService, useValue: { planEdits: planSpy, applyEdits: applySpy } },
        {
          provide: ChatStateService,
          useValue: {
            repositoryRoot: repositoryRoot.asReadonly(),
            repositoryAttached: computed(() => repositoryRoot() !== null),
            repositoryId: repositoryId.asReadonly(),
            repositorySourceType: repositorySourceType.asReadonly(),
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
      success: true,
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
              success: false,
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

  describe('delivery by registered repository (GitHub pull request flow)', () => {
    const REPO_ID = '3f0c2b7e-1111-4222-8333-444455556666';

    const PR_RESPONSE: ApplyEditsResponse = {
      success: true,
      message: 'Opened pull request https://github.com/o/r/pull/7',
      changedFiles: ['src/main/java/com/codepilot/chat/service/ChatService.java'],
      backupLocation: null,
      problems: [],
      sourceType: 'GITHUB',
      branch: 'codepilot/add-null-checking-1a2b3c',
      commitSha: '9f8e7d6c5b4a39281706f5e4d3c2b1a098765432',
      pullRequestUrl: 'https://github.com/o/r/pull/7',
    };

    beforeEach(() => {
      planSpy.and.returnValue(of(VALID_PLAN));
      service.planEdits('add null-checking to ChatService.chat');
    });

    it('sends repositoryId and the instruction for a registry repository, so the backend can open a PR', () => {
      repositoryId.set(REPO_ID);
      repositorySourceType.set('GITHUB');
      applySpy.and.returnValue(of(PR_RESPONSE));

      service.applyPlan();

      const request = applySpy.calls.mostRecent().args[0];
      expect(request.repositoryId).toBe(REPO_ID);
      // The instruction becomes the branch name, commit message and PR title.
      expect(request.instruction).toBe('add null-checking to ChatService.chat');
      expect(request.edits.length).toBe(1);
      expect(service.applyResult()).toEqual(PR_RESPONSE);
      expect(service.state()).toBe('applied');
    });

    it('also sends repositoryId for a registered LOCAL repository', () => {
      repositoryId.set(REPO_ID);
      repositorySourceType.set('LOCAL');
      applySpy.and.returnValue(of({ ...PR_RESPONSE, sourceType: 'LOCAL', pullRequestUrl: null }));

      service.applyPlan();

      expect(applySpy.calls.mostRecent().args[0].repositoryId).toBe(REPO_ID);
    });

    it('leaves a legacy path-only attachment exactly as before — no id, no instruction', () => {
      applySpy.and.returnValue(of(PR_RESPONSE));

      service.applyPlan();

      const request = applySpy.calls.mostRecent().args[0];
      expect(request.repositoryRoot).toBe('E:\\repos\\codepilot');
      expect('repositoryId' in request).toBeFalse();
      expect('instruction' in request).toBeFalse();
    });

    it('reports a GitHub target only for a GitHub repository', () => {
      expect(service.isGithubTarget()).toBeFalse();

      repositorySourceType.set('LOCAL');
      expect(service.isGithubTarget()).toBeFalse();

      repositorySourceType.set('GITHUB');
      expect(service.isGithubTarget()).toBeTrue();
    });

    it("shows the backend's own message for an anticipated failure (e.g. a repository that was never synced)", () => {
      repositoryId.set(REPO_ID);
      repositorySourceType.set('GITHUB');
      applySpy.and.returnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 502,
              error: {
                message: "Repository 'r' has no recorded branch. Sync it before applying edits.",
              },
            })
        )
      );

      service.applyPlan();

      expect(service.state()).toBe('error');
      expect(service.errorMessage()).toBe(
        "Repository 'r' has no recorded branch. Sync it before applying edits."
      );
    });

    it('keeps a reassuring fallback for a bare 500, worded for a pull request when GitHub', () => {
      repositoryId.set(REPO_ID);
      repositorySourceType.set('GITHUB');
      applySpy.and.returnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 500,
              error: { message: 'An unexpected error occurred.' },
            })
        )
      );

      service.applyPlan();

      expect(service.errorMessage()).toBe(
        'Opening the pull request failed. The base branch was not changed.'
      );
    });
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
