"use client";

import { useState } from "react";
import { PageHeader } from "@/components/shared/ui";
import { CategorySummaryGrid, type CategorySelection } from "@/components/transactions/CategorySummaryGrid";
import { TransactionsBrowser } from "@/components/transactions/TransactionsBrowser";

export default function TransactionsPage() {
  const [categoryFilter, setCategoryFilter] = useState<CategorySelection>(null);

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
