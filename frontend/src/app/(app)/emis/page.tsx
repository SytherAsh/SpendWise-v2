import { redirect } from "next/navigation";

/** EMIs & Subscriptions were merged into Planning in the IA redesign. */
export default function EmisPage() {
  redirect("/planning");
}
