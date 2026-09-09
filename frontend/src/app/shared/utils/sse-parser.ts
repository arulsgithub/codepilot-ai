import { StreamEvent, StreamEventType } from '../../core/models/chat.model';

/**
 * Incremental parser for a `text/event-stream` response body.
 *
 * Why this exists: `ReadableStream.read()` gives you raw bytes off the wire
 * in whatever chunks the network happens to deliver. There is NO guarantee
 * that a chunk boundary lines up with an SSE event boundary, a line
 * boundary, or even a complete JSON payload. A single JSON blob can be torn
 * in half across two `read()` calls, e.g.:
 *
 *   chunk 1: 'data: {"type":"TOKEN","content":"Hel'
 *   chunk 2: 'lo"}\n\n'
 *
 * This class buffers raw text across calls and only emits a `StreamEvent`
 * once a complete SSE record (terminated by a blank line, i.e. "\n\n") has
 * been received and its `data:` payload successfully parses as JSON.
 *
 * Usage:
 *   const parser = new SseParser();
 *   for await (const chunk of byteChunks) {
 *     const events = parser.push(decoder.decode(chunk, { stream: true }));
 *     for (const event of events) { ... }
 *   }
 *   // At stream end, flush() catches any final unterminated record.
 *   const trailing = parser.flush();
 */
export class SseParser {
  /** Raw text received so far that has not yet formed a complete SSE record. */
  private buffer = '';

  /**
   * Feed newly-decoded text into the parser.
   * Returns zero or more fully-parsed `StreamEvent`s extracted from the
   * combined buffer. Any trailing partial record is retained internally
   * for the next call.
   */
  push(rawChunk: string): StreamEvent[] {
    this.buffer += rawChunk;

    // Normalize CRLF to LF so "\r\n\r\n" record separators are handled too.
    this.buffer = this.buffer.replace(/\r\n/g, '\n');

    const events: StreamEvent[] = [];

    // SSE records are separated by a blank line ("\n\n"). Split off every
    // complete record, keeping anything after the final "\n\n" (which may
    // be an incomplete record still arriving) in the buffer.
    let separatorIndex: number;
    while ((separatorIndex = this.buffer.indexOf('\n\n')) !== -1) {
      const rawRecord = this.buffer.slice(0, separatorIndex);
      this.buffer = this.buffer.slice(separatorIndex + 2);

      const event = this.parseRecord(rawRecord);
      if (event) {
        events.push(event);
      }
    }

    return events;
  }

  /**
   * Call once the underlying stream has ended. Some servers omit the final
   * trailing blank line after the very last event, which would otherwise
   * leave a complete-but-unterminated record stuck in the buffer forever.
   */
  flush(): StreamEvent[] {
    if (!this.buffer.trim()) {
      this.buffer = '';
      return [];
    }
    const event = this.parseRecord(this.buffer);
    this.buffer = '';
    return event ? [event] : [];
  }

  /**
   * Parse a single raw SSE record (everything between two blank lines) into
   * a StreamEvent. An SSE record can contain multiple `data:` lines, which
   * per spec should be joined with "\n" before being treated as one payload
   * — we support that even though this backend always sends a single line.
   */
  private parseRecord(rawRecord: string): StreamEvent | null {
    const dataLines: string[] = [];

    for (const line of rawRecord.split('\n')) {
      if (line.startsWith('data:')) {
        // Strip the leading "data:" and at most one following space, per SSE spec.
        dataLines.push(line.startsWith('data: ') ? line.slice(6) : line.slice(5));
      }
      // Other SSE fields (event:, id:, retry:, comments starting with ":")
      // are intentionally ignored — this backend encodes everything needed
      // inside the JSON `data:` payload.
    }

    if (dataLines.length === 0) {
      return null;
    }

    const payload = dataLines.join('\n').trim();
    if (!payload) {
      return null;
    }

    try {
      const parsed = JSON.parse(payload);
      if (!this.isValidStreamEvent(parsed)) {
        return null;
      }
      return parsed;
    } catch {
      // A JSON.parse failure here means the payload was genuinely malformed
      // (not just split across chunks — push() only parses complete
      // records). Drop it rather than throwing, so one bad event doesn't
      // kill the rest of the stream.
      return null;
    }
  }

  private isValidStreamEvent(value: unknown): value is StreamEvent {
    if (typeof value !== 'object' || value === null) {
      return false;
    }
    const candidate = value as Record<string, unknown>;
    // 'SOURCES' was added in Phase 2 — it carries an extra `sources` array
    // alongside the usual `type`/`content`. Any other unrecognised type is
    // still dropped here (see the "unrecognized type" spec), so a future
    // backend event can never throw or corrupt the stream.
    const validTypes: StreamEventType[] = ['START', 'SOURCES', 'TOKEN', 'COMPLETE', 'ERROR'];
    return (
      typeof candidate['type'] === 'string' &&
      validTypes.includes(candidate['type'] as StreamEventType) &&
      (candidate['content'] === null || typeof candidate['content'] === 'string')
    );
  }
}
