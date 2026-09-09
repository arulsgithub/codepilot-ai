import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageSourcesComponent } from './message-sources.component';
import { SourceReference } from '../../../../core/models/chat.model';

describe('MessageSourcesComponent', () => {
  let fixture: ComponentFixture<MessageSourcesComponent>;
  let component: MessageSourcesComponent;

  const sources: SourceReference[] = [
    {
      filePath: 'com/codepilot/chat/service/ChatService.java',
      qualifiedName: 'com.codepilot.chat.service.ChatService#sendMessage',
      startLine: 48,
      endLine: 71,
    },
    {
      filePath: 'com/codepilot/ai/orchestrator/AIOrchestrator.java',
      qualifiedName: 'com.codepilot.ai.orchestrator.AIOrchestrator#route',
      startLine: 12,
      endLine: 12,
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MessageSourcesComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(MessageSourcesComponent);
    component = fixture.componentInstance;
  });

  function html(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  it('renders nothing when there are no sources', () => {
    fixture.componentRef.setInput('sources', []);
    fixture.detectChanges();
    expect(html().querySelector('.sources')).toBeNull();
  });

  it('renders a collapsed <details> summarising the count', () => {
    fixture.componentRef.setInput('sources', sources);
    fixture.detectChanges();

    const details = html().querySelector('details');
    expect(details).not.toBeNull();
    expect(details!.open).toBeFalse();
    expect(details!.querySelector('summary')!.textContent).toContain('2 sources');
  });

  it('singularises the summary for a single source', () => {
    fixture.componentRef.setInput('sources', [sources[0]]);
    fixture.detectChanges();
    expect(component.summaryLabel).toBe('1 source');
  });

  it('shows filePath, qualifiedName and the line range for each source', () => {
    fixture.componentRef.setInput('sources', sources);
    fixture.detectChanges();

    const text = html().textContent!;
    expect(text).toContain('com/codepilot/chat/service/ChatService.java');
    expect(text).toContain('com.codepilot.chat.service.ChatService#sendMessage');
    expect(text).toContain('lines 48–71');
    // A single-line chunk collapses to "line N".
    expect(text).toContain('line 12');
  });
});
