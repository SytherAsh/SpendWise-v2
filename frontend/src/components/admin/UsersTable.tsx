"use client";

import Link from "next/link";
import { useAdminApi } from "@/lib/useAdminApi";
import { Card, EmptyState, ErrorState, Spinner } from "@/components/shared/ui";

interface AdminUserSummary {
  id: string;
  phone: string | null;
  email: string | null;
  createdAt: string;
  transactionCount: number;
  lastActivity: string | null;
}

export function UsersTable() {
  const { data, error, isLoading, refresh } = useAdminApi<AdminUserSummary[]>("/admin/users");

  if (isLoading && !data) return <Spinner />;
  if (error && !data) return <ErrorState message="Could not load users." onRetry={refresh} />;

  const users = data ?? [];
  if (users.length === 0) return <EmptyState message="No users yet." />;

  return (
    <Card className="overflow-x-auto p-0">
      <table className="w-full min-w-[640px] text-left text-sm">
        <thead className="border-b border-black/10 text-foreground-muted dark:border-white/10">
          <tr>
            <th className="px-4 py-3 font-medium">User</th>
            <th className="px-4 py-3 font-medium">Transactions</th>
            <th className="px-4 py-3 font-medium">Last activity</th>
            <th className="px-4 py-3 font-medium">Joined</th>
          </tr>
        </thead>
        <tbody>
          {users.map((u) => (
            <tr key={u.id} className="border-b border-black/5 last:border-0 dark:border-white/5">
              <td className="px-4 py-3">
                <Link href={`/admin/users/${u.id}`} className="text-brand-700 underline">
                  {u.phone ?? u.email ?? u.id}
                </Link>
              </td>
              <td className="px-4 py-3">{u.transactionCount}</td>
              <td className="px-4 py-3">{u.lastActivity ? new Date(u.lastActivity).toLocaleString() : "—"}</td>
              <td className="px-4 py-3">{new Date(u.createdAt).toLocaleDateString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Card>
  );
}
