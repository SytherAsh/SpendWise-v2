import { PageHeader } from "@/components/shared/ui";
import { SettingsForm } from "@/components/settings/SettingsForm";

export default function SettingsPage() {
  return (
    <>
      <PageHeader title="Settings" subtitle="Manage your alert channels, payment apps, and account" />
      <SettingsForm />
    </>
  );
}
