import { Landing } from "@/components/landing/Landing";

/** Public marketing landing page. Visitors sign in via the header CTAs; the authenticated
 *  app lives under the (app) route group (AuthGuard bounces unauthenticated users to /login). */
export default function HomePage() {
  return <Landing />;
}
