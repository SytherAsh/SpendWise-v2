"use client";

import { useEffect, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { isAuthenticated } from "@/lib/auth";

// The token lives in client storage, so its presence is a client-only value. Reading it
// via useSyncExternalStore (server snapshot = false, client snapshot = real value) avoids
// both a hydration mismatch and a setState-in-effect — the guard resolves to `false`
// during SSR/first paint and to the real value once mounted on the client.
const EMPTY_SUBSCRIBE = () => () => {};

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
    if (!authed) {
      router.replace("/login");
    }
  }, [authed, router]);

  if (!authed) {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-neutral-500">
        Loading…
      </div>
    );
  }

  return <>{children}</>;
}
