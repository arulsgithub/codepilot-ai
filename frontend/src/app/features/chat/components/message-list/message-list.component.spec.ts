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

  it('shows an empty-conversation state when there are no messages, no error, and nothing loading', () => {
    component.loading = false;
    component.messages = [];
    component.error = null;
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.empty-conversation')).not.toBeNull();
    expect(compiled.querySelector('app-message-item')).toBeNull();
  });

  // scrollHeight/clientHeight/scrollTop are native, layout-derived accessors
  // that don't respond to plain writes on a detached (non-rendered) test
  // fixture — replacing them with plain writable data properties lets the
  // test drive the same "user scrolled up" condition onScroll() checks for.
  function stubScrollGeometry(el: HTMLDivElement, opts: { scrollTop: number; scrollHeight: number; clientHeight: number }): void {
    Object.defineProperty(el, 'scrollTop', { value: opts.scrollTop, writable: true, configurable: true });
    Object.defineProperty(el, 'scrollHeight', { value: opts.scrollHeight, writable: true, configurable: true });
    Object.defineProperty(el, 'clientHeight', { value: opts.clientHeight, writable: true, configurable: true });
  }

  it('hides the "scroll to latest" affordance until the user scrolls away from the bottom', () => {
    component.loading = false;
    component.messages = [userMessage];
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.scroll-to-bottom')).toBeNull();

    const scrollEl = component.scrollContainer!.nativeElement;
    stubScrollGeometry(scrollEl, { scrollTop: 0, scrollHeight: 1000, clientHeight: 300 });

    // Dispatch a real scroll event (rather than calling onScroll() directly)
    // so it goes through the same (scroll) binding + zone-triggered change
    // detection a real user scroll would.
    scrollEl.dispatchEvent(new Event('scroll'));
    fixture.detectChanges();

    expect(component.showScrollToBottom).toBeTrue();
    expect(fixture.nativeElement.querySelector('.scroll-to-bottom')).not.toBeNull();
  });

  it('scrollToBottomClicked() re-pins to the bottom and hides the affordance', () => {
    component.loading = false;
    component.messages = [userMessage];
    fixture.detectChanges();

    const scrollEl = component.scrollContainer!.nativeElement;
    stubScrollGeometry(scrollEl, { scrollTop: 0, scrollHeight: 1000, clientHeight: 300 });
    scrollEl.dispatchEvent(new Event('scroll'));
    expect(component.showScrollToBottom).toBeTrue();

    component.scrollToBottomClicked();

    expect(component.showScrollToBottom).toBeFalse();
    expect(scrollEl.scrollTop).toBe(1000);
  });

  it('announces a completed turn via the polite live region, not per-token', () => {
    component.loading = false;
    component.messages = [userMessage];
    component.chatState = 'streaming';
    fixture.detectChanges();
    expect(component.liveRegionMessage).toBe('');

    // Angular updates the bound @Input before invoking ngOnChanges, so the
    // field is already 'completed' by the time the hook runs — mirror that.
    component.chatState = 'completed';
    component.ngOnChanges({
      chatState: {
        previousValue: 'streaming',
        currentValue: 'completed',
        firstChange: false,
        isFirstChange: () => false,
      },
    });

    expect(component.liveRegionMessage).toBe('CodePilot replied.');
  });
});
