import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MessageComposerComponent } from './message-composer.component';

describe('MessageComposerComponent', () => {
  let fixture: ComponentFixture<MessageComposerComponent>;
  let component: MessageComposerComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MessageComposerComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(MessageComposerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function getTextarea(): HTMLTextAreaElement {
    return fixture.debugElement.query(By.css('textarea')).nativeElement;
  }

  function getSendButton(): HTMLButtonElement {
    return fixture.debugElement.query(By.css('button.composer-send')).nativeElement;
  }

  it('disables the send button when the draft is empty', () => {
    expect(getSendButton().disabled).toBeTrue();
  });

  it('enables the send button once there is non-whitespace text', () => {
    component.draft = 'Explain interfaces';
    fixture.detectChanges();
    expect(getSendButton().disabled).toBeFalse();
  });

  it('keeps the send button disabled for whitespace-only input', () => {
    component.draft = '   ';
    fixture.detectChanges();
    expect(getSendButton().disabled).toBeTrue();
  });

  it('emits the trimmed message and clears the draft on submit()', () => {
    const emitted: string[] = [];
    component.messageSubmitted.subscribe((text) => emitted.push(text));

    component.draft = '  Explain Java interfaces  ';
    component.submit();

    expect(emitted).toEqual(['Explain Java interfaces']);
    expect(component.draft).toBe('');
  });

  it('does not emit when submit() is called with an empty draft', () => {
    const emitted: string[] = [];
    component.messageSubmitted.subscribe((text) => emitted.push(text));

    component.draft = '';
    component.submit();

    expect(emitted).toEqual([]);
  });

  it('does not emit while disabled, even if the draft has content', () => {
    component.disabled = true;
    component.draft = 'Explain generics';
    component.submit();

    expect(component.canSend).toBeFalse();
  });

  it('Enter without Shift sends the message and prevents the default newline', () => {
    component.draft = 'What is polymorphism?';
    fixture.detectChanges();

    const emitted: string[] = [];
    component.messageSubmitted.subscribe((text) => emitted.push(text));

    const event = new KeyboardEvent('keydown', { key: 'Enter', shiftKey: false, cancelable: true });
    component.onKeydown(event);

    expect(emitted).toEqual(['What is polymorphism?']);
    expect(event.defaultPrevented).toBeTrue();
  });

  it('Shift+Enter does not send and does not prevent default (allows newline)', () => {
    component.draft = 'line one';
    fixture.detectChanges();

    const emitted: string[] = [];
    component.messageSubmitted.subscribe((text) => emitted.push(text));

    const event = new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true, cancelable: true });
    component.onKeydown(event);

    expect(emitted).toEqual([]);
    expect(event.defaultPrevented).toBeFalse();
    expect(component.draft).toBe('line one');
  });

  it('reflects the disabled input on the textarea element', () => {
    component.disabled = true;
    fixture.detectChanges();
    expect(getTextarea().disabled).toBeTrue();
  });
});
