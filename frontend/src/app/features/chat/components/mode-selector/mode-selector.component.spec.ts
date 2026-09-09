import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ModeSelectorComponent } from './mode-selector.component';
import { ModeSelection } from '../../../../core/models/chat.model';

describe('ModeSelectorComponent', () => {
  let fixture: ComponentFixture<ModeSelectorComponent>;
  let component: ModeSelectorComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ModeSelectorComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ModeSelectorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function buttonLabels(): string[] {
    return fixture.debugElement
      .queryAll(By.css('.mode-option'))
      .map((de) => (de.nativeElement as HTMLElement).textContent!.trim());
  }

  it('exposes exactly Auto, Fast, Code, Reasoning — never RAG or TITLE', () => {
    expect(buttonLabels()).toEqual(['Auto', 'Fast', 'Code', 'Reasoning']);
  });

  it('marks the active option from the `mode` input', () => {
    fixture.componentRef.setInput('mode', 'CODE');
    fixture.detectChanges();
    const active = fixture.debugElement.query(By.css('.mode-option--active'));
    expect((active.nativeElement as HTMLElement).textContent!.trim()).toBe('Code');
  });

  it('emits the chosen mode on click', () => {
    const emitted: ModeSelection[] = [];
    component.modeChange.subscribe((m) => emitted.push(m));

    const reasoningBtn = fixture.debugElement
      .queryAll(By.css('.mode-option'))
      .find((de) => (de.nativeElement as HTMLElement).textContent!.trim() === 'Reasoning')!;
    (reasoningBtn.nativeElement as HTMLButtonElement).click();

    expect(emitted).toEqual(['REASONING']);
  });

  it('does not re-emit when the already-active option is clicked', () => {
    fixture.componentRef.setInput('mode', 'AUTO');
    fixture.detectChanges();

    const emitted: ModeSelection[] = [];
    component.modeChange.subscribe((m) => emitted.push(m));

    const autoBtn = fixture.debugElement.queryAll(By.css('.mode-option'))[0];
    (autoBtn.nativeElement as HTMLButtonElement).click();

    expect(emitted).toEqual([]);
  });

  it('does not emit while disabled', () => {
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();

    const emitted: ModeSelection[] = [];
    component.modeChange.subscribe((m) => emitted.push(m));

    component.select('FAST');
    expect(emitted).toEqual([]);
  });
});
