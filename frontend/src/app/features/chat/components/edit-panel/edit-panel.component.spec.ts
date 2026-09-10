import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { WritableSignal, signal } from '@angular/core';

import { EditPanelComponent } from './edit-panel.component';
import { EditStateService } from '../../services/edit-state.service';
import {
  ApplyEditsResponse,
  EditFlowState,
  EditPlanResponse,
  EditPreview,
} from '../../../../core/models/edit.model';

/**
 * A hand-rolled stand-in for EditStateService: every signal the panel reads is
 * a WritableSignal the test can drive, and every method is a spy. This keeps
 * the panel test about rendering + user intent, with the state machine itself
 * covered in edit-state.service.spec.ts.
 */
class FakeEditState {
  state: WritableSignal<EditFlowState> = signal<EditFlowState>('idle');
  isBusy = signal(false);
  plan: WritableSignal<EditPlanResponse | null> = signal<EditPlanResponse | null>(null);
  instruction = signal('');
  errorMessage: WritableSignal<string | null> = signal<string | null>(null);
  conflictProblems: WritableSignal<string[]> = signal<string[]>([]);
  applyResult: WritableSignal<ApplyEditsResponse | null> = signal<ApplyEditsResponse | null>(null);
  repositoryRoot: WritableSignal<string | null> = signal<string | null>('E:\\repos\\codepilot');
  hasRepository = signal(true);
  canApply = signal(false);

  planEdits = jasmine.createSpy('planEdits');
  planAgain = jasmine.createSpy('planAgain');
  applyPlan = jasmine.createSpy('applyPlan');
  discard = jasmine.createSpy('discard');
}

function validPreview(overrides: Partial<EditPreview> = {}): EditPreview {
  return {
    relativeFilePath: 'src/main/java/com/codepilot/chat/service/ChatService.java',
    valid: true,
    problem: null,
    unifiedDiff: '--- a/ChatService.java\n+++ b/ChatService.java\n@@ -1,2 +1,3 @@\n a\n-b\n+c\n',
    searchText: 'b',
    replaceText: 'c',
    lastModifiedMs: 1757612345678,
    ...overrides,
  };
}

