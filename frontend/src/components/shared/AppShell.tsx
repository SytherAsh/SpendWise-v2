"use client";

import type { ReactNode } from "react";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { Sidebar } from "@/components/shared/Sidebar";
import { TopBar } from "@/components/shared/TopBar";
import { CommandPalette } from "@/components/shared/CommandPalette";
import { QuickAddTransaction } from "@/components/shared/QuickAddTransaction";
import { ChatAssistant } from "@/components/chatbot/ChatAssistant";
import { DateRangeProvider } from "@/lib/date-range";
import { ShellProvider, useShell } from "@/lib/shell";

function MobileNav() {
  const { mobileNavOpen, setMobileNavOpen } = useShell();
  return (
    <DialogPrimitive.Root open={mobileNavOpen} onOpenChange={setMobileNavOpen}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm data-[state=open]:animate-in data-[state=open]:fade-in-0 lg:hidden" />
        <DialogPrimitive.Content
          className="fixed inset-y-0 left-0 z-50 data-[state=open]:animate-in data-[state=open]:slide-in-from-left lg:hidden"
          aria-label="Navigation"
        >
          <DialogPrimitive.Title className="sr-only">Navigation</DialogPrimitive.Title>
          <Sidebar onNavigate={() => setMobileNavOpen(false)} />
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}

function Shell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen bg-background">
      <aside className="hidden lg:block">
        <Sidebar />
      </aside>
      <MobileNav />
      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar />
        <main className="flex-1 px-4 py-6 md:px-8 md:py-8">{children}</main>
      </div>
      <CommandPalette />
      <QuickAddTransaction />
      <ChatAssistant />
    </div>
  );
}

/** Authenticated app shell: sidebar + top bar + global overlays, with date-range and
 *  shell state providers scoped to the app routes. */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    <DateRangeProvider>
      <ShellProvider>
        <Shell>{children}</Shell>
      </ShellProvider>
    </DateRangeProvider>
  );
}
