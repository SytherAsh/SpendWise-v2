"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { adminApiClient } from "@/lib/adminApiClient";
import { setAdminAccessToken } from "@/lib/adminAuth";

function errorMessage(err: unknown): string {
  if (err instanceof Error && err.message) return err.message;
  return "Login failed. Please try again.";
}

/** Admin Portal login (E11-S1-T1 / E11-S3-T1) — a seeded username/password, never a regular user account. */
export function AdminLoginForm() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const res = await adminApiClient.post<{ accessToken: string }>(
        "/admin/auth/login",
        { username, password },
        { auth: false },
      );
      setAdminAccessToken(res.accessToken);
      router.replace("/admin/users");
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="w-full max-w-sm rounded-xl border border-black/10 bg-white p-8 shadow-sm dark:border-white/10 dark:bg-neutral-900">
      <h1 className="mb-1 text-2xl font-semibold">SpendWise Admin</h1>
      <p className="mb-6 text-sm text-foreground-muted">Operator sign-in — separate from any user account</p>

      {error && (
        <p role="alert" className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
          {error}
        </p>
      )}

      <form onSubmit={onSubmit} className="space-y-3">
        <label className="block text-sm font-medium" htmlFor="admin-username">
          Username
        </label>
        <input
          id="admin-username"
          name="username"
          type="text"
          autoComplete="username"
          required
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          className="w-full rounded-md border border-black/15 px-3 py-2 text-sm dark:border-white/15 dark:bg-neutral-800"
        />
        <label className="block text-sm font-medium" htmlFor="admin-password">
          Password
        </label>
        <input
          id="admin-password"
          name="password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="w-full rounded-md border border-black/15 px-3 py-2 text-sm dark:border-white/15 dark:bg-neutral-800"
        />
        <button
          type="submit"
          disabled={busy || !username || !password}
          className="w-full rounded-md bg-brand-700 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {busy ? "Signing in…" : "Sign in"}
        </button>
      </form>
    </div>
  );
}
