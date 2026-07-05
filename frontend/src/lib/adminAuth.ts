/**
 * Client-side session-token storage for the SpendWise Admin Portal (E11-S3-T1).
 *
 * Deliberately separate from `lib/auth.ts`: the admin token is issued by a completely
 * different credential path (`POST /admin/auth/login`, a seeded username/password — never a
 * regular user account, per CLAUDE.md) and signed with `ADMIN_JWT_SECRET`, a different secret
 * from the user session JWT. There is no refresh-token concept for admin sessions — the token
 * simply expires after 24h (`AdminJwtService.ACCESS_TOKEN_TTL_SECONDS`) and the admin logs in
 * again.
 */

const ADMIN_ACCESS_TOKEN_KEY = "spendwise.admin.accessToken";

function isBrowser(): boolean {
  return typeof window !== "undefined";
}

export function getAdminAccessToken(): string | null {
  if (!isBrowser()) return null;
  return window.localStorage.getItem(ADMIN_ACCESS_TOKEN_KEY);
}

export function setAdminAccessToken(accessToken: string): void {
  if (!isBrowser()) return;
  window.localStorage.setItem(ADMIN_ACCESS_TOKEN_KEY, accessToken);
}

export function clearAdminAccessToken(): void {
  if (!isBrowser()) return;
  window.localStorage.removeItem(ADMIN_ACCESS_TOKEN_KEY);
}

export function isAdminAuthenticated(): boolean {
  return getAdminAccessToken() != null;
}
