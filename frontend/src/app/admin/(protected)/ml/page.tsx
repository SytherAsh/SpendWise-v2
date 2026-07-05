import { PageHeader } from "@/components/shared/ui";
import { MlAccuracyPanel } from "@/components/admin/MlAccuracyPanel";

export default function AdminMlPage() {
  return (
    <>
      <PageHeader title="ML accuracy" subtitle="Categorization model accuracy metrics and manual retrain trigger" />
      <MlAccuracyPanel />
    </>
  );
}
