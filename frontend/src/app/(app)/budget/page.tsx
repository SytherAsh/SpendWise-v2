import { redirect } from "next/navigation";

/** Budget was merged into Planning in the IA redesign. */
export default function BudgetPage() {
  redirect("/planning");
}
