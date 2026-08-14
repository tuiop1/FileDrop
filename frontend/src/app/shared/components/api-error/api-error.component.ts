import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import type { UiApiError } from '../../../core/models/file-drop.models';

interface FieldErrorView {
  readonly field: string;
  readonly message: string;
}

@Component({
  selector: 'app-api-error',
  standalone: true,
  template: `
    <section class="api-error" role="alert">
      <div class="api-error__heading">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="12" r="9" />
          <path d="M12 7v6m0 4h.01" />
        </svg>
        <div>
          <strong>{{ error().message }}</strong>
          @if (errorLabel(); as label) {
            <span>{{ label }}</span>
          }
        </div>
      </div>
      @if (fieldErrors().length > 0) {
        <ul>
          @for (item of fieldErrors(); track item.field + item.message) {
            <li>
              <strong>{{ item.field }}:</strong> {{ item.message }}
            </li>
          }
        </ul>
      }
    </section>
  `,
  styles: `
    :host {
      display: block;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ApiErrorComponent {
  readonly error = input.required<UiApiError>();

  readonly errorLabel = computed(() => {
    const error = this.error();
    const parts: string[] = [];

    if (error.code) {
      parts.push(error.code);
    }

    if (error.status !== null && error.status > 0) {
      parts.push(`HTTP ${error.status}`);
    }

    return parts.length > 0 ? parts.join(' · ') : null;
  });

  readonly fieldErrors = computed<readonly FieldErrorView[]>(() =>
    Object.entries(this.error().fieldErrors).flatMap(([field, messages]) =>
      messages.map((message) => ({ field, message })),
    ),
  );
}
