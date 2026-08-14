import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { distinctUntilChanged, firstValueFrom, map } from 'rxjs';

import { FileDropApiService } from '../../core/api/file-drop-api.service';
import type { UiApiError } from '../../core/models/file-drop.models';
import { normalizeApiError } from '../../shared/utils/api-error.utils';
import { extractFilename, triggerBrowserDownload } from '../../shared/utils/download.utils';

interface FieldErrorView {
  readonly field: string;
  readonly message: string;
}

@Component({
  selector: 'app-download',
  standalone: true,
  templateUrl: './download.component.html',
  styleUrl: './download.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DownloadComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly fileDropApi = inject(FileDropApiService);

  private token = '';
  private requestId = 0;

  readonly loading = signal(false);
  readonly downloadedFilename = signal<string | null>(null);
  readonly downloadsRemaining = signal<number | null>(null);
  readonly error = signal<UiApiError | null>(null);
  readonly fieldErrors = computed<readonly FieldErrorView[]>(() => {
    const error = this.error();

    if (!error) {
      return [];
    }

    return Object.entries(error.fieldErrors).flatMap(([field, messages]) =>
      messages.map((message) => ({ field, message })),
    );
  });

  constructor() {
    this.route.paramMap
      .pipe(
        map((params) => params.get('token') ?? ''),
        distinctUntilChanged(),
        takeUntilDestroyed(),
      )
      .subscribe((token) => {
        this.token = token;
        this.requestId += 1;
        this.loading.set(false);
        this.error.set(null);
        this.downloadedFilename.set(null);
        this.downloadsRemaining.set(null);
      });
  }

  async download(): Promise<void> {
    if (this.loading()) {
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.downloadedFilename.set(null);
    this.downloadsRemaining.set(null);

    const requestId = ++this.requestId;
    const token = this.token;

    try {
      if (!token) {
        throw new Error('This download link is incomplete.');
      }

      const response = await firstValueFrom(this.fileDropApi.download(token));

      if (requestId !== this.requestId) {
        return;
      }

      if (!response.body) {
        throw new Error('The server returned an empty download.');
      }

      const filename =
        extractFilename(response.headers.get('Content-Disposition')) ?? 'filedrop-download';

      triggerBrowserDownload(response.body, filename);

      this.downloadedFilename.set(filename);
      this.downloadsRemaining.set(
        this.parseDownloadsRemaining(response.headers.get('X-Downloads-Remaining')),
      );
    } catch (error: unknown) {
      const normalizedError = await normalizeApiError(error);

      if (requestId === this.requestId) {
        this.error.set(normalizedError);
      }
    } finally {
      if (requestId === this.requestId) {
        this.loading.set(false);
      }
    }
  }

  private parseDownloadsRemaining(value: string | null): number | null {
    if (value === null || value.trim() === '') {
      return null;
    }

    const parsed = Number(value);
    return Number.isInteger(parsed) && parsed >= 0 ? parsed : null;
  }
}
