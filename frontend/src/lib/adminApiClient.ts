/**
 * Thin fetch wrapper around the SpendWise Admin Portal REST API (`/api/v1/admin/**`, E11-S3-T1).
 *
 * Deliberately simpler than `lib/apiClient.ts`: there is no refresh-token concept for admin
 * sessions (see `lib/adminAuth.ts`), so a 401 just clears the token and lets the caller's
 * `AdminAuthGuard` redirect to `/admin/login` on the next render — no retry-after-refresh logic.
 */

import { clearAdminAccessToken, getAdminAccessToken } from "@/lib/adminAuth";
import { ApiError } from "@/lib/apiClient";

function baseUrl(): string {
  const root = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";
  return root.replace(/\/$/, "");
}

function apiUrl(path: string): string {
  const normalized = path.startsWith("/") ? path : `/${path}`;
  return `${baseUrl()}/api/v1${normalized}`;
}

interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: unknown;
  /** Skip attaching the Authorization header (used only for `/admin/auth/login`). */
  auth?: boolean;
}

function buildHeaders(options: RequestOptions, token: string | null): Headers {
  const headers = new Headers(options.headers);
  if (options.body !== undefined && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (options.auth !== false && token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  return headers;
}

async function parseError(res: Response): Promise<ApiError> {
  try {
    const data = (await res.json()) as { error?: string; message?: string };
    return new ApiError(res.status, data.message ?? res.statusText, data.error ?? null);
  } catch {
    return new ApiError(res.status, res.statusText);
  }
}

async function adminRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const skipAuth = options.auth === false;
  const res = await fetch(apiUrl(path), {
    ...options,
    headers: buildHeaders(options, skipAuth ? null : getAdminAccessToken()),
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  if (res.status === 401) {
    clearAdminAccessToken();
  }
  if (!res.ok) {
    throw await parseError(res);
  }
  if (res.status === 204 || res.headers.get("Content-Length") === "0") {
    return undefined as T;
  }
  return (await res.json()) as T;
}

export const adminApiClient = {
  get: <T>(path: string, options?: RequestOptions) => adminRequest<T>(path, { ...options, method: "GET" }),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) => adminRequest<T>(path, { ...options, method: "POST", body }),
  put: <T>(path: string, body?: unknown, options?: RequestOptions) => adminRequest<T>(path, { ...options, method: "PUT", body }),
  delete: <T>(path: string, options?: RequestOptions) => adminRequest<T>(path, { ...options, method: "DELETE" }),
};

/** SWR fetcher for `useAdminApi`. */
export function adminSwrFetcher<T>(path: string): Promise<T> {
  return adminRequest<T>(path, { method: "GET" });
}
