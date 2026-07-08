"use client";

import { Suspense, useState } from "react";
import { useSearchParams } from "next/navigation";
import { PageHeader } from "@/components/shared/ui";
import { CategorySummaryGrid, type CategorySelection } from "@/components/transactions/CategorySummaryGrid";
import { TransactionsBrowser } from "@/components/transactions/TransactionsBrowser";

/** Reads `?category=` for deep links from other pages (e.g. Planning's per-category drill-through). */
function parseInitialCategory(raw: string | null): CategorySelection {
  if (!raw) return null;
  if (raw === "uncategorized") return "uncategorized";
  const id = Number(raw);
  return Number.isInteger(id) && id > 0 ? id : null;
}

function TransactionsPageContent() {
  const searchParams = useSearchParams();
  const [categoryFilter, setCategoryFilter] = useState<CategorySelection>(() =>
    parseInitialCategory(searchParams.get("category")),
  );

  return (
    <>
      <PageHeader title="Transactions" subtitle="See where your money went, then drill into any category" />
      <div className="space-y-6">
        <CategorySummaryGrid selected={categoryFilter} onSelect={setCategoryFilter} />
        <TransactionsBrowser categoryFilter={categoryFilter} onClearFilter={() => setCategoryFilter(null)} />
      </div>
    </>
  );
}

export default function TransactionsPage() {
  return (
    <Suspense fallback={null}>
      <TransactionsPageContent />
    </Suspense>
  );
}
