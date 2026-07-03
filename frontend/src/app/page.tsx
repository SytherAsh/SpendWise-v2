import { redirect } from "next/navigation";

export default function HomePage() {
  // The dashboard route is behind AuthGuard, which bounces unauthenticated users to
  // /login — so a single redirect here handles both the signed-in and signed-out cases.
  redirect("/dashboard");
}
