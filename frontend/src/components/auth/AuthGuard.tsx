"use client";

import { useEffect, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { isAuthenticated } from "@/lib/auth";

// The token lives in client storage, so its presence is a client-only value. Reading it
// via useSyncExternalStore (server snapshot = false, client snapshot = real value) avoids
// both a hydration mismatch and a setState-in-effect — the guard resolves to `false`
// during SSR/first paint and to the real value once mounted on the client.
const EMPTY_SUBSCRIBE = () => () => {};

// TEMP DEV-ONLY BYPASS: set NEXT_PUBLIC_DISABLE_AUTH=true in .env.local to skip the
// redirect-to-login below while OTP login is broken locally. Remove once login works
// again — this does not bypass backend JWT checks, only the client-side route guard.
const DEV_BYPASS_AUTH = process.env.NEXT_PUBLIC_DISABLE_AUTH === "true";

function useClientAuthenticated(): boolean {
  return useSyncExternalStore(
    EMPTY_SUBSCRIBE,
    () => isAuthenticated(),
    () => false,
  );
}

/**
 * Client-side route guard (E10-S1-T2). Protected routes render inside this. An
 * unauthenticated direct navigation to any protected route is redirected to `/login`;
 * until the client-side check resolves, a lightweight loading state is shown so protected
 * content never flashes.
 */
export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const authed = useClientAuthenticated();

  useEffect(() => {
    if (!authed && !DEV_BYPASS_AUTH) {
      router.replace("/login");
    }
  }, [authed, router]);

  if (!authed && !DEV_BYPASS_AUTH) {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-neutral-500">
        Loading…
      </div>
    );
  }

  return <>{children}</>;
}
