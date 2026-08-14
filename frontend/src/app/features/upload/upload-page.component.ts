import { DatePipe } from '@angular/common';
import {
  AbstractControl,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { FileDropApiService } from '../../core/api/file-drop-api.service';
import type {
  CreateDropRequest,
  CreateDropResponse,
  UiApiError,
} from '../../core/models/file-drop.models';
import { ApiErrorComponent } from '../../shared/components/api-error/api-error.component';
import { FileSizePipe } from '../../shared/pipes/file-size.pipe';
import { normalizeApiError } from '../../shared/utils/api-error.utils';
import {
  dateTimeLocalToIso,
  defaultExpirationValue,
  expirationInputBounds,
  isExpirationWithinBackendWindow,
} from '../../shared/utils/date.utils';
import { FormBuilder } from '@angular/forms';

const MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024;
const MAX_FILE_NAME_LENGTH = 255;
const CONTROL_CHARACTER_PATTERN = /[\u0000-\u001f\u007f-\u009f]/u;

type CopiedLink = 'share' | 'management';

interface UploadResult {
  readonly response: CreateDropResponse;
  readonly fileName: string;
  readonly shareUrl: string;
  readonly managementUrl: string;
}

const expirationWindowValidator: ValidatorFn = (
  control: AbstractControl<unknown>,
): ValidationErrors | null => {
  const value = control.value;
  return typeof value === 'string' && isExpirationWithinBackendWindow(value)
    ? null
    : { expirationWindow: true };
};

const wholeNumberValidator: ValidatorFn = (
  control: AbstractControl<unknown>,
): ValidationErrors | null =>
  typeof control.value === 'number' && Number.isInteger(control.value)
    ? null
    : { wholeNumber: true };

const optionalPasswordValidator: ValidatorFn = (
  control: AbstractControl<unknown>,
): ValidationErrors | null => {
  const value = control.value;

  if (value === '') {
    return null;
  }

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
  selector: 'app-upload-page',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, ApiErrorComponent, FileSizePipe],
  templateUrl: './upload-page.component.html',
  styleUrl: './upload-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UploadPageComponent {
  private readonly formBuilder = inject(FormBuilder).nonNullable;
  private readonly fileDropApi = inject(FileDropApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('fileInput');

  private dragDepth = 0;
  private copyConfirmationTimer: number | null = null;

  readonly acceptedFileTypes = [
    '.pdf',
    '.txt',
    '.csv',
    '.json',
    '.xml',
    '.doc',
    '.docx',
    '.xls',
    '.xlsx',
    '.ppt',
    '.pptx',
    '.odt',
    '.ods',
    '.odp',
    '.jpg',
    '.jpeg',
    '.png',
    '.gif',
    '.webp',
    '.zip',
    '.7z',
    '.rar',
    '.mp4',
    '.webm',
    '.mp3',
    '.wav',
    '.flac',
  ].join(',');

  readonly form = this.formBuilder.group({
    expiresAt: [defaultExpirationValue(), [Validators.required, expirationWindowValidator]],
    maxDownloads: [
      10,
      [Validators.required, Validators.min(1), Validators.max(100), wholeNumberValidator],
    ],
    password: ['', [optionalPasswordValidator]],
  });

  readonly selectedFile = signal<File | null>(null);
  readonly fileError = signal<string | null>(null);
  readonly apiError = signal<UiApiError | null>(null);
  readonly isDragging = signal(false);
  readonly isUploading = signal(false);
  readonly showPassword = signal(false);
  readonly uploadResult = signal<UploadResult | null>(null);
  readonly copiedLink = signal<CopiedLink | null>(null);
  readonly copyFailed = signal(false);

  minimumExpiration = expirationInputBounds().min;
  maximumExpiration = expirationInputBounds().max;

  constructor() {
    this.destroyRef.onDestroy(() => {
      if (this.copyConfirmationTimer !== null) {
        window.clearTimeout(this.copyConfirmationTimer);
      }
    });
  }

  onFileInput(event: Event): void {
    const input = event.target;

    if (!(input instanceof HTMLInputElement)) {
      return;
    }

    const file = input.files?.item(0);

    if (file) {
      this.useFile(file);
    }
  }

  onDragEnter(event: DragEvent): void {
    event.preventDefault();

    if (this.isUploading()) {
      return;
    }

    this.dragDepth += 1;
    this.isDragging.set(true);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();

    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = this.isUploading() ? 'none' : 'copy';
    }
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragDepth = Math.max(0, this.dragDepth - 1);

    if (this.dragDepth === 0) {
      this.isDragging.set(false);
    }
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragDepth = 0;
    this.isDragging.set(false);

    if (this.isUploading()) {
      return;
    }

    const file = event.dataTransfer?.files.item(0);

    if (file) {
      this.useFile(file);
    }
  }

  chooseAnother(): void {
    if (!this.isUploading()) {
      const input = this.fileInput()?.nativeElement;

      if (input) {
        input.value = '';
        input.click();
      }
    }
  }

  removeFile(): void {
    if (this.isUploading()) {
      return;
    }

    this.selectedFile.set(null);
    this.fileError.set(null);
    this.resetFileInput();
  }

  async submit(): Promise<void> {
    if (this.isUploading()) {
      return;
    }

    this.apiError.set(null);
    this.fileError.set(null);
    this.refreshExpirationBounds();
    this.form.controls.expiresAt.updateValueAndValidity();
    this.form.markAllAsTouched();

    const file = this.selectedFile();

    if (!file) {
      this.fileError.set('Choose a file before creating the drop.');
      return;
    }

    const fileValidationMessage = this.validateFile(file);

    if (fileValidationMessage !== null) {
      this.fileError.set(fileValidationMessage);
      return;
    }

    if (this.form.invalid) {
      return;
    }

    const expiresAt = dateTimeLocalToIso(this.form.controls.expiresAt.value);

    if (expiresAt === null) {
      this.form.controls.expiresAt.setErrors({ invalidDate: true });
      return;
    }

    const password = this.form.controls.password.value;
    const request: CreateDropRequest = {
      expiresAt,
      maxDownloads: this.form.controls.maxDownloads.value,
      password: password === '' ? null : password,
    };

    this.isUploading.set(true);

    try {
      const response = await firstValueFrom(this.fileDropApi.createDrop(file, request));
      const token = this.extractDownloadToken(response.downloadUrl);

      this.uploadResult.set({
        response,
        fileName: file.name,
        shareUrl: this.buildShareUrl(token),
        managementUrl: this.buildManagementUrl(response.id, response.managementToken),
      });
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (error: unknown) {
      this.apiError.set(await normalizeApiError(error));
    } finally {
      this.isUploading.set(false);
    }
  }

  uploadAnother(): void {
    this.uploadResult.set(null);
    this.apiError.set(null);
    this.fileError.set(null);
    this.selectedFile.set(null);
    this.showPassword.set(false);
    this.copiedLink.set(null);
    this.copyFailed.set(false);
    this.refreshExpirationBounds();
    this.form.reset({
      expiresAt: defaultExpirationValue(),
      maxDownloads: 10,
      password: '',
    });
    this.resetFileInput();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async copyLink(value: string, kind: CopiedLink): Promise<void> {
    this.copyFailed.set(false);

    try {
      await this.writeClipboard(value);
      this.copiedLink.set(kind);

      if (this.copyConfirmationTimer !== null) {
        window.clearTimeout(this.copyConfirmationTimer);
      }

      this.copyConfirmationTimer = window.setTimeout(() => {
        this.copiedLink.set(null);
        this.copyConfirmationTimer = null;
      }, 2_000);
    } catch {
      this.copiedLink.set(null);
      this.copyFailed.set(true);
    }
  }

  private useFile(file: File): void {
    this.apiError.set(null);
    const validationMessage = this.validateFile(file);

    if (validationMessage !== null) {
      this.selectedFile.set(null);
      this.fileError.set(validationMessage);
      this.resetFileInput();
      return;
    }

    this.selectedFile.set(file);
    this.fileError.set(null);
  }

  private validateFile(file: File): string | null {
    if (file.size === 0) {
      return 'The selected file is empty. Choose a file with content.';
    }

    if (file.size > MAX_FILE_SIZE_BYTES) {
      return 'The selected file is larger than the 100 MB limit.';
    }

    if (file.name.length > MAX_FILE_NAME_LENGTH) {
      return 'The filename must be 255 characters or fewer.';
    }

    return null;
  }

  private resetFileInput(): void {
    const input = this.fileInput()?.nativeElement;

    if (input) {
      input.value = '';
    }
  }

  private refreshExpirationBounds(): void {
    const bounds = expirationInputBounds();
    this.minimumExpiration = bounds.min;
    this.maximumExpiration = bounds.max;
  }

  private extractDownloadToken(downloadUrl: string): string {
    const url = new URL(downloadUrl, window.location.origin);
    const segments = url.pathname.split('/').filter(Boolean);
    const token = segments.at(-1);

    if (!token) {
      throw new Error('The backend returned a download URL without a token.');
    }

    return decodeURIComponent(token);
  }

  private buildShareUrl(token: string): string {
    return new URL(`/download/${encodeURIComponent(token)}`, window.location.origin).toString();
  }

  private buildManagementUrl(id: string, managementToken: string): string {
    const url = new URL(`/manage/${encodeURIComponent(id)}`, window.location.origin);
    const fragmentParams = new URLSearchParams({ token: managementToken });
    url.hash = fragmentParams.toString();
    return url.toString();
  }

  private async writeClipboard(value: string): Promise<void> {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(value);
      return;
    }

    const textarea = document.createElement('textarea');
    textarea.value = value;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();

    const copied = document.execCommand('copy');
    textarea.remove();

    if (!copied) {
      throw new Error('Clipboard access was denied.');
    }
  }
}
