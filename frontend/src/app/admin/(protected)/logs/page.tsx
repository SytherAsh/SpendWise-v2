import { PageHeader } from "@/components/shared/ui";
import { LogsViewer } from "@/components/admin/LogsViewer";

export default function AdminLogsPage() {
  return (
    <>
      <PageHeader title="System logs" subtitle="Parser failures, sync errors, and other system events" />
      <LogsViewer />
    </>
  );
}
