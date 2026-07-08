import { redirect } from "next/navigation";

/** The chatbot is now the floating assistant, available on every page from the launcher
 *  in the bottom-right. This route redirects to the dashboard. */
export default function ChatbotPage() {
  redirect("/dashboard");
}
