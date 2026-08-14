export const MIN_EXPIRATION_MILLISECONDS = 60_000;
export const MAX_EXPIRATION_MILLISECONDS = 7 * 24 * 60 * 60 * 1_000;
export const DEFAULT_EXPIRATION_MILLISECONDS = 24 * 60 * 60 * 1_000;

const UI_BOUNDARY_BUFFER_MILLISECONDS = 60_000;
const LOCAL_DATE_TIME_PATTERN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,3})?)?$/;

export interface ExpirationInputBounds {
  readonly min: string;
  readonly max: string;
}

export function toDateTimeLocalValue(value: string | Date): string {
  const date = value instanceof Date ? value : new Date(value);

  if (Number.isNaN(date.getTime())) {
    return '';
  }

  return [
    date.getFullYear().toString().padStart(4, '0'),
    '-',
    (date.getMonth() + 1).toString().padStart(2, '0'),
    '-',
    date.getDate().toString().padStart(2, '0'),
    'T',
    date.getHours().toString().padStart(2, '0'),
    ':',
    date.getMinutes().toString().padStart(2, '0'),
  ].join('');
}

export function dateTimeLocalToIso(value: string): string | null {
  if (!LOCAL_DATE_TIME_PATTERN.test(value)) {
    return null;
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return null;
  }

  // Date normalizes impossible local wall times (including DST gaps). Reject
  // those instead of silently sending a different instant than the user chose.
  if (toDateTimeLocalValue(date) !== value.slice(0, 16)) {
    return null;
  }

  return date.toISOString();
}

export function defaultExpirationValue(now: Date = new Date()): string {
  return toDateTimeLocalValue(new Date(now.getTime() + DEFAULT_EXPIRATION_MILLISECONDS));
}

/**
 * Uses a small buffer inside the backend's exact 1-minute-to-7-day window so a
 * value does not cross a boundary while the request is in flight.
 */
export function expirationInputBounds(now: Date = new Date()): ExpirationInputBounds {
  return {
    min: toDateTimeLocalValue(
      new Date(now.getTime() + MIN_EXPIRATION_MILLISECONDS + UI_BOUNDARY_BUFFER_MILLISECONDS),
    ),
    max: toDateTimeLocalValue(
      new Date(now.getTime() + MAX_EXPIRATION_MILLISECONDS - UI_BOUNDARY_BUFFER_MILLISECONDS),
    ),
  };
}

export function isExpirationWithinBackendWindow(value: string, now: Date = new Date()): boolean {
  const iso = dateTimeLocalToIso(value);

  if (iso === null) {
    return false;
  }

  const expiration = new Date(iso).getTime();
  const earliest = now.getTime() + MIN_EXPIRATION_MILLISECONDS;
  const latest = now.getTime() + MAX_EXPIRATION_MILLISECONDS;

  return expiration >= earliest && expiration <= latest;
}
