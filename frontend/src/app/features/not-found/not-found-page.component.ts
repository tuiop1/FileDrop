import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found-page',
  imports: [RouterLink],
  template: `
    <main class="page page--state">
      <section class="standalone-error" aria-labelledby="not-found-title">
        <span class="state-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24">
            <path d="M7 3.5h6.5L18 8v12.5H7z" />
            <path d="M13.5 3.5V8H18M9 15h6" />
          </svg>
        </span>
        <p class="eyebrow">404 · Not found</p>
        <h1 id="not-found-title">This link doesn’t point to a FileDrop page</h1>
        <p>Check the address, or head back to the upload page to create a new drop.</p>
        <a class="button button--primary" routerLink="/">Go to upload</a>
      </section>
    </main>
  `,
  styles: `
    :host {
      display: block;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFoundPageComponent {}
