/**
 * Firebase client SDK initialization for the web dashboard.
 *
 * Firebase is used ONLY to obtain a verified ID token from a phone-OTP or Google sign-in
 * flow. That ID token is immediately exchanged for a SpendWise-issued JWT via
 * `/auth/otp/verify` or `/auth/google` (see `src/lib/authApi.ts`). Per CLAUDE.md's Auth
 * pattern, the Firebase ID token is NEVER used as the backend session credential — only
 * the SpendWise JWT is.
 *
 * Config comes from the `NEXT_PUBLIC_FIREBASE_*` env vars scaffolded in Epic 0
 * (`.env.local.example`).
 */

import { getApp, getApps, initializeApp, type FirebaseApp } from "firebase/app";
import { getAuth, type Auth } from "firebase/auth";

const firebaseConfig = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY,
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN,
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID,
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID,
};

export function firebaseApp(): FirebaseApp {
  return getApps().length ? getApp() : initializeApp(firebaseConfig);
}

export function firebaseAuth(): Auth {
  return getAuth(firebaseApp());
}
