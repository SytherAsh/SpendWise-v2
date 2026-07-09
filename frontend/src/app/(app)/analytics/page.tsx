import { PageHeader } from "@/components/shared/ui";
import { AnalyticsView } from "@/components/analytics/AnalyticsView";
import { MonthStepper } from "@/components/analytics/MonthStepper";

export default function AnalyticsPage() {
  return (
    <>
      <PageHeader
        title="Analytics"
        subtitle="Dive into trends for each spending category"
        action={<MonthStepper />}
      />
      <AnalyticsView />
    </>
  );
}
