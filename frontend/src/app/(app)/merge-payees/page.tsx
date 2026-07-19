"use client";

import { PageHeader } from "@/components/shared/ui";
import { MergePayeesReview } from "@/components/merge-payees/MergePayeesReview";

export default function MergePayeesPage() {
  return (
    <>
      <PageHeader title="Merge Payees" subtitle="Confirm which payee names belong to the same person" />
      <MergePayeesReview />
    </>
  );
}
