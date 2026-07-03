import { PageHeader } from "@/components/shared/ui";
import { EmiManager } from "@/components/emis/EmiManager";

export default function EmisPage() {
  return (
    <>
      <PageHeader title="EMIs & Subscriptions" subtitle="Track recurring payments and cancel what you no longer use" />
      <EmiManager />
    </>
  );
}
