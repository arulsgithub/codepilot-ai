import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageListComponent } from './message-list.component';
import { ChatMessageViewModel } from '../../../../core/models/message.model';

describe('MessageListComponent', () => {
  let fixture: ComponentFixture<MessageListComponent>;
  let component: MessageListComponent;

  const userMessage: ChatMessageViewModel = {
    clientId: 'u1',
    role: 'USER',
    content: 'Explain interfaces',
    createdAt: '2026-01-01T00:00:00Z',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MessageListComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(MessageListComponent);
    component = fixture.componentInstance;
  });

  it('trackByClientId returns the stable clientId, not array index', () => {
    expect(component.trackByClientId(0, userMessage)).toBe('u1');
    expect(component.trackByClientId(5, userMessage)).toBe('u1');
  });

  it('shows the thinking indicator only in the "sending" chat state', () => {
    component.chatState = 'sending';
    expect(component.showThinkingIndicator).toBeTrue();

    component.chatState = 'streaming';
    expect(component.showThinkingIndicator).toBeFalse();

    component.chatState = 'idle';
    expect(component.showThinkingIndicator).toBeFalse();
  });

  it('renders a skeleton state while loading and hides messages', () => {
    component.loading = true;
    component.messages = [userMessage];
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.skeleton-list')).not.toBeNull();
    expect(compiled.querySelector('app-message-item')).toBeNull();
  });

  it('renders the error banner with a working Retry button', () => {
    component.loading = false;
    component.messages = [userMessage];
    component.error = 'CodePilot could not generate a response. Please try again.';
    fixture.detectChanges();

    let retried = false;
    component.retryRequested.subscribe(() => (retried = true));

    const compiled = fixture.nativeElement as HTMLElement;
    const retryButton = compiled.querySelector<HTMLButtonElement>('.retry-button');
    expect(retryButton).not.toBeNull();
    retryButton?.click();

    expect(retried).toBeTrue();
  });
});
