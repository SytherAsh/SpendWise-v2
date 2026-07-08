"use client";

import { useSyncExternalStore } from "react";
import { useTheme } from "next-themes";
import { Monitor, Moon, Sun } from "lucide-react";
import { cn } from "@/lib/cn";

const OPTIONS = [
  { value: "light", label: "Light", icon: Sun },
  { value: "dark", label: "Dark", icon: Moon },
  { value: "system", label: "System", icon: Monitor },
] as const;

// The selected theme is a client-only value (next-themes reads it from localStorage), so we
// resolve "mounted" via useSyncExternalStore — server snapshot false, client snapshot true —
// exactly like AuthGuard's client-only token check. This avoids both a hydration mismatch and
// a setState-in-effect (the project's lint rule forbids the latter).
const EMPTY_SUBSCRIBE = () => () => {};

/**
 * Light/Dark/System control, styled to match TabsList/TabsTrigger's segmented-pill look.
 * The active-state read is guarded by `mounted` — `theme` isn't known until after hydration,
 * so rendering it during SSR would mismatch. `setTheme` itself is safe to wire up unconditionally.
 */
export function ThemeToggle() {
  const { theme, setTheme } = useTheme();
  const mounted = useSyncExternalStore(EMPTY_SUBSCRIBE, () => true, () => false);

  return (
    <div className="inline-flex items-center gap-1 rounded-[var(--radius-sm)] border border-border bg-surface-muted p-1">
      {OPTIONS.map((opt) => {
        const Icon = opt.icon;
        const active = mounted && theme === opt.value;
        return (
          <button
            key={opt.value}
            type="button"
            onClick={() => setTheme(opt.value)}
            aria-pressed={active}
            className={cn(
              "inline-flex items-center gap-1.5 rounded-[calc(var(--radius-sm)-2px)] px-3 py-1.5 text-sm font-medium transition-colors",
              active
                ? "bg-surface text-foreground shadow-[var(--shadow-sm)]"
                : "text-foreground-muted hover:text-foreground",
            )}
          >
            <Icon className="size-4" />
            {opt.label}
          </button>
        );
      })}
    </div>
  );
}
