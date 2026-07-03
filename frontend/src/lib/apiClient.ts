/**
 * Thin fetch wrapper around the SpendWise Spring Boot REST API.
 *
 * Responsibilities (E10-S1-T1 / E10-S1-T2):
 *  - Prefix every path with `NEXT_PUBLIC_API_BASE_URL` + `/api/v1`.
 *  - Attach `Authorization: Bearer <access token>` from client storage.
 *  - On a 401, transparently refresh the access token once via
 *    `POST /auth/token/refresh` (rotating the refresh token per docs/security.md) and
 *    retry the original request. A second 401 — or a failed refresh — clears the session
 *    and surfaces an `ApiError` the caller (route guard) can act on.
 *
 * The token exchanged at login is the SpendWise-issued JWT, never the Firebase ID token
 * (CLAUDE.md Auth pattern).
 */

import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  setTokens,
} from "@/lib/auth";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string | null;

  constructor(status: number, message: string, code: string | null = null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

function baseUrl(): string {
  const root = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";
  // Strip a trailing slash so we can join cleanly with `/api/v1/...`.
  return root.replace(/\/$/, "");
}

function apiUrl(path: string): string {
  const normalized = path.startsWith("/") ? path : `/${path}`;
  return `${baseUrl()}/api/v1${normalized}`;
}

interface RequestOptions extends Omit<RequestInit, "body"> {
  /** Plain object serialized to JSON, or a raw BodyInit. */
  body?: unknown;
  /** Skip attaching the Authorization header (used for public auth endpoints). */
  auth?: boolean;
}

/**
 * A single refresh promise shared across concurrent 401s, so a burst of parallel
 * requests (the dashboard fires several at once) triggers exactly one token rotation
 * rather than a rotation per request — which would trip the replay-detection logic in
 * RefreshTokenService and revoke every session.
 */
let inFlightRefresh: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return null;

  const res = await fetch(apiUrl("/auth/token/refresh"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });

  if (!res.ok) {
    clearTokens();
    return null;
  }

  const data = (await res.json()) as { accessToken: string; refreshToken: string };
  setTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken });
  return data.accessToken;
}

async function ensureRefreshed(): Promise<string | null> {
  if (!inFlightRefresh) {
    inFlightRefresh = refreshAccessToken().finally(() => {
      inFlightRefresh = null;
    });
  }
  return inFlightRefresh;
}

function buildHeaders(options: RequestOptions, token: string | null): Headers {
  const headers = new Headers(options.headers);
  if (options.body !== undefined && !(options.body instanceof FormData)) {
    if (!headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  }
  if (options.auth !== false && token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  return headers;
}

function serializeBody(body: unknown): BodyInit | undefined {
  if (body === undefined) return undefined;
  if (
    typeof body === "string" ||
    body instanceof FormData ||
    body instanceof Blob ||
    body instanceof URLSearchParams
  ) {
    return body as BodyInit;
  }
  return JSON.stringify(body);
}

async function parseError(res: Response): Promise<ApiError> {
  try {
    const data = (await res.json()) as { error?: string; message?: string };
    return new ApiError(res.status, data.message ?? res.statusText, data.error ?? null);
  } catch {
    return new ApiError(res.status, res.statusText);
  }
}

async function doFetch(path: string, options: RequestOptions, token: string | null): Promise<Response> {
  return fetch(apiUrl(path), {
    ...options,
    headers: buildHeaders(options, token),
    body: serializeBody(options.body),
  });
}

/**
 * Core request method. Returns the parsed JSON body (typed as `T`), or `undefined` for
 * 204 No Content responses.
 */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const skipAuth = options.auth === false;
  let res = await doFetch(path, options, skipAuth ? null : getAccessToken());

  if (res.status === 401 && !skipAuth) {
    const newToken = await ensureRefreshed();
    if (!newToken) {
      throw new ApiError(401, "Session expired");
    }
    res = await doFetch(path, options, newToken);
  }

  if (!res.ok) {
    throw await parseError(res);
  }

  if (res.status === 204 || res.headers.get("Content-Length") === "0") {
    return undefined as T;
  }

  const contentType = res.headers.get("Content-Type") ?? "";
  if (!contentType.includes("application/json")) {
    return (await res.text()) as unknown as T;
  }
  return (await res.json()) as T;
}

export const apiClient = {
  get: <T>(path: string, options?: RequestOptions) =>
    apiRequest<T>(path, { ...options, method: "GET" }),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    apiRequest<T>(path, { ...options, method: "POST", body }),
  put: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    apiRequest<T>(path, { ...options, method: "PUT", body }),
  patch: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    apiRequest<T>(path, { ...options, method: "PATCH", body }),
  delete: <T>(path: string, options?: RequestOptions) =>
    apiRequest<T>(path, { ...options, method: "DELETE" }),
};

/** SWR fetcher: `useSWR('/analytics/summary?...', swrFetcher)`. */
export function swrFetcher<T>(path: string): Promise<T> {
  return apiRequest<T>(path, { method: "GET" });
}
