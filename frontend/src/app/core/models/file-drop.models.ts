export interface CreateDropRequest {
  readonly maxDownloads: number;
  readonly expiresAt: string;
  readonly password?: string | null;
}

export interface CreateDropResponse {
  readonly id: string;
  readonly downloadUrl: string;
  readonly managementToken: string;
  readonly expiresAt: string;
}

export type FileDropStatus = 'PENDING' | 'AVAILABLE' | 'DELETION_PENDING' | 'DELETED' | 'FAILED';

export interface FileDropDetails {
  readonly id: string;
  readonly originalFileName: string;
  readonly contentType: string;
  readonly size: number;
  readonly createdAt: string;
  readonly expiresAt: string;
  readonly deletedAt: string | null;
  readonly maxDownloads: number;
  readonly downloadCount: number;
  readonly downloadsRemaining: number;
  readonly status: FileDropStatus;
  readonly passwordProtected: boolean;
}

export interface UpdateExpirationRequest {
  readonly expiresAt: string;
}

export interface UpdateMaxDownloadsRequest {
  readonly maxDownloads: number;
}

export interface ApiError {
  readonly timestamp: string;
  readonly status: number;
  readonly code: string;
  readonly message: string;
  readonly path: string;
  readonly errors?: Readonly<Record<string, readonly string[]>>;
}

export interface UiApiError {
  readonly message: string;
  readonly status: number | null;
  readonly code: string | null;
  readonly fieldErrors: Readonly<Record<string, readonly string[]>>;
}
