import { AuthGuard } from "@/components/auth/AuthGuard";
import { AppNav } from "@/components/shared/AppNav";

/**
 * Layout for all authenticated dashboard routes. Wraps everything in AuthGuard (redirects
 * to /login if unauthenticated) and renders the persistent side nav alongside the page.
 */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard>
      <div className="flex min-h-screen">
        <AppNav />
        <main className="flex-1 overflow-x-auto p-6 md:p-8">{children}</main>
      </div>
    </AuthGuard>
  );
}
