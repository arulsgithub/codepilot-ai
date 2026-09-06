import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  Output,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Conversation } from '../../../../core/models/conversation.model';

/** Emitted when the user commits a rename from the inline editor. */
export interface ConversationRename {
  id: string;
  title: string;
}

@Component({
  selector: 'app-conversation-item',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './conversation-item.component.html',
  styleUrl: './conversation-item.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConversationItemComponent {
  @Input({ required: true }) conversation!: Conversation;
  @Input() active = false;

  @Output() selected = new EventEmitter<Conversation>();
  @Output() deleteRequested = new EventEmitter<Conversation>();
  @Output() renameRequested = new EventEmitter<ConversationRename>();

  @ViewChild('renameInput') renameInput?: ElementRef<HTMLInputElement>;

  menuOpen = false;
  editing = false;
  draftTitle = '';

  toggleMenu(event: Event): void {
    event.stopPropagation();
    this.menuOpen = !this.menuOpen;
  }

  closeMenu(): void {
    this.menuOpen = false;
  }

  requestDelete(event: Event): void {
    event.stopPropagation();
    this.closeMenu();
    this.deleteRequested.emit(this.conversation);
  }

  startRename(event: Event): void {
    event.stopPropagation();
    this.closeMenu();
    this.draftTitle = this.conversation.title;
    this.editing = true;
    // Focus + select once the input has rendered.
    setTimeout(() => {
      const el = this.renameInput?.nativeElement;
      el?.focus();
      el?.select();
    });
  }

  commitRename(): void {
    if (!this.editing) {
      return;
    }
    this.editing = false;
    const next = this.draftTitle.trim();
    if (next && next !== this.conversation.title) {
      this.renameRequested.emit({ id: this.conversation.id, title: next });
    }
  }

  cancelRename(): void {
    this.editing = false;
    this.draftTitle = '';
  }

  select(): void {
    if (this.editing) {
      return;
    }
    this.selected.emit(this.conversation);
  }
}
