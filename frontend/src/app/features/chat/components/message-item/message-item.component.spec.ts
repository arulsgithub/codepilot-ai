import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageItemComponent } from './message-item.component';
import { ChatMessageViewModel } from '../../../../core/models/message.model';

describe('MessageItemComponent', () => {
  let fixture: ComponentFixture<MessageItemComponent>;
  let component: MessageItemComponent;

  const assistantMessage: ChatMessageViewModel = {
    clientId: 'a1',
    role: 'ASSISTANT',
    content: 'Here is the answer.',
    createdAt: '2026-01-01T00:00:00Z',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MessageItemComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(MessageItemComponent);
    component = fixture.componentInstance;
  });

  function render(message: ChatMessageViewModel): void {
    fixture.componentRef.setInput('message', message);
    fixture.detectChanges();
  }

  it('copies the raw message text to the clipboard and shows a confirmation', async () => {
    spyOn(navigator.clipboard, 'writeText').and.returnValue(Promise.resolve());
    render(assistantMessage);

    component.copyMessage();
    await fixture.whenStable();

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('Here is the answer.');
    expect(component.copyState).toBe('copied');
  });

  it('falls back to a "failed" state if the clipboard write rejects', async () => {
    spyOn(navigator.clipboard, 'writeText').and.returnValue(Promise.reject('denied'));
    render(assistantMessage);

    component.copyMessage();
    await fixture.whenStable();

    expect(component.copyState).toBe('failed');
  });

  it('does not show a Regenerate action for a normal (non-errored) assistant message', () => {
    render(assistantMessage);
    expect(fixture.nativeElement.querySelector('[aria-label="Regenerate this response"]')).toBeNull();
  });

  it('shows Regenerate only for an errored assistant message, and it emits on click', () => {
    render({ ...assistantMessage, isError: true });

    const host = fixture.nativeElement as HTMLElement;
    const button = host.querySelector<HTMLButtonElement>('[aria-label="Regenerate this response"]');
    expect(button).not.toBeNull();

    let regenerated = false;
    component.regenerateRequested.subscribe(() => (regenerated = true));
    button!.click();

    expect(regenerated).toBeTrue();
  });

  it('never shows Regenerate for a USER message, even if flagged isError', () => {
    render({
      clientId: 'u1',
      role: 'USER',
      content: 'hi',
      createdAt: '2026-01-01T00:00:00Z',
      isError: true,
    });
    expect(fixture.nativeElement.querySelector('[aria-label="Regenerate this response"]')).toBeNull();
  });

  it('scrollToThis() scrolls the host element into view', () => {
    render(assistantMessage);
    const spy = spyOn(fixture.nativeElement, 'scrollIntoView');

    component.scrollToThis();

    expect(spy).toHaveBeenCalledWith({ behavior: 'smooth', block: 'center' });
  });
});
