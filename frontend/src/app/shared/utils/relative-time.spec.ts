import { formatAbsoluteTime, formatRelativeTime } from './relative-time';

describe('formatRelativeTime', () => {
  const NOW = Date.parse('2026-09-19T12:00:00Z');
  const ago = (ms: number) => new Date(NOW - ms).toISOString();
  const MIN = 60_000;
  const HOUR = 60 * MIN;
  const DAY = 24 * HOUR;

  it('says "never" for null and "unknown" for garbage', () => {
    expect(formatRelativeTime(null, NOW)).toBe('never');
    expect(formatRelativeTime('not-a-date', NOW)).toBe('unknown');
  });

  it('says "just now" under a minute, including small future skew', () => {
    expect(formatRelativeTime(ago(10_000), NOW)).toBe('just now');
    expect(formatRelativeTime(ago(-30_000), NOW)).toBe('just now');
  });

  it('formats minutes, hours and days with correct pluralisation', () => {
    expect(formatRelativeTime(ago(MIN), NOW)).toBe('1 minute ago');
    expect(formatRelativeTime(ago(5 * MIN), NOW)).toBe('5 minutes ago');
    expect(formatRelativeTime(ago(HOUR), NOW)).toBe('1 hour ago');
    expect(formatRelativeTime(ago(2 * HOUR), NOW)).toBe('2 hours ago');
    expect(formatRelativeTime(ago(DAY), NOW)).toBe('1 day ago');
    expect(formatRelativeTime(ago(3 * DAY), NOW)).toBe('3 days ago');
  });

  it('rolls up to months and years', () => {
    expect(formatRelativeTime(ago(60 * DAY), NOW)).toBe('2 months ago');
    expect(formatRelativeTime(ago(400 * DAY), NOW)).toBe('1 year ago');
    expect(formatRelativeTime(ago(800 * DAY), NOW)).toBe('2 years ago');
  });
});

describe('formatAbsoluteTime', () => {
  it('is empty for null or invalid and a locale string otherwise', () => {
    expect(formatAbsoluteTime(null)).toBe('');
    expect(formatAbsoluteTime('nope')).toBe('');
    expect(formatAbsoluteTime('2026-09-19T12:00:00Z')).toBe(
      new Date('2026-09-19T12:00:00Z').toLocaleString()
    );
  });
});
