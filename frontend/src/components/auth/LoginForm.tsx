"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { googleLogin, verifyOtp } from "@/lib/authApi";
import {
  confirmOtp,
  signInWithGoogle,
  startPhoneSignIn,
  type ConfirmationResult,
} from "@/lib/firebaseLogin";

const RECAPTCHA_CONTAINER_ID = "recaptcha-container";

function errorMessage(err: unknown): string {
  if (err instanceof Error && err.message) return err.message;
  return "Login failed. Please try again.";
}

/**
 * SpendWise web login (E10-S1-T1). Phone-OTP and Google flows both obtain a Firebase ID
 * token, exchange it for a SpendWise JWT (`/auth/otp/verify` or `/auth/google`), store the
 * SpendWise access + refresh token, and redirect to the dashboard. The Firebase ID token is
 * never used as the backend session credential (CLAUDE.md Auth pattern).
 */
export function LoginForm() {
  const router = useRouter();
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [confirmation, setConfirmation] = useState<ConfirmationResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSendOtp(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const result = await startPhoneSignIn(phone, RECAPTCHA_CONTAINER_ID);
      setConfirmation(result);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function onVerifyOtp(e: React.FormEvent) {
    e.preventDefault();
    if (!confirmation) return;
    setError(null);
    setBusy(true);
    try {
      const idToken = await confirmOtp(confirmation, code);
      await verifyOtp(phone, idToken);
      router.replace("/dashboard");
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function onGoogle() {
    setError(null);
    setBusy(true);
    try {
      const idToken = await signInWithGoogle();
      await googleLogin(idToken);
      router.replace("/dashboard");
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="w-full max-w-sm rounded-xl border border-black/10 bg-white p-8 shadow-sm dark:border-white/10 dark:bg-neutral-900">
      <h1 className="mb-1 text-2xl font-semibold">SpendWise</h1>
      <p className="mb-6 text-sm text-neutral-500">Sign in to your dashboard</p>

      {error && (
        <p role="alert" className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
          {error}
        </p>
      )}

      {!confirmation ? (
        <form onSubmit={onSendOtp} className="space-y-3">
          <label className="block text-sm font-medium" htmlFor="phone">
            Phone number
          </label>
          <input
            id="phone"
            name="phone"
            type="tel"
            autoComplete="tel"
            required
            placeholder="+91XXXXXXXXXX"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            className="w-full rounded-md border border-black/15 px-3 py-2 text-sm dark:border-white/15 dark:bg-neutral-800"
          />
          <button
            type="submit"
            disabled={busy || !phone}
            className="w-full rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
          >
            {busy ? "Sending…" : "Send OTP"}
          </button>
        </form>
      ) : (
        <form onSubmit={onVerifyOtp} className="space-y-3">
          <label className="block text-sm font-medium" htmlFor="otp">
            Enter OTP
          </label>
          <input
            id="otp"
            name="otp"
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            required
            placeholder="6-digit code"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            className="w-full rounded-md border border-black/15 px-3 py-2 text-sm dark:border-white/15 dark:bg-neutral-800"
          />
          <button
            type="submit"
            disabled={busy || !code}
            className="w-full rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
          >
            {busy ? "Verifying…" : "Verify & sign in"}
          </button>
        </form>
      )}

      <div className="my-5 flex items-center gap-3 text-xs text-neutral-400">
        <span className="h-px flex-1 bg-black/10 dark:bg-white/10" />
        or
        <span className="h-px flex-1 bg-black/10 dark:bg-white/10" />
      </div>

      <button
        type="button"
        onClick={onGoogle}
        disabled={busy}
        className="w-full rounded-md border border-black/15 px-3 py-2 text-sm font-medium disabled:opacity-50 dark:border-white/15"
      >
        Continue with Google
      </button>

      {/* Firebase invisible reCAPTCHA mounts here for phone-OTP sign-in. */}
      <div id={RECAPTCHA_CONTAINER_ID} />
    </div>
  );
}
