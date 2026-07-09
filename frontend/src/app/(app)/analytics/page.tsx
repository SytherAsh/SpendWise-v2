import { PageHeader } from "@/components/shared/ui";
import { AnalyticsView } from "@/components/analytics/AnalyticsView";
import { TransactionsHeaderStats } from "@/components/transactions/TransactionsHeaderStats";

export default function AnalyticsPage() {
  return (
    <>
      <PageHeader
        title="Analytics"
        subtitle="Dive into trends for each spending category"
        action={<TransactionsHeaderStats />}
      />
      <AnalyticsView />
    </>
  );
}
