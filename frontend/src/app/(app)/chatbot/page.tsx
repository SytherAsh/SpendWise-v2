import { PageHeader } from "@/components/shared/ui";
import { ChatbotView } from "@/components/chatbot/ChatbotView";

export default function ChatbotPage() {
  return (
    <>
      <PageHeader title="Chatbot" subtitle="Ask questions about your spending" />
      <ChatbotView />
    </>
  );
}
