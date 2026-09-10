import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EditDiffComponent } from './edit-diff.component';

const SAMPLE_DIFF = [
  '--- a/ChatService.java',
  '+++ b/ChatService.java',
  '@@ -10,7 +10,9 @@ public class ChatService {',
  '     public String chat(String message) {',
  '-        return orchestrator.run(message);',
  '+        if (message == null) {',
  '+            throw new IllegalArgumentException("message");',
  '+        }',
  '+        return orchestrator.run(message);',
  '     }',
].join('\n');

describe('EditDiffComponent', () => {
  let fixture: ComponentFixture<EditDiffComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EditDiffComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(EditDiffComponent);
  });

  function html(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function setDiff(diff: string): void {
    fixture.componentRef.setInput('unifiedDiff', diff);
    fixture.detectChanges();
  }

  it('classifies every line of the unified diff', () => {
    setDiff(SAMPLE_DIFF);
    const kinds = fixture.componentInstance.lines.map((l) => l.kind);
    expect(kinds).toEqual([
      'file',
      'file',
      'hunk',
      'context',
      'del',
      'add',
      'add',
      'add',
      'add',
      'context',
    ]);
  });

  it('gives additions a "+" gutter sign and removals a "-" sign (not colour alone)', () => {
    setDiff(SAMPLE_DIFF);

    const add = html().querySelector('.diff-line--add')!;
    expect(add.querySelector('.diff-sign')!.textContent).toBe('+');

    const del = html().querySelector('.diff-line--del')!;
    expect(del.querySelector('.diff-sign')!.textContent).toBe('-');
  });

  it('strips the leading +/- marker from the rendered code text', () => {
    setDiff(SAMPLE_DIFF);
    const del = html().querySelector('.diff-line--del .diff-text')!;
    expect(del.textContent).toBe('        return orchestrator.run(message);');
    expect(del.textContent!.startsWith('-')).toBeFalse();
  });

  it('renders the hunk header on its own line', () => {
    setDiff(SAMPLE_DIFF);
    const hunk = html().querySelector('.diff-line--hunk .diff-text')!;
    expect(hunk.textContent).toContain('@@ -10,7 +10,9 @@');
  });

  it('marks the code text as preformatted so long lines never wrap mid-line', () => {
    setDiff(SAMPLE_DIFF);
    const text = html().querySelector('.diff-text') as HTMLElement;
    expect(getComputedStyle(text).whiteSpace).toBe('pre');
  });

  it('handles an empty diff without throwing', () => {
    setDiff('');
    expect(fixture.componentInstance.lines.length).toBe(1);
    expect(fixture.componentInstance.lines[0].kind).toBe('context');
  });
});
