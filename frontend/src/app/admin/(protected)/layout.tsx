import { AdminAuthGuard } from "@/components/admin/AdminAuthGuard";
import { AdminNav } from "@/components/admin/AdminNav";

/** Layout for every authenticated Admin Portal route — gated by the admin token, distinct from the user AuthGuard (E11-S3-T1). */
export default function AdminProtectedLayout({ children }: { children: React.ReactNode }) {
  return (
    <AdminAuthGuard>
      <div className="flex min-h-screen">
        <AdminNav />
        <main className="flex-1 overflow-x-auto p-6 md:p-8">{children}</main>
      </div>
    </AdminAuthGuard>
  );
}
