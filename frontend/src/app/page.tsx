"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useClientAuthenticated } from "@/components/auth/AuthGuard";
import { Landing } from "@/components/landing/Landing";

/** Public marketing landing page. Visitors sign in via the header CTAs; the authenticated
 *  app lives under the (app) route group (AuthGuard bounces unauthenticated users to /login).
 *  A returning signed-in user landing on `/` is sent straight to `/dashboard` instead of
 *  being shown the marketing page again. */
export default function HomePage() {
  const router = useRouter();
  const authed = useClientAuthenticated();

  useEffect(() => {
    if (authed) {
      router.replace("/dashboard");
    }
  }, [authed, router]);

  if (authed) {
    return null;
  }

  return <Landing />;
}
