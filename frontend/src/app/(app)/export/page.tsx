import { PageHeader } from "@/components/shared/ui";
import { ExportForm } from "@/components/export/ExportForm";

export default function ExportPage() {
  return (
    <>
      <PageHeader title="Export" subtitle="Download a PDF report or CSV of your transactions" />
      <ExportForm />
    </>
  );
}
