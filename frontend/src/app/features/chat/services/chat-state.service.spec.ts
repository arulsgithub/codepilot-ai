import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import { ChatStateService } from './chat-state.service';
import { ChatService } from '../../../core/services/chat.service';
import { ConversationService } from '../../../core/services/conversation.service';
import { MessageService } from '../../../core/services/message.service';
import { IndexingService } from '../../../core/services/indexing.service';
import { ChatRequest, StreamEvent } from '../../../core/models/chat.model';
import { Conversation } from '../../../core/models/conversation.model';

const CONVERSATION: Conversation = {
  id: 'conv-1',
  title: 'Test',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

describe('ChatStateService (Phase 2 wiring)', () => {
  let service: ChatStateService;
  let streamChatSpy: jasmine.Spy;
  let stream$: Subject<StreamEvent>;
  let indexRepositorySpy: jasmine.Spy;

  beforeEach(() => {
    try {
      localStorage.removeItem('codepilot-repository-root');
      localStorage.removeItem('codepilot-chat-mode');
    } catch {
      /* ignore */
    }

    stream$ = new Subject<StreamEvent>();
    streamChatSpy = jasmine.createSpy('streamChat').and.returnValue(stream$.asObservable());
    indexRepositorySpy = jasmine.createSpy('indexRepository');

    TestBed.configureTestingModule({
      providers: [
        ChatStateService,
        { provide: ChatService, useValue: { streamChat: streamChatSpy } },
        {
          provide: ConversationService,
          useValue: {
            getConversation: () => of(CONVERSATION),
            listConversations: () => of([CONVERSATION]),
          },
        },
        { provide: MessageService, useValue: { getMessages: () => of([]) } },
        { provide: IndexingService, useValue: { indexRepository: indexRepositorySpy } },
      ],
    });

    service = TestBed.inject(ChatStateService);
  });

  function lastRequest(): ChatRequest {
    return streamChatSpy.calls.mostRecent().args[0] as ChatRequest;
  }

  it('sends a byte-identical body (no repo, mode Auto) — unchanged from Phase 1', () => {
    service.sendMessageToConversation(CONVERSATION.id, 'hello');

    expect(streamChatSpy).toHaveBeenCalledTimes(1);
    const req = lastRequest();
    expect(Object.keys(req).sort()).toEqual(['conversationId', 'message', 'requestId']);
    expect(req.repositoryRoot).toBeUndefined();
    expect(req.mode).toBeUndefined();
  });

  it('includes repositoryRoot once a repository is attached', () => {
    indexRepositorySpy.and.returnValue(
      of({ repositoryRoot: 'E:\\repos\\codepilot', chunksIndexed: 412 })
    );
    service.indexRepository('E:\\repos\\codepilot');

    expect(service.repositoryRoot()).toBe('E:\\repos\\codepilot');
    expect(service.indexingStatus()).toBe('success');
    expect(service.indexedChunkCount()).toBe(412);

    service.sendMessageToConversation(CONVERSATION.id, 'explain ChatService');
    expect(lastRequest().repositoryRoot).toBe('E:\\repos\\codepilot');
  });

  it('omits repositoryRoot again after detaching', () => {
    indexRepositorySpy.and.returnValue(
      of({ repositoryRoot: 'E:\\repos\\codepilot', chunksIndexed: 10 })
    );
    service.indexRepository('E:\\repos\\codepilot');
    service.detachRepository();

    expect(service.repositoryRoot()).toBeNull();
    expect(service.indexingStatus()).toBe('idle');

    service.sendMessageToConversation(CONVERSATION.id, 'hi');
    expect(lastRequest().repositoryRoot).toBeUndefined();
  });

  it('keeps a prior attachment when a re-index fails', () => {
    indexRepositorySpy.and.returnValue(
      of({ repositoryRoot: 'E:\\repos\\codepilot', chunksIndexed: 10 })
    );
    service.indexRepository('E:\\repos\\codepilot');

    indexRepositorySpy.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 400 }))
    );
    service.indexRepository('E:\\bad\\path');

    expect(service.indexingStatus()).toBe('error');
    expect(service.indexingError()).toBeTruthy();
    expect(service.repositoryRoot()).toBe('E:\\repos\\codepilot');
  });

  it('sends the selected mode, and omits it again for Auto', () => {
    service.setChatMode('REASONING');
    service.sendMessageToConversation(CONVERSATION.id, 'think hard');
    expect(lastRequest().mode).toBe('REASONING');

    stream$.next({ type: 'COMPLETE', content: null });
    stream$.complete();

    service.setChatMode('AUTO');
    service.sendMessageToConversation(CONVERSATION.id, 'now relax');
    expect(lastRequest().mode).toBeUndefined();
  });

  it('attaches a streamed SOURCES event to the in-flight assistant message', () => {
    service.sendMessageToConversation(CONVERSATION.id, 'explain ChatService');

    stream$.next({ type: 'START', content: null });
    stream$.next({
      type: 'SOURCES',
      content: null,
      sources: [
        {
          filePath: 'com/codepilot/chat/service/ChatService.java',
          qualifiedName: 'com.codepilot.chat.service.ChatService#sendMessage',
          startLine: 48,
          endLine: 71,
        },
      ],
    });
    stream$.next({ type: 'TOKEN', content: 'Here...' });
    stream$.next({ type: 'COMPLETE', content: null });
    stream$.complete();

    const assistant = service.messages().find((m) => m.role === 'ASSISTANT')!;
    expect(assistant.sources?.length).toBe(1);
    expect(assistant.sources![0].qualifiedName).toBe(
      'com.codepilot.chat.service.ChatService#sendMessage'
    );
    expect(assistant.content).toBe('Here...');
  });

  it('leaves messages without a SOURCES event exactly as before', () => {
    service.sendMessageToConversation(CONVERSATION.id, 'hi');

    stream$.next({ type: 'START', content: null });
    stream$.next({ type: 'TOKEN', content: 'Hello' });
    stream$.next({ type: 'COMPLETE', content: null });
    stream$.complete();

    const assistant = service.messages().find((m) => m.role === 'ASSISTANT')!;
    expect(assistant.sources).toBeUndefined();
    expect(assistant.content).toBe('Hello');
  });
});
