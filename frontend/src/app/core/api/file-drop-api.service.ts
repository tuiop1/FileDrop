import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CreateDropRequest,
  CreateDropResponse,
  FileDropDetails,
  UpdateExpirationRequest,
  UpdateMaxDownloadsRequest,
} from '../models/file-drop.models';

@Injectable({ providedIn: 'root' })
export class FileDropApiService {
  private static readonly API_URL = '/api/v1/drops';

  private readonly http = inject(HttpClient);

  createDrop(file: File, request: CreateDropRequest): Observable<CreateDropResponse> {
    const formData = new FormData();
    const metadata = new Blob([JSON.stringify(request)], {
      type: 'application/json',
    });

    formData.append('file', file, file.name);
    formData.append('metadata', metadata);

    // Do not set Content-Type here. The browser must add the multipart boundary.
    return this.http.post<CreateDropResponse>(FileDropApiService.API_URL, formData);
  }

  download(token: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${FileDropApiService.API_URL}/d/${encodeURIComponent(token)}`, {
      observe: 'response',
      responseType: 'blob',
    });
  }

  getDetails(id: string, managementToken: string): Observable<FileDropDetails> {
    return this.http.get<FileDropDetails>(this.dropUrl(id), {
      headers: this.managementHeaders(managementToken),
    });
  }

  updateExpiration(
    id: string,
    managementToken: string,
    request: UpdateExpirationRequest,
  ): Observable<FileDropDetails> {
    return this.http.patch<FileDropDetails>(`${this.dropUrl(id)}/expiration`, request, {
      headers: this.managementHeaders(managementToken),
    });
  }

  updateMaxDownloads(
    id: string,
    managementToken: string,
    request: UpdateMaxDownloadsRequest,
  ): Observable<FileDropDetails> {
    return this.http.patch<FileDropDetails>(`${this.dropUrl(id)}/max-downloads`, request, {
      headers: this.managementHeaders(managementToken),
    });
  }

  deleteDrop(id: string, managementToken: string): Observable<void> {
    return this.http.delete<void>(this.dropUrl(id), {
      headers: this.managementHeaders(managementToken),
    });
  }

  private dropUrl(id: string): string {
    return `${FileDropApiService.API_URL}/${encodeURIComponent(id)}`;
  }

  private managementHeaders(managementToken: string): HttpHeaders {
    return new HttpHeaders({ 'X-Management-Token': managementToken });
  }
}
