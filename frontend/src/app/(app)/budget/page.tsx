import { PageHeader } from "@/components/shared/ui";
import { BudgetManager } from "@/components/budget/BudgetManager";

export default function BudgetPage() {
  return (
    <>
      <PageHeader title="Budget" subtitle="Set monthly limits per category and track your progress" />
      <BudgetManager />
    </>
  );
}
