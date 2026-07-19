import { PageHeader } from "@/components/shared/ui";
import { ScheduledJobsPanel } from "@/components/admin/ScheduledJobsPanel";

export default function AdminOpsPage() {
  return (
    <>
      <PageHeader title="Scheduled jobs" subtitle="Run any background job on demand instead of waiting for its schedule" />
      <ScheduledJobsPanel />
    </>
  );
}
