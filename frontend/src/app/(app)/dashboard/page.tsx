import { PageHeader } from "@/components/shared/ui";
import { DashboardView } from "@/components/dashboard/DashboardView";

export default function DashboardPage() {
  return (
    <>
      <PageHeader title="Dashboard" subtitle="Your spending at a glance" />
      <DashboardView />
    </>
  );
}
