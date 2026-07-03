/**
 * Thin wrappers around the Firebase client SDK sign-in flows. Each returns a Firebase ID
 * token, which the caller then exchanges for a SpendWise JWT via `authApi.ts`.
 *
 * This layer is deliberately small so `LoginForm` can be component-tested by mocking this
 * whole module (no real Firebase project / reCAPTCHA needed in tests — consistent with the
 * rest of the repo, where Firebase Auth has never been exercised against a real project;
 * see STATUS.md Epic 5 close-out).
 */

import {
  GoogleAuthProvider,
  RecaptchaVerifier,
  signInWithPhoneNumber,
  signInWithPopup,
  type ConfirmationResult,
} from "firebase/auth";
import { firebaseAuth } from "@/lib/firebase";

export type { ConfirmationResult };

/**
 * Begin phone-OTP sign-in. Renders an invisible reCAPTCHA bound to `recaptchaContainerId`
 * and sends the SMS code. The returned ConfirmationResult is passed to `confirmOtp`.
 */
export async function startPhoneSignIn(
  phone: string,
  recaptchaContainerId: string,
): Promise<ConfirmationResult> {
  const auth = firebaseAuth();
  const verifier = new RecaptchaVerifier(auth, recaptchaContainerId, {
    size: "invisible",
  });
  return signInWithPhoneNumber(auth, phone, verifier);
}

/** Confirm the SMS code and return the resulting Firebase ID token. */
export async function confirmOtp(confirmation: ConfirmationResult, code: string): Promise<string> {
  const credential = await confirmation.confirm(code);
  return credential.user.getIdToken();
}

/** Google popup sign-in; returns the resulting Firebase ID token. */
export async function signInWithGoogle(): Promise<string> {
  const auth = firebaseAuth();
  const credential = await signInWithPopup(auth, new GoogleAuthProvider());
  return credential.user.getIdToken();
}
