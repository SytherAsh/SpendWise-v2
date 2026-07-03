import { PageHeader } from "@/components/shared/ui";
import { TransactionsBrowser } from "@/components/transactions/TransactionsBrowser";

export default function TransactionsPage() {
  return (
    <>
      <PageHeader title="Transactions" subtitle="Browse, filter, and re-categorize your transactions" />
      <TransactionsBrowser />
    </>
  );
}
