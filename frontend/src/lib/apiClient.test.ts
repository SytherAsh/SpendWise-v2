import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient, ApiError } from "@/lib/apiClient";
import { getAccessToken, getRefreshToken, setTokens } from "@/lib/auth";

/**
 * E10-S1-T2 required test: the refresh-on-401 interceptor.
 *
 * These exercise the apiClient against a mocked global `fetch`, asserting the
 * transparent single-retry refresh flow and its failure modes.
 */

const BASE = "http://backend.test";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("apiClient 401 refresh interceptor", () => {
  beforeEach(() => {
    process.env.NEXT_PUBLIC_API_BASE_URL = BASE;
    window.localStorage.clear();
    setTokens({ accessToken: "old-access", refreshToken: "refresh-1" });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    window.localStorage.clear();
  });

  it("refreshes the access token on a 401 and retries the original request once", async () => {
    const fetchMock = vi
      .fn()
      // 1st call: original request → 401
      .mockResolvedValueOnce(jsonResponse(401, { error: "UNAUTHORIZED", message: "expired" }))
      // 2nd call: token refresh → new tokens
      .mockResolvedValueOnce(
        jsonResponse(200, { accessToken: "new-access", refreshToken: "refresh-2", expiresIn: 604800 }),
      )
      // 3rd call: retried original request → success
      .mockResolvedValueOnce(jsonResponse(200, { ok: true }));
    vi.stubGlobal("fetch", fetchMock);

    const result = await apiClient.get<{ ok: boolean }>("/transactions");

    expect(result).toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledTimes(3);

    // The refresh call hit the right endpoint with the stored refresh token.
    const [refreshUrl, refreshInit] = fetchMock.mock.calls[1];
    expect(refreshUrl).toBe(`${BASE}/api/v1/auth/token/refresh`);
    expect(JSON.parse(refreshInit.body as string)).toEqual({ refreshToken: "refresh-1" });

    // The retry carried the NEW access token, and storage was rotated.
    const retryInit = fetchMock.mock.calls[2][1] as RequestInit;
    expect(new Headers(retryInit.headers).get("Authorization")).toBe("Bearer new-access");
    expect(getAccessToken()).toBe("new-access");
    expect(getRefreshToken()).toBe("refresh-2");
  });

  it("throws a 401 ApiError and clears tokens when the refresh itself fails", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(401, { error: "UNAUTHORIZED", message: "expired" }))
      .mockResolvedValueOnce(jsonResponse(401, { error: "INVALID_REFRESH", message: "revoked" }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(apiClient.get("/transactions")).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).toHaveBeenCalledTimes(2); // no retry after a failed refresh
    expect(getAccessToken()).toBeNull();
    expect(getRefreshToken()).toBeNull();
  });

  it("does not attempt a refresh for public (auth:false) requests", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(400, { error: "INVALID_OTP", message: "bad otp" }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(
      apiClient.post("/auth/otp/verify", { phone: "+910000000000", idToken: "x" }, { auth: false }),
    ).rejects.toMatchObject({ status: 400, code: "INVALID_OTP" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("coalesces concurrent 401s into a single refresh call", async () => {
    let refreshCalls = 0;
    // Data requests 401 while the old token is presented, then 200 once the new token is.
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.endsWith("/auth/token/refresh")) {
        refreshCalls += 1;
        return Promise.resolve(
          jsonResponse(200, { accessToken: "new-access", refreshToken: "refresh-2", expiresIn: 604800 }),
        );
      }
      const auth = new Headers(init?.headers).get("Authorization");
      return Promise.resolve(
        auth === "Bearer new-access"
          ? jsonResponse(200, { ok: true })
          : jsonResponse(401, { error: "UNAUTHORIZED", message: "expired" }),
      );
    });
    vi.stubGlobal("fetch", fetchMock);

    // Fire three requests concurrently; all should share one refresh and then succeed.
    const results = await Promise.all([
      apiClient.get<{ ok: boolean }>("/a"),
      apiClient.get<{ ok: boolean }>("/b"),
      apiClient.get<{ ok: boolean }>("/c"),
    ]);

    expect(results).toEqual([{ ok: true }, { ok: true }, { ok: true }]);
    expect(refreshCalls).toBe(1);
  });
});
