import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ConversationService } from './conversation.service';
import { Conversation } from '../models/conversation.model';

describe('ConversationService', () => {
  let service: ConversationService;
  let httpMock: HttpTestingController;

  const sample: Conversation = {
    id: '16456cfb-163d-493b-96ca-f8de5df0e1cd',
    title: 'Java Questions',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ConversationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('POSTs a title to /api/v1/conversations to create a conversation', () => {
    service.createConversation('Java Questions').subscribe((result) => {
      expect(result).toEqual(sample);
    });

    const req = httpMock.expectOne('/api/v1/conversations');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ title: 'Java Questions' });
    req.flush(sample);
  });

  it('GETs /api/v1/conversations to list conversations', () => {
    service.listConversations().subscribe((result) => {
      expect(result).toEqual([sample]);
    });

    const req = httpMock.expectOne('/api/v1/conversations');
    expect(req.request.method).toBe('GET');
    req.flush([sample]);
  });

  it('GETs /api/v1/conversations/{id} to fetch a single conversation', () => {
    service.getConversation(sample.id).subscribe((result) => {
      expect(result).toEqual(sample);
    });

    const req = httpMock.expectOne(`/api/v1/conversations/${sample.id}`);
    expect(req.request.method).toBe('GET');
    req.flush(sample);
  });

  it('DELETEs /api/v1/conversations/{id} to remove a conversation', () => {
    service.deleteConversation(sample.id).subscribe();

    const req = httpMock.expectOne(`/api/v1/conversations/${sample.id}`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
