import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ShortcutsDialogComponent } from './shortcuts-dialog.component';

describe('ShortcutsDialogComponent', () => {
  let fixture: ComponentFixture<ShortcutsDialogComponent>;
  let component: ShortcutsDialogComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShortcutsDialogComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ShortcutsDialogComponent);
    component = fixture.componentInstance;
  });

  it('renders nothing when closed', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.dialog')).toBeNull();
  });

  it('lists every shortcut entry when open', () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('.shortcut-row');
    expect(rows.length).toBe(component.shortcuts.length);
  });

  it('emits closeRequested when the close button is clicked', () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    let closed = false;
    component.closeRequested.subscribe(() => (closed = true));

    (fixture.nativeElement.querySelector('.dialog-close') as HTMLButtonElement).click();

    expect(closed).toBeTrue();
  });

  it('emits closeRequested when the scrim is clicked', () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    let closed = false;
    component.closeRequested.subscribe(() => (closed = true));

    (fixture.nativeElement.querySelector('.dialog-scrim') as HTMLElement).click();

    expect(closed).toBeTrue();
  });

  it('Escape closes the dialog', () => {
    let closed = false;
    component.closeRequested.subscribe(() => (closed = true));

    component.onKeydown(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(closed).toBeTrue();
  });
});
