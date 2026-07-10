"use client";

import { useSyncExternalStore } from "react";
import { useTheme } from "next-themes";
import { Moon, Sun } from "lucide-react";
import { cn } from "@/lib/cn";

// The resolved theme is a client-only value (next-themes reads it from localStorage), so
// "mounted" is resolved via useSyncExternalStore — server snapshot false, client snapshot
// true — exactly like AuthGuard's client-only token check. Avoids both a hydration mismatch
// and a setState-in-effect (the project's lint rule forbids the latter).
const EMPTY_SUBSCRIBE = () => () => {};

/**
 * Light/dark toggle. Uses next-themes (data-theme, system default, persisted).
 * Renders a stable placeholder until mounted to avoid hydration mismatch.
 */
export function ThemeToggle({ className }: { className?: string }) {
  const { resolvedTheme, setTheme } = useTheme();
  const mounted = useSyncExternalStore(EMPTY_SUBSCRIBE, () => true, () => false);

  const isDark = resolvedTheme === "dark";

  return (
    <button
      type="button"
      aria-label={mounted ? `Switch to ${isDark ? "light" : "dark"} theme` : "Toggle theme"}
      onClick={() => setTheme(isDark ? "light" : "dark")}
      className={cn(
        "inline-grid size-9 place-items-center rounded-[var(--radius-sm)] border border-border text-foreground-muted",
        "transition-colors hover:border-border-strong hover:text-foreground",
        className,
      )}
    >
      {mounted && isDark ? <Sun className="size-4" /> : <Moon className="size-4" />}
    </button>
  );
}
