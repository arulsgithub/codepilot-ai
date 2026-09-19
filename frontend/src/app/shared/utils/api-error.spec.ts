import { HttpErrorResponse } from '@angular/common/http';
import { describeApiError } from './api-error';

describe('describeApiError', () => {
  it("returns the backend's `message` verbatim", () => {
    const message =
      'Git clone failed: repository requires authentication. Set the GITHUB_TOKEN environment variable';
    const err = new HttpErrorResponse({
      status: 502,
      error: { timestamp: 't', status: 502, code: 'X', message, path: '/p' },
    });

    expect(describeApiError(err, 'fallback')).toBe(message);
  });

  it('reports an unreachable backend for status 0', () => {
    const err = new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') });

    expect(describeApiError(err, 'fallback')).toContain('Unable to reach the CodePilot backend');
  });

  it('falls back (with the status) when the body has no usable message', () => {
    expect(describeApiError(new HttpErrorResponse({ status: 500, error: null }), 'Sync failed')).toBe(
      'Sync failed (HTTP 500)'
    );
    expect(
      describeApiError(new HttpErrorResponse({ status: 500, error: { message: '   ' } }), 'Sync failed')
    ).toBe('Sync failed (HTTP 500)');
    expect(
      describeApiError(new HttpErrorResponse({ status: 502, error: '<html>Bad gateway</html>' }), 'X')
    ).toBe('X (HTTP 502)');
  });

  it('uses a plain Error message and otherwise the fallback', () => {
    expect(describeApiError(new Error('boom'), 'fallback')).toBe('boom');
    expect(describeApiError('weird', 'fallback')).toBe('fallback');
  });
});
