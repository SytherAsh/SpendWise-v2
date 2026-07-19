import { PageHeader } from "@/components/shared/ui";
import { JobSchedulesPanel } from "@/components/admin/JobSchedulesPanel";

export default function AdminSchedulesPage() {
  return (
    <>
      <PageHeader title="Job schedules" subtitle="Adjust how often each background job runs — changes take effect immediately" />
      <JobSchedulesPanel />
    </>
  );
}
