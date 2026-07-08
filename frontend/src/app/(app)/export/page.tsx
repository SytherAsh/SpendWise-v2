import { redirect } from "next/navigation";

/** Export is now an action within Settings (IA redesign). */
export default function ExportPage() {
  redirect("/settings");
}
