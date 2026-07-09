import { PageHeader } from "@/components/shared/ui";
import { DashboardView } from "@/components/dashboard/DashboardView";
import { MonthStepper } from "@/components/shared/MonthStepper";

export default function DashboardPage() {
  return (
    <>
      <PageHeader title="Dashboard" subtitle="Your spending at a glance" center={<MonthStepper />} />
      <DashboardView />
    </>
  );
}
