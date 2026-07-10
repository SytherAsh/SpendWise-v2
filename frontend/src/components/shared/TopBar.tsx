"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Menu, Plus, Search, PlayCircle } from "lucide-react";
import { DateRangePicker } from "@/components/shared/DateRangePicker";
import { NotificationsBell } from "@/components/shared/NotificationsBell";
import { UserMenu } from "@/components/shared/UserMenu";
import { BrandMark } from "@/components/shared/BrandMark";
import { Button } from "@/components/ui/button";
import { useShell, PRIMARY_NAV } from "@/lib/shell";
import { useApi } from "@/lib/useApi";
import { DEMO_PHONE } from "@/lib/authApi";
import { cn } from "@/lib/cn";

interface Profile {
  phone: string;
}

/**
 * Primary top navigation: logo (→ landing) + primary destinations + global
 * search, date range, quick-add, notifications, and the user menu. Replaces the
 * former sidebar-primary shell (design-system § 7.1).
 */
export function TopBar() {
  const pathname = usePathname();
  const { setMobileNavOpen, setCommandOpen, setQuickAddOpen } = useShell();
  // Shares the SWR cache key with UserMenu's own `/users/me` fetch — no extra request.
  const { data: profile } = useApi<Profile>("/users/me");
  const isDemo = profile?.phone === DEMO_PHONE;

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center gap-2 border-b border-border bg-surface/80 px-3 backdrop-blur-xl md:px-6">
      <button
        type="button"
        onClick={() => setMobileNavOpen(true)}
        aria-label="Open navigation"
        className="rounded-[var(--radius-sm)] p-2 text-foreground-muted hover:bg-surface-muted lg:hidden"
      >
        <Menu className="size-5" />
      </button>

      <BrandMark />

      {isDemo && (
        <span className="inline-flex items-center gap-1.5 rounded-full border border-brand-400/40 bg-brand-50 px-2.5 py-1 text-xs font-medium text-brand-700 dark:bg-brand-400/10 dark:text-brand-300">
          <PlayCircle className="size-3.5" /> Demo account
        </span>
      )}

      {/* Primary nav */}
      <nav className="ml-4 hidden items-center gap-1 lg:flex">
        {PRIMARY_NAV.map((item) => {
          const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={active ? "page" : undefined}
              className={cn(
                "relative rounded-[var(--radius-sm)] px-3 py-2 text-sm font-medium transition-colors",
                active
                  ? "bg-brand-400/12 text-foreground shadow-[inset_0_-2px_0_0_var(--color-brand-500)]"
                  : "text-foreground-muted hover:bg-surface-muted hover:text-foreground",
              )}
            >
              {item.label}
            </Link>
          );
        })}
      </nav>

      <div className="ml-auto flex items-center gap-2">
        <button
          type="button"
          onClick={() => setCommandOpen(true)}
          className="hidden items-center gap-2 rounded-[var(--radius-sm)] border border-border-strong bg-surface px-3 py-2 text-sm text-foreground-subtle transition-colors hover:bg-surface-muted md:flex"
        >
          <Search className="size-4" />
          <span>Search</span>
          <kbd className="mono ml-3 rounded border border-border bg-surface-muted px-1.5 py-0.5 text-[10px] font-medium text-foreground-muted">
            ⌘K
          </kbd>
        </button>

        <button
          type="button"
          onClick={() => setCommandOpen(true)}
          aria-label="Search"
          className="grid size-9 place-items-center rounded-[var(--radius-sm)] border border-border text-foreground-muted hover:text-foreground md:hidden"
        >
          <Search className="size-4" />
        </button>

        <div className="hidden sm:block">
          <DateRangePicker />
        </div>

        <Button size="md" onClick={() => setQuickAddOpen(true)} className="gap-1.5">
          <Plus className="size-4" />
          <span className="hidden sm:inline">Add</span>
        </Button>

        <NotificationsBell />
        <UserMenu />
      </div>
    </header>
  );
}
