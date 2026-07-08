"use client";

import { MessageSquare } from "lucide-react";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet";
import { ChatbotView } from "@/components/chatbot/ChatbotView";
import { useShell } from "@/lib/shell";

/**
 * Secondary AI assistant — a floating launcher (bottom-right, by convention) that opens
 * a slide-over panel over any page. Kept out of primary nav so it never dominates.
 */
export function ChatAssistant() {
  const { assistantOpen, setAssistantOpen } = useShell();

  return (
    <Sheet open={assistantOpen} onOpenChange={setAssistantOpen}>
      {!assistantOpen && (
        <button
          type="button"
          onClick={() => setAssistantOpen(true)}
          aria-label="Open the spending assistant"
          className="fixed bottom-5 right-5 z-40 flex size-14 items-center justify-center rounded-full bg-brand-700 text-white shadow-[var(--shadow-lg)] transition-transform hover:scale-105 hover:bg-brand-800 focus-visible:outline-none"
        >
          <MessageSquare className="size-6" />
        </button>
      )}
      <SheetContent side="right" className="max-w-lg">
        <SheetHeader>
          <SheetTitle>Spending assistant</SheetTitle>
          <SheetDescription>Ask questions about your transactions and spending.</SheetDescription>
        </SheetHeader>
        <div className="min-h-0 flex-1 overflow-y-auto p-4">
          <ChatbotView />
        </div>
      </SheetContent>
    </Sheet>
  );
}
