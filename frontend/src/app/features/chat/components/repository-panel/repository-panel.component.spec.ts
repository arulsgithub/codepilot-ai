import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { RepositoryPanelComponent } from './repository-panel.component';

describe('RepositoryPanelComponent', () => {
  let fixture: ComponentFixture<RepositoryPanelComponent>;
  let component: RepositoryPanelComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RepositoryPanelComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(RepositoryPanelComponent);
    component = fixture.componentInstance;
  });

  function html(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  it('renders nothing while closed', () => {
    fixture.componentRef.setInput('open', false);
    fixture.detectChanges();
    expect(html().querySelector('.panel')).toBeNull();
  });

  it('seeds the path field from the attached repository when opened', () => {
    fixture.componentRef.setInput('repositoryRoot', 'E:\\repos\\codepilot');
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    expect(component.draftPath).toBe('E:\\repos\\codepilot');
  });

  it('emits indexRequested with the trimmed path on submit', () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    const emitted: string[] = [];
    component.indexRequested.subscribe((p) => emitted.push(p));

    component.draftPath = '  E:\\repos\\codepilot  ';
    component.submit();

    expect(emitted).toEqual(['E:\\repos\\codepilot']);
  });

  it('does not submit while an index is already in flight', () => {
    fixture.componentRef.setInput('open', true);
    fixture.componentRef.setInput('indexingStatus', 'indexing');
    component.draftPath = 'E:\\repos\\codepilot';
    fixture.detectChanges();

    const emitted: string[] = [];
    component.indexRequested.subscribe((p) => emitted.push(p));
    component.submit();

    expect(emitted).toEqual([]);
  });

  it('shows the working message and chunk count for each status', () => {
    fixture.componentRef.setInput('open', true);
    fixture.componentRef.setInput('indexingStatus', 'indexing');
    fixture.detectChanges();
    expect(html().querySelector('.status--working')).not.toBeNull();

    fixture.componentRef.setInput('indexingStatus', 'success');
    fixture.componentRef.setInput('indexedChunkCount', 412);
    fixture.detectChanges();
    expect(html().querySelector('.status--ok')!.textContent).toContain('412');

    fixture.componentRef.setInput('indexingStatus', 'error');
    fixture.componentRef.setInput('indexingError', 'That path could not be indexed.');
    fixture.detectChanges();
    expect(html().querySelector('.status--error')!.textContent).toContain(
      'That path could not be indexed.'
    );
  });

  it('offers Detach only when a repository is attached', () => {
    fixture.componentRef.setInput('open', true);
    fixture.componentRef.setInput('repositoryRoot', null);
    fixture.detectChanges();
    expect(fixture.debugElement.query(By.css('.btn--ghost'))).toBeNull();

    fixture.componentRef.setInput('repositoryRoot', 'E:\\repos\\codepilot');
    fixture.detectChanges();
    const detachBtn = fixture.debugElement.query(By.css('.btn--ghost'));
    expect(detachBtn).not.toBeNull();

    let detached = false;
    component.detachRequested.subscribe(() => (detached = true));
    (detachBtn.nativeElement as HTMLButtonElement).click();
    expect(detached).toBeTrue();
  });

  it('does not emit closeRequested while indexing', () => {
    fixture.componentRef.setInput('open', true);
    fixture.componentRef.setInput('indexingStatus', 'indexing');
    fixture.detectChanges();

    let closed = false;
    component.closeRequested.subscribe(() => (closed = true));
    component.close();
    expect(closed).toBeFalse();
  });
});
