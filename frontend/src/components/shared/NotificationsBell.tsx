"use client";

import * as React from "react";
import Link from "next/link";
import { Bell, AlertTriangle, AlertCircle, Info, type LucideIcon } from "lucide-react";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { useApi } from "@/lib/useApi";
import { cn } from "@/lib/cn";

interface Alert {
  id: string;
  type: string;
  priority: string;
  isRead: boolean;
  payload: Record<string, unknown>;
}
interface AlertListResponse {
  data: Alert[];
  nextCursor: string | null;
  hasMore: boolean;
}

const TONE: Record<string, { icon: LucideIcon; accent: string }> = {
  high: { icon: AlertTriangle, accent: "text-[var(--color-danger)]" },
  medium: { icon: AlertCircle, accent: "text-[var(--color-warning)]" },
  low: { icon: Info, accent: "text-foreground-subtle" },
};

function humanize(type: string): string {
  return type.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

export function NotificationsBell() {
  const [open, setOpen] = React.useState(false);
  const { data, error, isLoading } = useApi<AlertListResponse>("/alerts?limit=20");

  const alerts = data?.data ?? [];
  const unread = alerts.filter((a) => !a.isRead).length;

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        aria-label={unread > 0 ? `Notifications (${unread} unread)` : "Notifications"}
        className="relative grid size-9 place-items-center rounded-[var(--radius-sm)] border border-border text-foreground-muted transition-colors hover:border-border-strong hover:text-foreground"
      >
        <Bell className="size-4" />
        {unread > 0 && (
          <span className="absolute -right-1 -top-1 grid min-w-[18px] place-items-center rounded-full bg-[var(--color-danger)] px-1 text-[10px] font-semibold leading-[18px] text-white ring-2 ring-[var(--background)]">
            <span className="mono">{unread > 9 ? "9+" : unread}</span>
          </span>
        )}
      </PopoverTrigger>
      <PopoverContent align="end" className="w-80 p-0">
        <div className="flex items-center justify-between border-b border-border px-4 py-3">
          <p className="text-sm font-semibold text-foreground">Notifications</p>
          {unread > 0 && (
            <span className="mono rounded-full bg-brand-50 px-2 py-0.5 text-xs font-semibold text-brand-700 dark:bg-brand-400/15 dark:text-brand-300">
              {unread} new
            </span>
          )}
        </div>

        <div className="max-h-[22rem] overflow-y-auto">
          {isLoading && !data ? (
            <div className="space-y-2 p-3">
              {[0, 1, 2].map((i) => (
                <div key={i} className="h-12 animate-pulse rounded-[var(--radius-sm)] bg-surface-muted" />
              ))}
            </div>
          ) : error && !data ? (
            <p className="px-4 py-8 text-center text-sm text-foreground-subtle">Could not load notifications.</p>
          ) : alerts.length === 0 ? (
            <div className="px-4 py-10 text-center">
              <Bell className="mx-auto size-6 text-foreground-subtle" />
              <p className="mt-2 text-sm text-foreground-muted">You&apos;re all caught up.</p>
            </div>
          ) : (
            <ul className="p-1.5">
              {alerts.map((a) => {
                const tone = TONE[a.priority] ?? TONE.low;
                const Icon = tone.icon;
                const message = typeof a.payload?.message === "string" ? a.payload.message : null;
                return (
                  <li key={a.id}>
                    <div
                      className={cn(
                        "flex items-start gap-3 rounded-[var(--radius-sm)] px-2.5 py-2.5",
                        !a.isRead && "bg-surface-muted/60",
                      )}
                    >
                      <Icon className={cn("mt-0.5 size-4 shrink-0", tone.accent)} aria-hidden />
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-medium text-foreground">{humanize(a.type)}</p>
                        {message && <p className="mt-0.5 text-xs text-foreground-muted">{message}</p>}
                      </div>
                      {!a.isRead && <span className="mt-1.5 size-2 shrink-0 rounded-full bg-brand-500" aria-label="Unread" />}
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        <div className="border-t border-border p-1.5">
          <Link
            href="/dashboard"
            onClick={() => setOpen(false)}
            className="block rounded-[var(--radius-sm)] px-2.5 py-2 text-center text-sm font-medium text-brand-700 transition-colors hover:bg-surface-muted dark:text-brand-300"
          >
            View all on dashboard
          </Link>
        </div>
      </PopoverContent>
    </Popover>
  );
}
