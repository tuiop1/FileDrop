import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'Upload a file · FileDrop',
    loadComponent: () =>
      import('./features/upload/upload-page.component').then(
        (component) => component.UploadPageComponent,
      ),
  },
  {
    path: 'download/:token',
    title: 'Download file · FileDrop',
    loadComponent: () =>
      import('./features/download/download.component').then(
        (component) => component.DownloadComponent,
      ),
  },
  {
    path: 'manage/:id',
    title: 'Manage file drop · FileDrop',
    loadComponent: () =>
      import('./features/management/management-page.component').then(
        (component) => component.ManagementPageComponent,
      ),
  },
  {
    path: '**',
    title: 'Page not found · FileDrop',
    loadComponent: () =>
      import('./features/not-found/not-found-page.component').then(
        (component) => component.NotFoundPageComponent,
      ),
  },
];
