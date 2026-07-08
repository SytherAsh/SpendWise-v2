import { PageHeader } from "@/components/shared/ui";
import { AnalyticsView } from "@/components/analytics/AnalyticsView";

export default function AnalyticsPage() {
  return (
    <>
      <PageHeader title="Analytics" subtitle="Break down where your money goes and compare periods" />
      <AnalyticsView />
    </>
  );
}
