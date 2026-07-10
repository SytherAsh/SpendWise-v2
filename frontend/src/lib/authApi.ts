/**
 * SpendWise session lifecycle: exchange a Firebase ID token for a SpendWise JWT, and
 * revoke the session on logout.
 *
 * Kept separate from `apiClient.ts` so the login/logout calls (which manage the tokens
 * themselves) don't recurse through the 401-refresh interceptor.
 */

import { apiClient } from "@/lib/apiClient";
import { clearTokens, getRefreshToken, setTokens } from "@/lib/auth";

export interface AuthTokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: { id: string; phone: string | null; email: string | null };
}

/** Matches the backend's `demo.phone` default (application.yml) — used to detect a demo session client-side. */
export const DEMO_PHONE = "+919876543210";

/** Exchange a phone-OTP Firebase ID token for a SpendWise session. */
export async function verifyOtp(phone: string, idToken: string): Promise<AuthTokenResponse> {
  const res = await apiClient.post<AuthTokenResponse>(
    "/auth/otp/verify",
    { phone, idToken },
    { auth: false },
  );
  setTokens({ accessToken: res.accessToken, refreshToken: res.refreshToken });
  return res;
}

/** Exchange a Google Firebase ID token for a SpendWise session. */
export async function googleLogin(idToken: string): Promise<AuthTokenResponse> {
  const res = await apiClient.post<AuthTokenResponse>(
    "/auth/google",
    { idToken },
    { auth: false },
  );
  setTokens({ accessToken: res.accessToken, refreshToken: res.refreshToken });
  return res;
}

/**
 * Local-dev-only shortcut: mints a real session for a seeded test user, skipping Firebase
 * entirely. Only ever succeeds against a backend running with the "local" Spring profile
 * (see DevAuthController) — 404s against the shared dev/prod deployments.
 */
export async function devLogin(): Promise<AuthTokenResponse> {
  const res = await apiClient.post<AuthTokenResponse>("/auth/dev-login", undefined, { auth: false });
  setTokens({ accessToken: res.accessToken, refreshToken: res.refreshToken });
  return res;
}

/**
 * Public marketing feature: mints a real session for the pre-seeded demo account, skipping
 * auth entirely. Unlike {@link devLogin}, this succeeds in every environment where
 * `demo.enabled=true` (see DemoAuthController) — it's meant to be reachable from the landing
 * page's "Try Demo" button, not just local dev.
 */
export async function demoLogin(): Promise<AuthTokenResponse> {
  const res = await apiClient.post<AuthTokenResponse>("/auth/demo/login", undefined, { auth: false });
  setTokens({ accessToken: res.accessToken, refreshToken: res.refreshToken });
  return res;
}

/**
 * Revoke this device's refresh token server-side, then clear local tokens. Local tokens
 * are cleared even if the network call fails — the user's intent to end the session takes
 * precedence over the server round-trip.
 */
export async function logout(): Promise<void> {
  const refreshToken = getRefreshToken();
  try {
    if (refreshToken) {
      await apiClient.post<void>("/auth/logout", { refreshToken });
    }
  } finally {
    clearTokens();
  }
}
