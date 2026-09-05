import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Conversation } from '../../../../core/models/conversation.model';

@Component({
  selector: 'app-conversation-item',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './conversation-item.component.html',
  styleUrl: './conversation-item.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConversationItemComponent {
  @Input({ required: true }) conversation!: Conversation;
  @Input() active = false;

  @Output() selected = new EventEmitter<Conversation>();
  @Output() deleteRequested = new EventEmitter<Conversation>();

  menuOpen = false;

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

  select(): void {
    this.selected.emit(this.conversation);
  }
}
