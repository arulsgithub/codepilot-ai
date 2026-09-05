import { ChangeDetectionStrategy, Component, EventEmitter, Output } from '@angular/core';

/** One example prompt shown on the welcome screen. */
export interface ExamplePrompt {
  label: string;
}

@Component({
  selector: 'app-welcome-screen',
  standalone: true,
  templateUrl: './welcome-screen.component.html',
  styleUrl: './welcome-screen.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WelcomeScreenComponent {
  /** Emits the prompt text when the user clicks an example prompt chip. */
  @Output() promptSelected = new EventEmitter<string>();

  readonly examplePrompts: ExamplePrompt[] = [
    { label: 'Explain Java virtual threads' },
    { label: 'Why would I use CompletableFuture?' },
    { label: 'How does Spring dependency injection work?' },
  ];

  selectPrompt(prompt: ExamplePrompt): void {
    this.promptSelected.emit(prompt.label);
  }
}
