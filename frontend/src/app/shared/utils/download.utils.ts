const CONTROL_CHARACTERS = /[\u0000-\u001f\u007f-\u009f]/g;

export function extractFilename(contentDisposition: string | null): string | null {
  if (contentDisposition === null || contentDisposition.trim() === '') {
    return null;
  }

  const extendedMatch = /(?:^|;)\s*filename\*\s*=\s*([^;]+)/i.exec(contentDisposition);
  const extendedFilename = extendedMatch?.[1] ? decodeExtendedFilename(extendedMatch[1]) : null;
  const safeExtendedFilename = extendedFilename === null ? null : safeBasename(extendedFilename);

  if (safeExtendedFilename !== null) {
    return safeExtendedFilename;
  }

  const quotedMatch = /(?:^|;)\s*filename\s*=\s*"((?:\\.|[^"])*)"/i.exec(contentDisposition);

  if (quotedMatch?.[1] !== undefined) {
    return safeBasename(quotedMatch[1].replace(/\\([\\"])/g, '$1'));
  }

  const unquotedMatch = /(?:^|;)\s*filename\s*=\s*([^;]+)/i.exec(contentDisposition);

  return unquotedMatch?.[1] ? safeBasename(stripQuotes(unquotedMatch[1].trim())) : null;
}

export function triggerBrowserDownload(blob: Blob, filename: string): void {
  const objectUrl = URL.createObjectURL(blob);
  const anchor = document.createElement('a');

  anchor.href = objectUrl;
  anchor.download = safeBasename(filename) ?? 'filedrop-download';
  anchor.style.display = 'none';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();

  // Revoking on a later task gives the browser time to begin reading the URL.
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1_000);
}

function decodeExtendedFilename(rawValue: string): string | null {
  const value = stripQuotes(rawValue.trim());
  const firstApostrophe = value.indexOf("'");
  const secondApostrophe = firstApostrophe >= 0 ? value.indexOf("'", firstApostrophe + 1) : -1;
  const encodedFilename = secondApostrophe >= 0 ? value.slice(secondApostrophe + 1) : value;

  try {
    return decodeURIComponent(encodedFilename);
  } catch {
    return null;
  }
}

function stripQuotes(value: string): string {
  return value.startsWith('"') && value.endsWith('"') ? value.slice(1, -1) : value;
}

function safeBasename(filename: string): string | null {
  const withoutControlCharacters = filename.replace(CONTROL_CHARACTERS, '');
  const segments = withoutControlCharacters.split(/[\\/]/);
  const basename = segments[segments.length - 1]?.trim() ?? '';

  return basename === '' ? null : basename;
}
