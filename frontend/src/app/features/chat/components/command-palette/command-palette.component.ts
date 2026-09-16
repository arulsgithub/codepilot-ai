import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Conversation } from '../../../../core/models/conversation.model';
import { ModeSelection } from '../../../../core/models/chat.model';
import { fuzzyScore } from '../../../../shared/utils/fuzzy-match';

interface PaletteCommand {
  id: string;
  label: string;
  hint: string;
  group: string;
  run: () => void;
}

/**
 * Ctrl/Cmd+K command palette — a fuzzy-matched, fully keyboard-driven list of
 * app-wide actions plus a "switch conversation" shortcut. This component
 * owns none of the app's actual state; every action it offers is delegated
 * back to ChatPageComponent via an output, exactly like every other
 * presentational component here.
 *
 * Accessibility: implements the ARIA combobox-with-listbox pattern — the
 * text input is `role="combobox"` and owns `aria-activedescendant`, the
 * results list is `role="listbox"`. Focus never leaves the input (arrow keys
 * move the "active" selection, not DOM focus), and the previously focused
 * element is restored when the palette closes, matching the app's other
 * overlays' focus-restore behavior.
 */
@Component({
  selector: 'app-command-palette',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './command-palette.component.html',
  styleUrl: './command-palette.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandPaletteComponent implements OnChanges {
  @Input() open = false;
  @Input() conversations: Conversation[] = [];
  @Input() theme: 'dark' | 'light' = 'dark';

  @Output() closeRequested = new EventEmitter<void>();
  @Output() newChatRequested = new EventEmitter<void>();
  @Output() conversationSelected = new EventEmitter<Conversation>();
  @Output() themeToggleRequested = new EventEmitter<void>();
  @Output() attachRepoRequested = new EventEmitter<void>();
  @Output() editPanelRequested = new EventEmitter<void>();
  @Output() modeChangeRequested = new EventEmitter<ModeSelection>();
  @Output() focusComposerRequested = new EventEmitter<void>();
  @Output() shortcutsRequested = new EventEmitter<void>();

  @ViewChild('inputRef') inputRef?: ElementRef<HTMLInputElement>;

  query = '';
  activeIndex = 0;
  results: PaletteCommand[] = [];

  private previouslyFocused: HTMLElement | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open']) {
      if (this.open) {
        this.previouslyFocused = (document.activeElement as HTMLElement) ?? null;
        this.query = '';
        this.activeIndex = 0;
        this.refreshResults();
        // Wait for the overlay to be in the DOM before focusing it.
        setTimeout(() => this.inputRef?.nativeElement.focus());
      } else {
        this.previouslyFocused?.focus?.();
        this.previouslyFocused = null;
      }
    }
    if (changes['conversations'] && this.open) {
      this.refreshResults();
    }
  }

  onQueryChange(): void {
    this.activeIndex = 0;
    this.refreshResults();
  }

  onKeydown(event: KeyboardEvent): void {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.move(1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.move(-1);
        break;
      case 'Enter':
        event.preventDefault();
        this.runActive();
        break;
      case 'Escape':
        event.preventDefault();
        this.close();
        break;
    }
  }

  select(index: number): void {
    this.activeIndex = index;
    this.runActive();
  }

  close(): void {
    this.closeRequested.emit();
  }

  onScrimClick(): void {
    this.close();
  }

  trackByCommand(_index: number, command: PaletteCommand): string {
    return command.id;
  }

  private move(delta: number): void {
    if (this.results.length === 0) {
      return;
    }
    this.activeIndex = (this.activeIndex + delta + this.results.length) % this.results.length;
  }

  private runActive(): void {
    const command = this.results[this.activeIndex];
    if (!command) {
      return;
    }
    command.run();
    this.close();
  }

  private refreshResults(): void {
    const all = this.buildAllCommands();
    const q = this.query.trim();
    if (!q) {
      this.results = all.slice(0, 20);
      return;
    }
    this.results = all
      .map((command) => ({ command, score: fuzzyScore(q, `${command.label} ${command.hint}`) }))
      .filter((entry): entry is { command: PaletteCommand; score: number } => entry.score !== null)
      .sort((a, b) => b.score - a.score)
      .map((entry) => entry.command)
      .slice(0, 20);
  }

  private buildAllCommands(): PaletteCommand[] {
    const commands: PaletteCommand[] = [
      {
        id: 'new-chat',
        label: 'New chat',
        hint: 'Start a fresh conversation',
        group: 'Actions',
        run: () => this.newChatRequested.emit(),
      },
      {
        id: 'focus-composer',
        label: 'Focus message box',
        hint: 'Jump to the composer',
        group: 'Actions',
        run: () => this.focusComposerRequested.emit(),
      },
      {
        id: 'toggle-theme',
        label: this.theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme',
        hint: 'Toggle appearance',
        group: 'Actions',
        run: () => this.themeToggleRequested.emit(),
      },
      {
        id: 'attach-repo',
        label: 'Attach repository',
        hint: 'Ground answers in an indexed codebase',
        group: 'Actions',
        run: () => this.attachRepoRequested.emit(),
      },
      {
        id: 'edit-code',
        label: 'Edit code with AI',
        hint: 'Describe a change to plan and apply',
        group: 'Actions',
        run: () => this.editPanelRequested.emit(),
      },
      {
        id: 'shortcuts',
        label: 'Keyboard shortcuts',
        hint: 'Show the shortcut reference',
        group: 'Actions',
        run: () => this.shortcutsRequested.emit(),
      },
      ...(['AUTO', 'FAST', 'CODE', 'REASONING'] as ModeSelection[]).map((mode) => ({
        id: `mode-${mode}`,
        label: `Set mode: ${mode.charAt(0) + mode.slice(1).toLowerCase()}`,
        hint: 'Change the model mode',
        group: 'Mode',
        run: () => this.modeChangeRequested.emit(mode),
      })),
      ...this.conversations.map((conversation) => ({
        id: `conversation-${conversation.id}`,
        label: conversation.title,
        hint: 'Switch conversation',
        group: 'Conversations',
        run: () => this.conversationSelected.emit(conversation),
      })),
    ];
    return commands;
  }
}
