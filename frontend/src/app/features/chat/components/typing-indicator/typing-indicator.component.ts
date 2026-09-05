import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Small animated "CodePilot is thinking" indicator, shown between
 * `sending` state (request fired) and the first TOKEN event arriving.
 * Kept as its own component so ChatState transitions don't need to know
 * about animation details.
 */
@Component({
  selector: 'app-typing-indicator',
  standalone: true,
  templateUrl: './typing-indicator.component.html',
  styleUrl: './typing-indicator.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TypingIndicatorComponent {}
