import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommandPaletteComponent } from './command-palette.component';
import { Conversation } from '../../../../core/models/conversation.model';

describe('CommandPaletteComponent', () => {
  let fixture: ComponentFixture<CommandPaletteComponent>;
  let component: CommandPaletteComponent;

  const conversations: Conversation[] = [
    { id: 'c1', title: 'Explain interfaces', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' },
    { id: 'c2', title: 'Debug NPE in ChatService', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CommandPaletteComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(CommandPaletteComponent);
    component = fixture.componentInstance;
    component.conversations = conversations;
  });

  function openPalette(): void {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
  }

  it('renders nothing when closed', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.palette')).toBeNull();
  });

  it('lists the static commands plus every conversation when opened with an empty query', () => {
    openPalette();
    const labels = component.results.map((r) => r.label);
    expect(labels).toContain('New chat');
    expect(labels).toContain('Explain interfaces');
    expect(labels).toContain('Debug NPE in ChatService');
  });

  it('resets the query and selection each time it is opened', () => {
    openPalette();
    component.query = 'debug';
    component.onQueryChange();
    component.activeIndex = 2;

    fixture.componentRef.setInput('open', false);
    fixture.detectChanges();
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    expect(component.query).toBe('');
    expect(component.activeIndex).toBe(0);
  });

  it('fuzzy-filters commands and conversations by the query', () => {
    openPalette();
    component.query = 'npe';
    component.onQueryChange();

    const labels = component.results.map((r) => r.label);
    expect(labels).toEqual(['Debug NPE in ChatService']);
  });

  it('ArrowDown/ArrowUp move the active selection, wrapping at the ends', () => {
    openPalette();
    component.query = '';
    component.onQueryChange();
    const count = component.results.length;

    component.onKeydown(new KeyboardEvent('keydown', { key: 'ArrowUp' }));
    expect(component.activeIndex).toBe(count - 1);

    component.onKeydown(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    expect(component.activeIndex).toBe(0);
  });

  it('Enter runs the active command and closes the palette', () => {
    openPalette();
    component.query = 'new chat';
    component.onQueryChange();

    let newChatRequested = false;
    let closed = false;
    component.newChatRequested.subscribe(() => (newChatRequested = true));
    component.closeRequested.subscribe(() => (closed = true));

    component.onKeydown(new KeyboardEvent('keydown', { key: 'Enter' }));

    expect(newChatRequested).toBeTrue();
    expect(closed).toBeTrue();
  });

  it('selecting a conversation emits conversationSelected with that conversation', () => {
    openPalette();
    component.query = 'interfaces';
    component.onQueryChange();

    let selected: Conversation | undefined;
    component.conversationSelected.subscribe((c) => (selected = c));

    component.select(0);

    expect(selected).toEqual(conversations[0]);
  });

  it('Escape closes without running a command', () => {
    openPalette();
    let newChatRequested = false;
    let closed = false;
    component.newChatRequested.subscribe(() => (newChatRequested = true));
    component.closeRequested.subscribe(() => (closed = true));

    component.onKeydown(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(newChatRequested).toBeFalse();
    expect(closed).toBeTrue();
  });

  it('clicking the scrim closes the palette', () => {
    openPalette();
    let closed = false;
    component.closeRequested.subscribe(() => (closed = true));

    (fixture.nativeElement.querySelector('.palette-scrim') as HTMLElement).click();

    expect(closed).toBeTrue();
  });

  it('shows "no matching" when the query matches nothing', () => {
    openPalette();
    // Drive this one through the real input element + event, matching how a
    // user actually types, rather than mutating the field directly.
    const input = (fixture.nativeElement as HTMLElement).querySelector('input')!;
    input.value = 'zzzzznomatch';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(component.results.length).toBe(0);
    expect(fixture.nativeElement.querySelector('.palette-empty')).not.toBeNull();
  });
});
