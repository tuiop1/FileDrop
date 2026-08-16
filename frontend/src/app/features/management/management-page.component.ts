import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { combineLatest, distinctUntilChanged, firstValueFrom, map } from 'rxjs';

import { FileDropApiService } from '../../core/api/file-drop-api.service';
import type {
  FileDropDetails,
  FileDropStatus,
  UiApiError,
} from '../../core/models/file-drop.models';
import { ApiErrorComponent } from '../../shared/components/api-error/api-error.component';
import { FileSizePipe } from '../../shared/pipes/file-size.pipe';
import { normalizeApiError } from '../../shared/utils/api-error.utils';
import {
  dateTimeLocalToIso,
  expirationInputBounds,
  isExpirationWithinBackendWindow,
  toDateTimeLocalValue,
} from '../../shared/utils/date.utils';

type ManagementOperation = 'expiration' | 'downloads' | 'delete';

interface ManagementRequestContext {
  readonly generation: number;
  readonly id: string;
  readonly token: string;
}

@Component({
  selector: 'app-management-page',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, RouterLink, ApiErrorComponent, FileSizePipe],
  templateUrl: './management-page.component.html',
  styleUrl: './management-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ManagementPageComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly fileDropApi = inject(FileDropApiService);
  private readonly formBuilder = inject(FormBuilder).nonNullable;

  private dropId = '';
  private managementToken = '';
  private loadRequestId = 0;
  private routeGeneration = 0;
  private readonly deleteTrigger = viewChild<ElementRef<HTMLButtonElement>>('deleteTrigger');
  private readonly cancelDeleteButton =
    viewChild<ElementRef<HTMLButtonElement>>('cancelDeleteButton');
  private readonly confirmDialog = viewChild<ElementRef<HTMLElement>>('confirmDialog');
  private readonly managementHeading =
    viewChild<ElementRef<HTMLHeadingElement>>('managementHeading');

  readonly details = signal<FileDropDetails | null>(null);
  readonly apiError = signal<UiApiError | null>(null);
  readonly isLoading = signal(true);
  readonly activeOperation = signal<ManagementOperation | null>(null);
  readonly operationMessage = signal<string | null>(null);
  readonly showDeleteConfirmation = signal(false);
  readonly isBusy = computed(() => this.isLoading() || this.activeOperation() !== null);

  readonly expirationForm = this.formBuilder.group({
    expiresAt: ['', [Validators.required]],
  });

  readonly downloadsForm = this.formBuilder.group({
    maxDownloads: [
      1,
      [
        Validators.required,
        Validators.min(1),
        Validators.max(100),
        (control) => (Number.isInteger(control.value) ? null : { wholeNumber: true }),
      ],
    ],
  });

  minimumExpiration = expirationInputBounds().min;
  maximumExpiration = expirationInputBounds().max;

  constructor() {
    combineLatest([this.route.paramMap, this.route.fragment])
      .pipe(
        map(([params, fragment]) => ({
          id: params.get('id') ?? '',
          token: new URLSearchParams(fragment ?? '').get('token') ?? '',
        })),
        distinctUntilChanged(
          (previous, current) => previous.id === current.id && previous.token === current.token,
        ),
        takeUntilDestroyed(),
      )
      .subscribe(({ id, token }) => {
        this.routeGeneration += 1;
        this.loadRequestId += 1;
        this.dropId = id;
        this.managementToken = token;
        this.activeOperation.set(null);
        this.showDeleteConfirmation.set(false);
        this.details.set(null);
        void this.loadDetails();
      });
  }

  async loadDetails(): Promise<void> {
    const context = this.currentRequestContext();
    const requestId = ++this.loadRequestId;
    this.apiError.set(null);
    this.operationMessage.set(null);

    if (!this.dropId || !this.managementToken) {
      this.isLoading.set(false);
      this.details.set(null);
      this.apiError.set({
        message:
          'This management link is incomplete. It must include both the drop ID and its secret management token.',
        status: null,
        code: 'INCOMPLETE_MANAGEMENT_LINK',
        fieldErrors: {},
      });
      return;
    }

    this.isLoading.set(true);

    try {
      const details = await firstValueFrom(this.fileDropApi.getDetails(context.id, context.token));

      if (requestId === this.loadRequestId && this.isCurrentContext(context)) {
        this.applyDetails(details);
      }
    } catch (error: unknown) {
      const normalizedError = await normalizeApiError(error);

      if (requestId === this.loadRequestId && this.isCurrentContext(context)) {
        this.apiError.set(normalizedError);
      }
    } finally {
      if (requestId === this.loadRequestId && this.isCurrentContext(context)) {
        this.isLoading.set(false);
      }
    }
  }

  async updateExpiration(): Promise<void> {
    const drop = this.details();

    if (!drop || !this.canEdit(drop) || this.isBusy()) {
      return;
    }

    this.refreshExpirationBounds();
    this.expirationForm.markAllAsTouched();
    const localValue = this.expirationForm.controls.expiresAt.value;
    const expiresAt = dateTimeLocalToIso(localValue);

    if (expiresAt === null || !isExpirationWithinBackendWindow(localValue)) {
      this.expirationForm.controls.expiresAt.setErrors({ expirationWindow: true });
      return;
    }

    const context = this.currentRequestContext();
    this.beginOperation('expiration');

    try {
      const updated = await firstValueFrom(
        this.fileDropApi.updateExpiration(context.id, context.token, { expiresAt }),
      );

      if (this.isCurrentContext(context)) {
        this.applyDetails(updated);
        this.operationMessage.set('Expiration updated.');
      }
    } catch (error: unknown) {
      const normalizedError = await normalizeApiError(error);

      if (this.isCurrentContext(context)) {
        this.apiError.set(normalizedError);
      }
    } finally {
      if (this.isCurrentContext(context) && this.activeOperation() === 'expiration') {
        this.activeOperation.set(null);
      }
    }
  }

  async updateMaxDownloads(): Promise<void> {
    const drop = this.details();

    if (!drop || !this.canEdit(drop) || this.isBusy()) {
      return;
    }

    this.downloadsForm.markAllAsTouched();
    const maxDownloads = this.downloadsForm.controls.maxDownloads.value;

    if (
      this.downloadsForm.invalid ||
      !Number.isInteger(maxDownloads) ||
      maxDownloads <= drop.downloadCount
    ) {
      this.downloadsForm.controls.maxDownloads.setErrors({
        greaterThanDownloadCount: true,
      });
      return;
    }

    const context = this.currentRequestContext();
    this.beginOperation('downloads');

    try {
      const updated = await firstValueFrom(
        this.fileDropApi.updateMaxDownloads(context.id, context.token, {
          maxDownloads,
        }),
      );

      if (this.isCurrentContext(context)) {
        this.applyDetails(updated);
        this.operationMessage.set('Maximum downloads updated.');
      }
    } catch (error: unknown) {
      const normalizedError = await normalizeApiError(error);

      if (this.isCurrentContext(context)) {
        this.apiError.set(normalizedError);
      }
    } finally {
      if (this.isCurrentContext(context) && this.activeOperation() === 'downloads') {
        this.activeOperation.set(null);
      }
    }
  }

  async deleteDrop(): Promise<void> {
    const drop = this.details();

    if (!drop || this.isBusy()) {
      return;
    }

    const context = this.currentRequestContext();
    this.beginOperation('delete');
    window.setTimeout(() => this.confirmDialog()?.nativeElement.focus());

    try {
      await firstValueFrom(this.fileDropApi.deleteDrop(context.id, context.token));

      if (!this.isCurrentContext(context)) {
        return;
      }

      const deletionPending: FileDropDetails = {
        ...drop,
        status: 'DELETION_PENDING',
      };
      this.applyDetails(deletionPending);
      this.operationMessage.set(
        'Deletion requested. Scheduled cleanup will remove the stored file shortly.',
      );
      this.showDeleteConfirmation.set(false);
      window.setTimeout(() => this.managementHeading()?.nativeElement.focus());
    } catch (error: unknown) {
      const normalizedError = await normalizeApiError(error);

      if (this.isCurrentContext(context)) {
        this.apiError.set(normalizedError);
        this.closeDeleteConfirmation(true);
      }
    } finally {
      if (this.isCurrentContext(context) && this.activeOperation() === 'delete') {
        this.activeOperation.set(null);
      }
    }
  }

  openDeleteConfirmation(): void {
    const drop = this.details();

    if (!drop || this.isBusy() || drop.status === 'DELETION_PENDING' || drop.status === 'DELETED') {
      return;
    }

    this.showDeleteConfirmation.set(true);
    window.setTimeout(() => this.cancelDeleteButton()?.nativeElement.focus());
  }

  cancelDelete(): void {
    if (this.activeOperation() !== 'delete') {
      this.closeDeleteConfirmation(true);
    }
  }

  onDialogKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.cancelDelete();
      return;
    }

    if (event.key !== 'Tab') {
      return;
    }

    const dialog = this.confirmDialog()?.nativeElement;
    const focusable = dialog
      ? Array.from(dialog.querySelectorAll<HTMLElement>('button:not(:disabled)'))
      : [];

    if (focusable.length === 0) {
      event.preventDefault();
      return;
    }

    const first = focusable[0];
    const last = focusable.at(-1);

    if (event.shiftKey && document.activeElement === first && last) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last && first) {
      event.preventDefault();
      first.focus();
    }
  }

  canEdit(drop: FileDropDetails): boolean {
    return drop.status === 'AVAILABLE';
  }

  downloadProgress(drop: FileDropDetails): number {
    if (drop.maxDownloads <= 0) {
      return 0;
    }

    return Math.min(100, Math.max(0, (drop.downloadCount / drop.maxDownloads) * 100));
  }

  statusLabel(status: FileDropStatus): string {
    return status.replaceAll('_', ' ');
  }

  statusHeading(status: FileDropStatus): string {
    switch (status) {
      case 'PENDING':
        return 'The file is still being prepared';
      case 'DELETION_PENDING':
        return 'Deletion is scheduled';
      case 'DELETED':
        return 'This drop has been deleted';
      case 'FAILED':
        return 'File processing failed';
      case 'AVAILABLE':
        return 'This drop is available';
    }
  }

  statusDescription(status: FileDropStatus): string {
    switch (status) {
      case 'PENDING':
        return 'Settings remain read-only until backend processing completes.';
      case 'DELETION_PENDING':
        return 'New downloads are blocked while scheduled cleanup removes the stored object.';
      case 'DELETED':
        return 'The stored object has been removed. These details remain available for reference.';
      case 'FAILED':
        return 'The backend could not finish creating this drop. You can still request cleanup.';
      case 'AVAILABLE':
        return 'The file can be downloaded and its limits can be edited.';
    }
  }

  private beginOperation(operation: ManagementOperation): void {
    this.activeOperation.set(operation);
    this.apiError.set(null);
    this.operationMessage.set(null);
  }

  private closeDeleteConfirmation(restoreFocus: boolean): void {
    this.showDeleteConfirmation.set(false);

    if (restoreFocus) {
      window.setTimeout(() => this.deleteTrigger()?.nativeElement.focus());
    }
  }

  private currentRequestContext(): ManagementRequestContext {
    return {
      generation: this.routeGeneration,
      id: this.dropId,
      token: this.managementToken,
    };
  }

  private isCurrentContext(context: ManagementRequestContext): boolean {
    return (
      context.generation === this.routeGeneration &&
      context.id === this.dropId &&
      context.token === this.managementToken
    );
  }

  private applyDetails(details: FileDropDetails): void {
    this.details.set(details);
    this.refreshExpirationBounds();
    this.expirationForm.reset({
      expiresAt: toDateTimeLocalValue(details.expiresAt),
    });
    this.downloadsForm.reset({
      maxDownloads: details.maxDownloads,
    });

    if (this.canEdit(details)) {
      this.expirationForm.enable({ emitEvent: false });
      this.downloadsForm.enable({ emitEvent: false });
    } else {
      this.expirationForm.disable({ emitEvent: false });
      this.downloadsForm.disable({ emitEvent: false });
    }
  }

  private refreshExpirationBounds(): void {
    const bounds = expirationInputBounds();
    this.minimumExpiration = bounds.min;
    this.maximumExpiration = bounds.max;
  }
}
