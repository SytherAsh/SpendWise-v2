import { PageHeader } from "@/components/shared/ui";
import { SettingsForm } from "@/components/settings/SettingsForm";
import { ExportForm } from "@/components/export/ExportForm";

export default function SettingsPage() {
  return (
    <>
      <PageHeader title="Settings" subtitle="Manage your alert channels, payment apps, and account" />
      <SettingsForm />

      <section className="mt-10" aria-labelledby="export-heading">
        <h2 id="export-heading" className="text-lg font-semibold tracking-tight text-foreground">
          Export data
        </h2>
        <p className="mb-4 mt-1 text-sm text-foreground-muted">
          Download a PDF report or CSV of your transactions.
        </p>
        <ExportForm />
      </section>
    </>
  );
}
