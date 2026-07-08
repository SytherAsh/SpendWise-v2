"use client";

import type { ReactNode } from "react";
import { TopBar } from "@/components/shared/TopBar";
import { MobileNav } from "@/components/shared/MobileNav";
import { Breadcrumbs } from "@/components/shared/Breadcrumbs";
import { CommandPalette } from "@/components/shared/CommandPalette";
import { QuickAddTransaction } from "@/components/shared/QuickAddTransaction";
import { ChatAssistant } from "@/components/chatbot/ChatAssistant";
import { DateRangeProvider } from "@/lib/date-range";
import { ShellProvider } from "@/lib/shell";

function Shell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen bg-background">
      <TopBar />
      <MobileNav />
      <main className="mx-auto max-w-[1600px] px-4 py-6 md:px-8 md:py-8">
        <Breadcrumbs />
        {children}
      </main>
      <CommandPalette />
      <QuickAddTransaction />
      <ChatAssistant />
    </div>
  );
}

/** Authenticated app shell: top nav + global overlays, with date-range and
 *  shell state providers scoped to the app routes. Navigation is top-nav on
 *  desktop and the MobileNav drawer on small screens — there is no left sidebar. */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    <DateRangeProvider>
      <ShellProvider>
        <Shell>{children}</Shell>
      </ShellProvider>
    </DateRangeProvider>
  );
}
