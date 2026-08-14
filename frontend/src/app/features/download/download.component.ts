import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
} from '@angular/forms';
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

const CONTROL_CHARACTER_PATTERN = /[\u0000-\u001f\u007f-\u009f]/u;

const downloadPasswordValidator: ValidatorFn = (
  control: AbstractControl<unknown>,
): ValidationErrors | null => {
  const value = control.value;

  if (typeof value !== 'string') {
    return { password: true };
  }

  const codePointLength = Array.from(value).length;
  const isBlank = value.trim().length === 0;
  const hasControlCharacter = CONTROL_CHARACTER_PATTERN.test(value);

  return codePointLength >= 8 && codePointLength <= 128 && !isBlank && !hasControlCharacter
    ? null
    : { password: true };
};

@Component({
  selector: 'app-download',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './download.component.html',
  styleUrl: './download.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DownloadComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly fileDropApi = inject(FileDropApiService);
  private readonly passwordInput = viewChild<ElementRef<HTMLInputElement>>('passwordInput');

  private token = '';
  private requestId = 0;

  readonly loading = signal(false);
  readonly passwordRequired = signal(false);
  readonly showPassword = signal(false);
  readonly passwordError = signal<string | null>(null);
  readonly passwordControl = new FormControl('', {
    nonNullable: true,
    validators: [downloadPasswordValidator],
  });
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
        this.passwordRequired.set(false);
        this.showPassword.set(false);
        this.passwordError.set(null);
        this.passwordControl.reset('');
        this.error.set(null);
        this.downloadedFilename.set(null);
        this.downloadsRemaining.set(null);
      });
  }

  async download(): Promise<void> {
    if (this.loading()) {
      return;
    }

    const password = this.passwordRequired() ? this.passwordControl.value : undefined;

    if (this.passwordRequired()) {
      this.passwordControl.markAsTouched();

      if (this.passwordControl.invalid) {
        this.focusPasswordInput();
        return;
      }
    }

    this.loading.set(true);
    this.passwordError.set(null);
    this.error.set(null);
    this.downloadedFilename.set(null);
    this.downloadsRemaining.set(null);

    const requestId = ++this.requestId;
    const token = this.token;

    try {
      if (!token) {
        throw new Error('This download link is incomplete.');
      }

      const response = await firstValueFrom(this.fileDropApi.download(token, password));

      if (requestId !== this.requestId) {
        return;
      }

      if (!response.body) {
        throw new Error('The server returned an empty download.');
      }

      const filename =
        extractFilename(response.headers.get('Content-Disposition')) ?? 'filedrop-download';

      triggerBrowserDownload(response.body, filename);

      if (password !== undefined) {
        this.passwordControl.reset('');
        this.showPassword.set(false);
      }

      this.downloadedFilename.set(filename);
      this.downloadsRemaining.set(
        this.parseDownloadsRemaining(response.headers.get('X-Downloads-Remaining')),
      );
    } catch (error: unknown) {
      const normalizedError = await normalizeApiError(error);

      if (requestId === this.requestId) {
        if (normalizedError.code === 'DOWNLOAD_PASSWORD_REQUIRED') {
          this.passwordRequired.set(true);
          this.error.set(null);
          this.focusPasswordInput();
        } else if (normalizedError.code === 'INVALID_DOWNLOAD_PASSWORD') {
          this.passwordRequired.set(true);
          this.passwordControl.reset('');
          this.passwordError.set('Incorrect password. Try again.');
          this.error.set(null);
          this.focusPasswordInput();
        } else {
          this.error.set(normalizedError);
        }
      }
    } finally {
      if (requestId === this.requestId) {
        this.loading.set(false);
      }
    }
  }

  submitPassword(event: Event): void {
    event.preventDefault();
    void this.download();
  }

  clearPasswordError(): void {
    this.passwordError.set(null);
  }

  private focusPasswordInput(): void {
    window.setTimeout(() => this.passwordInput()?.nativeElement.focus(), 0);
  }

  private parseDownloadsRemaining(value: string | null): number | null {
    if (value === null || value.trim() === '') {
      return null;
    }

    const parsed = Number(value);
    return Number.isInteger(parsed) && parsed >= 0 ? parsed : null;
  }
}
