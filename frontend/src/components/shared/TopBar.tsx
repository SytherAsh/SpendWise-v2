"use client";

import { Menu, Plus, Search } from "lucide-react";
import { DateRangePicker } from "@/components/shared/DateRangePicker";
import { Button } from "@/components/ui/button";
import { useShell } from "@/lib/shell";

export function TopBar() {
  const { setMobileNavOpen, setCommandOpen, setQuickAddOpen } = useShell();

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-border bg-surface/80 px-4 backdrop-blur md:px-6">
      <button
        type="button"
        onClick={() => setMobileNavOpen(true)}
        aria-label="Open navigation"
        className="rounded-[var(--radius-sm)] p-2 text-foreground-muted hover:bg-surface-muted lg:hidden"
      >
        <Menu className="size-5" />
      </button>

      <div className="ml-auto flex items-center gap-2">
        <button
          type="button"
          onClick={() => setCommandOpen(true)}
          className="hidden items-center gap-2 rounded-[var(--radius-sm)] border border-border-strong bg-surface px-3 py-2 text-sm text-foreground-subtle transition-colors hover:bg-surface-muted sm:flex"
        >
          <Search className="size-4" />
          <span>Search</span>
          <kbd className="ml-4 rounded border border-border bg-surface-muted px-1.5 py-0.5 font-sans text-[10px] font-medium text-foreground-muted">
            ⌘K
          </kbd>
        </button>

        <DateRangePicker />

        <Button size="md" onClick={() => setQuickAddOpen(true)} className="gap-1.5">
          <Plus className="size-4" />
          <span className="hidden sm:inline">Add</span>
        </Button>
      </div>
    </header>
  );
}
