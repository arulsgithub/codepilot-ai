import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ModeSelection } from '../../../../core/models/chat.model';

interface ModeOption {
  value: ModeSelection;
  label: string;
  hint: string;
}

/**
 * Segmented control for the model mode sent with each chat turn.
 *
 * Only four options are exposed: "Auto" (the default, which makes
 * ChatStateService omit the `mode` field so the backend chooses), plus the
 * three explicit modes a user might reasonably want. `RAG` is deliberately
 * absent — the backend selects it on its own whenever a repository is
 * attached — and so is the backend-internal `TITLE` mode.
 */
@Component({
  selector: 'app-mode-selector',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './mode-selector.component.html',
  styleUrl: './mode-selector.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ModeSelectorComponent {
  @Input() mode: ModeSelection = 'AUTO';
  @Input() disabled = false;
  @Output() modeChange = new EventEmitter<ModeSelection>();

  readonly options: ModeOption[] = [
    { value: 'AUTO', label: 'Auto', hint: 'Let CodePilot pick the best mode' },
    { value: 'FAST', label: 'Fast', hint: 'Lower latency, lighter answers' },
    { value: 'CODE', label: 'Code', hint: 'Tuned for code generation' },
    { value: 'REASONING', label: 'Reasoning', hint: 'Slower, deeper analysis' },
  ];

  select(value: ModeSelection): void {
    if (this.disabled || value === this.mode) {
      return;
    }
    this.modeChange.emit(value);
  }
}
