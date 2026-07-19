"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { adminApiClient } from "@/lib/adminApiClient";
import { useAdminApi } from "@/lib/useAdminApi";
import { formatCurrency } from "@/lib/format";
import { Card, EmptyState, ErrorState, Spinner } from "@/components/shared/ui";
import { DeleteUserDialog } from "@/components/admin/DeleteUserDialog";

interface AdminTransaction {
  id: string;
  transactionDate: string;
  amount: number;
  recipientName: string | null;
  recipientCanonical: string | null;
  categoryId: number | null;
}

interface AdminBudget {
  id: string;
  categoryId: number;
  monthlyLimit: number;
}

interface AdminAlert {
  id: string;
  type: string;
  priority: string;
  triggeredAt: string;
  isRead: boolean;
}

interface AdminUserDetail {
  id: string;
  phone: string | null;
  email: string | null;
  createdAt: string;
  transactions: AdminTransaction[];
  budgets: AdminBudget[];
  alerts: AdminAlert[];
}

export function UserDetailView({ userId }: { userId: string }) {
  const router = useRouter();
  const { data, error, isLoading, refresh } = useAdminApi<AdminUserDetail>(`/admin/users/${userId}`);
  const [deleting, setDeleting] = useState(false);
  const [busy, setBusy] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  if (isLoading && !data) return <Spinner />;
  if (error && !data) return <ErrorState message="Could not load this user." onRetry={refresh} />;
  if (!data) return null;

  const identifier = data.phone ?? data.email ?? data.id;

  async function onConfirmDelete() {
    setBusy(true);
    setDeleteError(null);
    try {
      await adminApiClient.delete(`/admin/users/${userId}`);
      router.replace("/admin/users");
    } catch {
      setDeleteError("Could not delete this user. Please try again.");
      setBusy(false);
    }
  }

  return (
    <div className="max-w-3xl space-y-6">
      <Card>
        <h2 className="mb-1 text-lg font-semibold">{identifier}</h2>
        <p className="text-sm text-foreground-muted">Joined {new Date(data.createdAt).toLocaleDateString()}</p>
      </Card>

      <Card>
        <h3 className="mb-3 text-sm font-semibold">Transactions</h3>
        {data.transactions.length === 0 ? (
          <EmptyState message="No transactions." />
        ) : (
          <ul className="space-y-2 text-sm">
            {data.transactions.map((t) => (
              <li key={t.id} className="flex justify-between border-b border-black/5 pb-2 last:border-0 dark:border-white/5">
                <span>{t.recipientCanonical ?? t.recipientName ?? "—"}</span>
                <span>{formatCurrency(t.amount)}</span>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card>
        <h3 className="mb-3 text-sm font-semibold">Budgets</h3>
        {data.budgets.length === 0 ? (
          <EmptyState message="No budgets set." />
        ) : (
          <ul className="space-y-2 text-sm">
            {data.budgets.map((b) => (
              <li key={b.id} className="flex justify-between border-b border-black/5 pb-2 last:border-0 dark:border-white/5">
                <span>Category {b.categoryId}</span>
                <span>{formatCurrency(b.monthlyLimit)}</span>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card>
        <h3 className="mb-3 text-sm font-semibold">Alerts</h3>
        {data.alerts.length === 0 ? (
          <EmptyState message="No alerts." />
        ) : (
          <ul className="space-y-2 text-sm">
            {data.alerts.map((a) => (
              <li key={a.id} className="flex justify-between border-b border-black/5 pb-2 last:border-0 dark:border-white/5">
                <span>{a.type}</span>
                <span>{a.priority}</span>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card>
        {!deleting ? (
          <button
            type="button"
            onClick={() => setDeleting(true)}
            className="rounded-md border border-red-300 px-3 py-2 text-sm font-medium text-red-700 dark:border-red-900 dark:text-red-300"
          >
            Delete user…
          </button>
        ) : (
          <>
            {deleteError && <p role="alert" className="mb-2 text-sm text-red-600">{deleteError}</p>}
            <DeleteUserDialog
              identifier={identifier}
              busy={busy}
              onConfirm={onConfirmDelete}
              onCancel={() => setDeleting(false)}
            />
          </>
        )}
      </Card>
    </div>
  );
}