describe('EditPanelComponent', () => {
  let fixture: ComponentFixture<EditPanelComponent>;
  let component: EditPanelComponent;
  let fake: FakeEditState;

  beforeEach(async () => {
    fake = new FakeEditState();
    await TestBed.configureTestingModule({
      imports: [EditPanelComponent],
      providers: [{ provide: EditStateService, useValue: fake }],
    }).compileComponents();

    fixture = TestBed.createComponent(EditPanelComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
  });

  afterEach(() => fixture.destroy());

  function html(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function textOf(selector: string): string {
    return html().querySelector(selector)?.textContent?.trim() ?? '';
  }

  it('renders nothing while closed', () => {
    fixture.componentRef.setInput('open', false);
    fixture.detectChanges();
    expect(html().querySelector('.panel')).toBeNull();
  });

  it('explains that a repository is required instead of hiding the feature', () => {
    fake.hasRepository.set(false);
    fixture.detectChanges();

    expect(html().textContent).toContain('AI editing works against an indexed repository');
    const attachBtn = fixture.debugElement.query(By.css('.btn--primary'));
    expect(attachBtn.nativeElement.textContent).toContain('Attach a repository');

    let asked = false;
    component.attachRepoRequested.subscribe(() => (asked = true));
    attachBtn.nativeElement.click();
    expect(asked).toBeTrue();
  });

  it('submits a trimmed instruction to the state service', () => {
    component.draft = '  add null-checking to ChatService.chat  ';
    fixture.detectChanges();

    fixture.debugElement.query(By.css('.btn--primary')).nativeElement.click();

    expect(fake.planEdits).toHaveBeenCalledWith('add null-checking to ChatService.chat');
  });

  it('shows an honest, growing progress state while planning', () => {
    fake.state.set('planning');
    fake.instruction.set('add null-checking to ChatService.chat');
    fixture.detectChanges();

    expect(html().querySelector('.spinner')).not.toBeNull();
    expect(html().textContent).toContain('10–40 seconds');
    expect(html().textContent).toContain('0s elapsed');
    expect(html().textContent).toContain('add null-checking to ChatService.chat');
  });

  it('renders the summary and one section per preview, each labelled with its path', () => {
    fake.state.set('reviewing');
    fake.plan.set({
      summary: 'Add null guard to ChatService.chat',
      applicable: true,
      previews: [
        validPreview(),
        validPreview({ relativeFilePath: 'src/App.java', searchText: 'x', replaceText: 'y' }),
      ],
    });
    fake.canApply.set(true);
    fixture.detectChanges();

    expect(textOf('.review-summary-text')).toBe('Add null guard to ChatService.chat');
    const paths = Array.from(html().querySelectorAll('.preview-path')).map((n) => n.textContent);
    expect(paths).toEqual([
      'src/main/java/com/codepilot/chat/service/ChatService.java',
      'src/App.java',
    ]);
    expect(html().querySelectorAll('app-edit-diff').length).toBe(2);
  });

  it('renders an invalid preview as a blocked problem message, not a diff', () => {
    fake.state.set('reviewing');
    fake.plan.set({
      summary: 'Attempted change',
      applicable: false,
      previews: [
        validPreview(),
        validPreview({
          relativeFilePath: 'src/Missing.java',
          valid: false,
          problem: 'SEARCH text not found in file',
          unifiedDiff: null,
          lastModifiedMs: 0,
        }),
      ],
    });
    fake.canApply.set(false);
    fixture.detectChanges();

    const blocked = html().querySelector('.preview--blocked')!;
    expect(blocked.querySelector('app-edit-diff')).toBeNull();
    expect(blocked.querySelector('.preview-problem')!.textContent).toContain(
      'SEARCH text not found in file'
    );
    expect(blocked.querySelector('.preview-badge--blocked')!.textContent!.trim()).toBe('blocked');
  });

  it('disables Apply when the plan is not applicable', () => {
    fake.state.set('reviewing');
    fake.plan.set({ summary: 's', applicable: false, previews: [validPreview({ valid: false, unifiedDiff: null, problem: 'nope' })] });
    fake.canApply.set(false);
    fixture.detectChanges();

    const applyBtn = fixture.debugElement.query(By.css('.btn--danger')).nativeElement as HTMLButtonElement;
    expect(applyBtn.disabled).toBeTrue();
    applyBtn.click();
    expect(fake.applyPlan).not.toHaveBeenCalled();
  });

  it('requires a confirmation step before it will apply', () => {
    fake.state.set('reviewing');
    fake.plan.set({ summary: 's', applicable: true, previews: [validPreview()] });
    fake.canApply.set(true);
    fixture.detectChanges();

    // First click only reveals the confirmation — nothing is applied yet.
    fixture.debugElement.query(By.css('.btn--danger')).nativeElement.click();
    fixture.detectChanges();
    expect(fake.applyPlan).not.toHaveBeenCalled();
    expect(html().querySelector('.confirm')).not.toBeNull();
    expect(html().querySelector('.confirm')!.textContent).toContain('Write 1 file to disk?');

    // Confirming inside the dialog actually applies.
    const confirmBtn = Array.from(
      html().querySelectorAll('.confirm-actions .btn--danger')
    )[0] as HTMLButtonElement;
    confirmBtn.click();
    expect(fake.applyPlan).toHaveBeenCalledTimes(1);
  });

  it('shows the message, changed files and backup path after a successful apply', () => {
    fake.state.set('applied');
    fake.applyResult.set({
      applied: true,
      message: 'Applied edits to 2 file(s); 14 chunk(s) re-indexed',
      changedFiles: ['path/one.java', 'path/two.java'],
      backupLocation: 'E:\\repos\\codepilot\\.codepilot-backups\\20260910-143022',
      problems: [],
    });
    fixture.detectChanges();

    const text = html().textContent!;
    expect(text).toContain('Applied edits to 2 file(s); 14 chunk(s) re-indexed');
    expect(text).toContain('path/one.java');
    expect(text).toContain('path/two.java');
    expect(text).toContain('.codepilot-backups\\20260910-143022');
    // No Apply affordance survives a successful apply.
    expect(html().querySelector('.btn--danger')).toBeNull();
  });

  it('renders a 409 conflict as its problem list with a "Plan again" action and no generic error', () => {
    fake.state.set('conflict');
    fake.conflictProblems.set([
      'ChatService.java: file changed on disk since the edit was planned - re-plan the edit',
      'App.java: file changed on disk since the edit was planned - re-plan the edit',
    ]);
    fixture.detectChanges();

    const items = Array.from(html().querySelectorAll('.problem-list li')).map((n) =>
      n.textContent!.trim()
    );
    expect(items.length).toBe(2);
    expect(items[0]).toContain('ChatService.java');

    const planAgain = fixture.debugElement.query(By.css('.btn--primary')).nativeElement as HTMLButtonElement;
    expect(planAgain.textContent).toContain('Plan again');
    planAgain.click();
    expect(fake.planAgain).toHaveBeenCalled();
  });

  it('offers Discard while reviewing and forwards it to the state service', () => {
    fake.state.set('reviewing');
    fake.plan.set({ summary: 's', applicable: true, previews: [validPreview()] });
    fake.canApply.set(true);
    fixture.detectChanges();

    fixture.debugElement.query(By.css('.btn--ghost')).nativeElement.click();
    expect(fake.discard).toHaveBeenCalled();
  });
});
