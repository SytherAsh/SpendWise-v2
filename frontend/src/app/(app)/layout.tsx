import { AuthGuard } from "@/components/auth/AuthGuard";
import { AppShell } from "@/components/shared/AppShell";

/**
 * Layout for all authenticated dashboard routes. Wraps everything in AuthGuard (redirects
 * to /login if unauthenticated) and renders the persistent app shell — sidebar, top
 * context bar (date-range, ⌘K, quick-add), and the floating chat assistant.
 */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard>
      <AppShell>{children}</AppShell>
    </AuthGuard>
  );
}
