"use client";

import { useEffect, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { isAdminAuthenticated } from "@/lib/adminAuth";

const EMPTY_SUBSCRIBE = () => () => {};

function useClientAdminAuthenticated(): boolean {
  return useSyncExternalStore(EMPTY_SUBSCRIBE, () => isAdminAuthenticated(), () => false);
}

/** Admin Portal counterpart to `components/auth/AuthGuard.tsx` — gates on the admin token, not the user token. */
export function AdminAuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const authed = useClientAdminAuthenticated();

  useEffect(() => {
    if (!authed) {
      router.replace("/admin/login");
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
