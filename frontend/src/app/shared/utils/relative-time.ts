const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

function plural(count: number, unit: string): string {
  return `${count} ${unit}${count === 1 ? '' : 's'} ago`;
}

/**
 * "2 hours ago"-style text for an ISO-8601 timestamp. `null` (never happened)
 * reads as "never", and an unparseable value reads as "unknown" rather than
 * "NaN days ago". Timestamps slightly in the future (clock skew between the
 * browser and the backend) are treated as "just now".
 *
 * `now` is a parameter so callers can re-render on a timer and tests are
 * deterministic.
 */
export function formatRelativeTime(iso: string | null, now: number = Date.now()): string {
  if (iso === null) {
    return 'never';
  }
  const then = Date.parse(iso);
  if (Number.isNaN(then)) {
    return 'unknown';
  }
  const elapsed = now - then;
  if (elapsed < MINUTE) {
    return 'just now';
  }
  if (elapsed < HOUR) {
    return plural(Math.floor(elapsed / MINUTE), 'minute');
  }
  if (elapsed < DAY) {
    return plural(Math.floor(elapsed / HOUR), 'hour');
  }
  if (elapsed < 30 * DAY) {
    return plural(Math.floor(elapsed / DAY), 'day');
  }
  if (elapsed < 365 * DAY) {
    return plural(Math.floor(elapsed / (30 * DAY)), 'month');
  }
  return plural(Math.floor(elapsed / (365 * DAY)), 'year');
}

/** Absolute, locale-formatted timestamp for hover tooltips. Empty for null/invalid. */
export function formatAbsoluteTime(iso: string | null): string {
  if (iso === null) {
    return '';
  }
  const parsed = new Date(iso);
  return Number.isNaN(parsed.getTime()) ? '' : parsed.toLocaleString();
}
