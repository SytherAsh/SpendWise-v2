"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Wallet } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { devLogin, googleLogin, verifyOtp } from "@/lib/authApi";
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

  async function onDevLogin() {
    setError(null);
    setBusy(true);
    try {
      await devLogin();
      router.replace("/dashboard");
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="w-full max-w-sm rounded-[var(--radius-lg)] border border-border bg-surface p-8 shadow-[var(--shadow-lg)]">
      <div className="mb-6 flex items-center gap-2.5">
        <span className="flex size-9 items-center justify-center rounded-lg bg-brand-600 text-white">
          <Wallet className="size-5" />
        </span>
        <div>
          <h1 className="text-xl font-semibold tracking-tight text-foreground">SpendWise</h1>
          <p className="text-sm text-foreground-muted">Sign in to your dashboard</p>
        </div>
      </div>

      {error && (
        <p
          role="alert"
          className="mb-4 rounded-[var(--radius-sm)] border border-[var(--color-danger-border)] bg-[var(--color-danger-surface)] px-3 py-2 text-sm text-[var(--color-danger)]"
        >
          {error}
        </p>
      )}

      {!confirmation ? (
        <form onSubmit={onSendOtp} className="space-y-3">
          <label className="block text-sm font-medium text-foreground" htmlFor="phone">
            Phone number
          </label>
          <Input
            id="phone"
            name="phone"
            type="tel"
            autoComplete="tel"
            required
            placeholder="+91XXXXXXXXXX"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
          />
          <Button type="submit" disabled={busy || !phone} className="w-full">
            {busy ? "Sending…" : "Send OTP"}
          </Button>
        </form>
      ) : (
        <form onSubmit={onVerifyOtp} className="space-y-3">
          <label className="block text-sm font-medium text-foreground" htmlFor="otp">
            Enter OTP
          </label>
          <Input
            id="otp"
            name="otp"
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            required
            placeholder="6-digit code"
            value={code}
            onChange={(e) => setCode(e.target.value)}
          />
          <Button type="submit" disabled={busy || !code} className="w-full">
            {busy ? "Verifying…" : "Verify & sign in"}
          </Button>
        </form>
      )}

      <div className="my-5 flex items-center gap-3 text-xs text-foreground-subtle">
        <span className="h-px flex-1 bg-border" />
        or
        <span className="h-px flex-1 bg-border" />
      </div>

      <Button type="button" variant="secondary" onClick={onGoogle} disabled={busy} className="w-full">
        Continue with Google
      </Button>

      {/* next dev always sets NODE_ENV=development, next build always sets "production" — this
          can never render in a deployed build regardless of any env file. */}
      {process.env.NODE_ENV === "development" && (
        <Button
          type="button"
          variant="ghost"
          onClick={onDevLogin}
          disabled={busy}
          className="mt-2 w-full text-foreground-subtle"
        >
          Dev login (skip Firebase)
        </Button>
      )}

      {/* Firebase invisible reCAPTCHA mounts here for phone-OTP sign-in. */}
      <div id={RECAPTCHA_CONTAINER_ID} />
    </div>
  );
}
