import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MessageService } from './message.service';
import { Message } from '../models/message.model';

describe('MessageService', () => {
  let service: MessageService;
  let httpMock: HttpTestingController;

  const conversationId = '16456cfb-163d-493b-96ca-f8de5df0e1cd';
  const messages: Message[] = [
    {
      id: 'm1',
      conversationId,
      role: 'USER',
      content: 'What is an abstract class?',
      sequenceNumber: 1,
      createdAt: '2026-01-01T00:00:00Z',
    },
    {
      id: 'm2',
      conversationId,
      role: 'ASSISTANT',
      content: 'An abstract class...',
      sequenceNumber: 2,
      createdAt: '2026-01-01T00:00:01Z',
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(MessageService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs /api/v1/conversations/{id}/messages', () => {
    service.getMessages(conversationId).subscribe((result) => {
      expect(result).toEqual(messages);
    });

    const req = httpMock.expectOne(`/api/v1/conversations/${conversationId}/messages`);
    expect(req.request.method).toBe('GET');
    req.flush(messages);
  });
});
