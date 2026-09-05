import { TestBed } from '@angular/core/testing';
import { ChatService, HttpStreamError } from './chat.service';
import { StreamEvent } from '../models/chat.model';

/** Builds a fake `Response` whose body streams the given text chunks. */
function fakeStreamingResponse(chunks: string[], status = 200): Response {
  const encoder = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(encoder.encode(chunk));
      }
      controller.close();
    },
  });
  return new Response(body, { status });
}

describe('ChatService', () => {
  let service: ChatService;
  let fetchSpy: jasmine.Spy;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ChatService);
    fetchSpy = spyOn(window, 'fetch');
  });

  it('emits START, TOKEN, and COMPLETE events in order, then completes', (done) => {
    fetchSpy.and.resolveTo(
      fakeStreamingResponse([
        'data: {"type":"START","content":null}\n\n',
        'data: {"type":"TOKEN","content":"An"}\n\n',
        'data: {"type":"TOKEN","content":" interface"}\n\n',
        'data: {"type":"COMPLETE","content":null}\n\n',
      ])
    );

    const received: StreamEvent[] = [];
    service.streamChat({ conversationId: 'c1', message: 'Explain interfaces', requestId: 'r1' }).subscribe({
      next: (event) => received.push(event),
      complete: () => {
        expect(received).toEqual([
          { type: 'START', content: null },
          { type: 'TOKEN', content: 'An' },
          { type: 'TOKEN', content: ' interface' },
          { type: 'COMPLETE', content: null },
        ]);
        done();
      },
      error: done.fail,
    });
  });

  it('reassembles tokens split across raw byte chunks end-to-end', (done) => {
    fetchSpy.and.resolveTo(
      fakeStreamingResponse(['data: {"type":"TOKEN","content":"Hel', 'lo"}\n\n'])
    );

    const received: StreamEvent[] = [];
    service.streamChat({ conversationId: 'c1', message: 'hi', requestId: 'r1' }).subscribe({
      next: (event) => received.push(event),
      complete: () => {
        expect(received).toEqual([{ type: 'TOKEN', content: 'Hello' }]);
        done();
      },
      error: done.fail,
    });
  });

  it('POSTs the conversationId and message as JSON to /api/v1/chat/stream', (done) => {
    fetchSpy.and.resolveTo(fakeStreamingResponse(['data: {"type":"COMPLETE","content":null}\n\n']));

    service.streamChat({ conversationId: 'abc', message: 'Explain generics', requestId: 'r1' }).subscribe({
      complete: () => {
        expect(fetchSpy).toHaveBeenCalledTimes(1);
        const [url, init] = fetchSpy.calls.mostRecent().args;
        expect(url).toContain('/api/v1/chat/stream');
        expect(init.method).toBe('POST');
        expect(JSON.parse(init.body)).toEqual({
          conversationId: 'abc',
          message: 'Explain generics',
          requestId: 'r1',
        });
        done();
      },
      error: done.fail,
    });
  });

  it('errors with HttpStreamError when the backend responds non-OK', (done) => {
    fetchSpy.and.resolveTo(fakeStreamingResponse([], 500));

    service.streamChat({ conversationId: 'c1', message: 'hi', requestId: 'r1' }).subscribe({
      next: () => done.fail('should not emit any events'),
      error: (err) => {
        expect(err instanceof HttpStreamError).toBeTrue();
        expect((err as HttpStreamError).status).toBe(500);
        done();
      },
    });
  });

  it('errors when fetch itself rejects (e.g. backend unreachable)', (done) => {
    fetchSpy.and.rejectWith(new TypeError('Failed to fetch'));

    service.streamChat({ conversationId: 'c1', message: 'hi', requestId: 'r1' }).subscribe({
      next: () => done.fail('should not emit any events'),
      error: (err) => {
        expect(err).toBeInstanceOf(TypeError);
        done();
      },
    });
  });

  it('propagates an ERROR stream event and then completes', (done) => {
    fetchSpy.and.resolveTo(fakeStreamingResponse(['data: {"type":"ERROR","content":null}\n\n']));

    const received: StreamEvent[] = [];
    service.streamChat({ conversationId: 'c1', message: 'hi', requestId: 'r1' }).subscribe({
      next: (event) => received.push(event),
      complete: () => {
        expect(received).toEqual([{ type: 'ERROR', content: null }]);
        done();
      },
      error: done.fail,
    });
  });
});
