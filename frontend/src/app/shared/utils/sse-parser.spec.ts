import { SseParser } from './sse-parser';

describe('SseParser', () => {
  let parser: SseParser;

  beforeEach(() => {
    parser = new SseParser();
  });

  it('parses a single complete event delivered in one chunk', () => {
    const events = parser.push('data: {"type":"START","content":null}\n\n');
    expect(events).toEqual([{ type: 'START', content: null }]);
  });

  it('parses multiple complete events delivered in one chunk', () => {
    const chunk =
      'data: {"type":"TOKEN","content":"An"}\n\n' +
      'data: {"type":"TOKEN","content":" abstract"}\n\n';
    const events = parser.push(chunk);
    expect(events).toEqual([
      { type: 'TOKEN', content: 'An' },
      { type: 'TOKEN', content: ' abstract' },
    ]);
  });

  it('reassembles a JSON payload split across two chunks (the core requirement)', () => {
    const firstChunkEvents = parser.push('data: {"type":"TOKEN","content":"Hel');
    expect(firstChunkEvents).toEqual([]);

    const secondChunkEvents = parser.push('lo"}\n\n');
    expect(secondChunkEvents).toEqual([{ type: 'TOKEN', content: 'Hello' }]);
  });

  it('reassembles a payload split across more than two chunks', () => {
    expect(parser.push('data: {"typ')).toEqual([]);
    expect(parser.push('e":"TOKEN","con')).toEqual([]);
    expect(parser.push('tent":"Hel')).toEqual([]);
    expect(parser.push('lo wor')).toEqual([]);
    const events = parser.push('ld"}\n\n');
    expect(events).toEqual([{ type: 'TOKEN', content: 'Hello world' }]);
  });

  it('handles a chunk boundary that falls exactly on the record separator', () => {
    expect(parser.push('data: {"type":"START","content":null}\n')).toEqual([]);
    const events = parser.push('\n');
    expect(events).toEqual([{ type: 'START', content: null }]);
  });

  it('handles multiple events split arbitrarily across chunk boundaries', () => {
    const allEvents: unknown[] = [];
    const chunks = [
      'data: {"type":"START","content":null}\n\ndata: {"typ',
      'e":"TOKEN","content":"An"}\n\ndata: {"type":"TOKEN","con',
      'tent":" abstract"}\n\ndata: {"type":"COMPLETE","content":null}\n\n',
    ];
    for (const chunk of chunks) {
      allEvents.push(...parser.push(chunk));
    }
    expect(allEvents).toEqual([
      { type: 'START', content: null },
      { type: 'TOKEN', content: 'An' },
      { type: 'TOKEN', content: ' abstract' },
      { type: 'COMPLETE', content: null },
    ]);
  });

  it('normalizes CRLF line endings', () => {
    const events = parser.push('data: {"type":"TOKEN","content":"x"}\r\n\r\n');
    expect(events).toEqual([{ type: 'TOKEN', content: 'x' }]);
  });

  it('ignores non-data SSE fields such as event: and id:', () => {
    const events = parser.push(
      'event: message\nid: 42\ndata: {"type":"TOKEN","content":"hi"}\n\n'
    );
    expect(events).toEqual([{ type: 'TOKEN', content: 'hi' }]);
  });

  it('drops a record with malformed JSON instead of throwing', () => {
    expect(() => parser.push('data: {not valid json\n\n')).not.toThrow();
    expect(parser.push('data: {not valid json\n\n')).toEqual([]);
  });

  it('drops an event with an unrecognized type', () => {
    const events = parser.push('data: {"type":"UNKNOWN","content":null}\n\n');
    expect(events).toEqual([]);
  });

  it('ignores a blank keep-alive record', () => {
    const events = parser.push('\n\n');
    expect(events).toEqual([]);
  });

  it('flush() emits a final record that never received a trailing blank line', () => {
    parser.push('data: {"type":"COMPLETE","content":null}');
    const flushed = parser.flush();
    expect(flushed).toEqual([{ type: 'COMPLETE', content: null }]);
  });

  it('flush() returns nothing when the buffer is empty or whitespace', () => {
    expect(parser.flush()).toEqual([]);
    parser.push('   \n');
    expect(parser.flush()).toEqual([]);
  });

  it('joins multiple data: lines within a single record per the SSE spec', () => {
    const events = parser.push('data: {"type":"TOKEN",\ndata: "content":"hi"}\n\n');
    expect(events).toEqual([{ type: 'TOKEN', content: 'hi' }]);
  });
});
