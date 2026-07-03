/**
 * Client-side session-token storage for the SpendWise web dashboard.
 *
 * Per docs/architecture.md and docs/deployment.md, the Next.js frontend calls the
 * Spring Boot REST API directly — there is no Next.js BFF / API-route proxy layer that
 * could set or read an httpOnly cookie. The epic (E10-S1-T2) explicitly allows either
 * "httpOnly-cookie-backed OR secure client storage"; without a server layer to own the
 * cookie, secure client storage is the only option consistent with the current
 * architecture.
 *
 * The tokens stored here are the SpendWise-issued access + refresh tokens (NOT the
 * Firebase ID token — per CLAUDE.md's Auth pattern, the Firebase credential is exchanged
 * for a SpendWise JWT at login and only the SpendWise JWT is used as the session
 * credential).
 */

const ACCESS_TOKEN_KEY = "spendwise.accessToken";
const REFRESH_TOKEN_KEY = "spendwise.refreshToken";

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
}

function isBrowser(): boolean {
  return typeof window !== "undefined";
}

export function getAccessToken(): string | null {
  if (!isBrowser()) return null;
  return window.localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  if (!isBrowser()) return null;
  return window.localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function setTokens(tokens: AuthTokens): void {
  if (!isBrowser()) return;
  window.localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
  window.localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
}

export function setAccessToken(accessToken: string): void {
  if (!isBrowser()) return;
  window.localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
}

export function clearTokens(): void {
  if (!isBrowser()) return;
  window.localStorage.removeItem(ACCESS_TOKEN_KEY);
  window.localStorage.removeItem(REFRESH_TOKEN_KEY);
}

export function isAuthenticated(): boolean {
  return getAccessToken() != null;
}
