import { HttpErrorResponse } from '@angular/common/http';

import { UiApiError } from '../../core/models/file-drop.models';

const CODE_MESSAGES: Readonly<Record<string, string>> = {
  FILE_DROP_NOT_FOUND: 'This file drop could not be found, or the link is invalid.',
  FILE_DROP_EXPIRED: 'This file drop has expired.',
  DOWNLOAD_LIMIT_EXCEEDED: 'This file drop has reached its download limit.',
  FILE_DROP_NOT_EDITABLE: 'This file drop can no longer be edited.',
  INVALID_EXPIRATION: 'Choose an expiration between 1 minute and 7 days from now.',
  INVALID_MAX_DOWNLOADS: 'Choose a maximum download count from 1 to 100.',
  VALIDATION_FAILED: 'Some of the supplied values are invalid.',
  EMPTY_FILE: 'Choose a file that is not empty.',
  FILE_TOO_LARGE: 'The selected file is larger than the 100 MB limit.',
  INVALID_FILE_NAME: 'The selected file has an invalid filename.',
  UNSUPPORTED_CONTENT_TYPE: 'The backend does not support this file type.',
  MALWARE_DETECTED: 'The backend rejected this file because malware was detected.',
  DROP_CREATION_FAILED: 'The backend could not create this file drop.',
  DROP_DOWNLOAD_PREPARATION_FAILED: 'The backend could not prepare this file for download.',
};

const STATUS_MESSAGES: Readonly<Record<number, string>> = {
  400: 'The request was not valid.',
  404: 'The requested file drop was not found.',
  409: 'The file drop is not in a state that allows this operation.',
  410: 'This file drop is no longer available.',
  413: 'The selected file is larger than the server allows.',
  415: 'The backend does not support this file type.',
  422: 'The backend could not accept the supplied file.',
  500: 'The backend encountered an internal error.',
  502: 'The frontend could not reach the backend service.',
  503: 'The backend service is temporarily unavailable.',
  504: 'The backend took too long to respond.',
};

type UnknownRecord = Record<string, unknown>;

export async function normalizeApiError(error: unknown): Promise<UiApiError> {
  if (!(error instanceof HttpErrorResponse)) {
    return {
      message:
        error instanceof Error && error.message.trim() !== ''
          ? error.message
          : 'An unexpected error occurred.',
      status: null,
      code: null,
      fieldErrors: {},
    };
  }

  const body = await readBody(error.error);
  const bodyRecord = isRecord(body) ? body : null;
  const status = readNumber(bodyRecord?.['status']) ?? (error.status > 0 ? error.status : null);
  const code = readNonBlankString(bodyRecord?.['code']);
  const fieldErrors = normalizeFieldErrors(bodyRecord?.['errors']);
  const responseMessage = firstNonBlankString(
    bodyRecord?.['message'],
    bodyRecord?.['detail'],
    bodyRecord?.['title'],
    typeof body === 'string' && !looksLikeMarkup(body) ? body : null,
  );

  let message: string;

  if (error.status === 0) {
    message = 'Could not reach the FileDrop backend. Check that it is running and try again.';
  } else if (responseMessage !== null) {
    message = responseMessage;
  } else if (code !== null && CODE_MESSAGES[code] !== undefined) {
    message = CODE_MESSAGES[code];
  } else if (status !== null && STATUS_MESSAGES[status] !== undefined) {
    message = STATUS_MESSAGES[status];
  } else if (status !== null) {
    message = `The request failed with HTTP ${status}.`;
  } else {
    message = 'The request failed unexpectedly.';
  }

  return { message, status, code, fieldErrors };
}

async function readBody(body: unknown): Promise<unknown> {
  if (body instanceof Blob) {
    try {
      return parseText(await body.text());
    } catch {
      return null;
    }
  }

  if (typeof body === 'string') {
    return parseText(body);
  }

  return body;
}

function parseText(text: string): unknown {
  const trimmed = text.trim();

  if (trimmed === '') {
    return null;
  }

  try {
    return JSON.parse(trimmed) as unknown;
  } catch {
    return trimmed;
  }
}

function normalizeFieldErrors(value: unknown): Readonly<Record<string, readonly string[]>> {
  if (!isRecord(value)) {
    return {};
  }

  const result: Record<string, readonly string[]> = {};

  for (const [field, rawMessages] of Object.entries(value)) {
    const messages = Array.isArray(rawMessages)
      ? rawMessages.filter((message): message is string => typeof message === 'string')
      : typeof rawMessages === 'string'
        ? [rawMessages]
        : [];

    if (messages.length > 0) {
      result[field] = messages;
    }
  }

  return result;
}

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function readNonBlankString(value: unknown): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value.trim() : null;
}

function firstNonBlankString(...values: readonly unknown[]): string | null {
  for (const value of values) {
    const stringValue = readNonBlankString(value);

    if (stringValue !== null) {
      return stringValue;
    }
  }

  return null;
}

function looksLikeMarkup(value: string): boolean {
  return /^\s*</.test(value);
}
