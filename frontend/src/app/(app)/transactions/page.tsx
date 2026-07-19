"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Search } from "lucide-react";
import { PageHeader } from "@/components/shared/ui";
import { MonthStepper } from "@/components/shared/MonthStepper";
import { CategorySummaryGrid, type CategorySelection } from "@/components/transactions/CategorySummaryGrid";
import { TransactionsHeaderStats } from "@/components/transactions/TransactionsHeaderStats";
import { TransactionsBrowser } from "@/components/transactions/TransactionsBrowser";
import { Input } from "@/components/ui/input";

/** Reads `?category=` for deep links from other pages (e.g. Planning's per-category drill-through). */
function parseInitialCategory(raw: string | null): CategorySelection {
  if (!raw) return null;
  if (raw === "uncategorized") return "uncategorized";
  if (raw === "received") return "received";
  const id = Number(raw);
  return Number.isInteger(id) && id > 0 ? id : null;
}

function TransactionsPageContent() {
  const searchParams = useSearchParams();
  const [categoryFilter, setCategoryFilter] = useState<CategorySelection>(() =>
    parseInitialCategory(searchParams.get("category")),
  );
  const [searchInput, setSearchInput] = useState("");
  const [search, setSearch] = useState("");

  // Debounce the search box before it reaches the backend query — avoids a request per keystroke.
  useEffect(() => {
    const handle = setTimeout(() => setSearch(searchInput.trim()), 300);
    return () => clearTimeout(handle);
  }, [searchInput]);

  return (
    <>
      <PageHeader
        title="Transactions"
        subtitle="See where your money went, then drill into any category"
        center={<MonthStepper />}
        action={<TransactionsHeaderStats />}
      />
      <div className="space-y-6">
        <CategorySummaryGrid selected={categoryFilter} onSelect={setCategoryFilter} />
        <div className="relative max-w-sm">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-foreground-subtle" />
          <Input
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Search payee, UPI id, note, or category…"
            className="pl-9"
            aria-label="Search transactions"
          />
        </div>
        <TransactionsBrowser
          categoryFilter={categoryFilter}
          onClearFilter={() => setCategoryFilter(null)}
          search={search}
        />
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
