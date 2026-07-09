"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTheme } from "next-themes";
import { User, Settings as SettingsIcon, LifeBuoy, Upload, LogOut, Moon, Sun } from "lucide-react";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { useApi } from "@/lib/useApi";
import { useShell } from "@/lib/shell";
import { logout } from "@/lib/authApi";
import { cn } from "@/lib/cn";

interface Profile {
  id: string;
  phone: string;
  email: string | null;
  createdAt: string;
}

function initials(profile: Profile | undefined): string {
  if (!profile) return "SW";
  if (profile.email) return profile.email.slice(0, 2).toUpperCase();
  const digits = profile.phone?.replace(/\D/g, "") ?? "";
  return digits.slice(-2) || "SW";
}

const itemClass =
  "flex w-full items-center gap-2.5 rounded-[var(--radius-sm)] px-2.5 py-2 text-sm text-foreground transition-colors hover:bg-surface-muted";

export function UserMenu() {
  const router = useRouter();
  const { setAssistantOpen, setUploadOpen } = useShell();
  const { data } = useApi<Profile>("/users/me");
  const [open, setOpen] = React.useState(false);

  const identity = data?.email ?? data?.phone ?? "Signed in";

  async function onLogout() {
    setOpen(false);
    await logout();
    router.replace("/login");
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        aria-label="Account menu"
        className="grid size-9 place-items-center rounded-full bg-[image:var(--gradient-brand-vivid)] text-[13px] font-semibold text-[#04170d] shadow-[var(--glow-brand-sm)] transition-transform hover:scale-105 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--ring)]"
      >
        <span className="mono">{initials(data)}</span>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-64 p-2">
        <div className="flex items-center gap-2.5 px-2.5 py-2">
          <span className="grid size-9 shrink-0 place-items-center rounded-full bg-[image:var(--gradient-brand-vivid)] text-[13px] font-semibold text-[#04170d]">
            <span className="mono">{initials(data)}</span>
          </span>
          <div className="min-w-0">
            <p className="truncate text-sm font-medium text-foreground">{identity}</p>
            <p className="text-xs text-foreground-subtle">SpendWise account</p>
          </div>
        </div>

        <div className="my-1.5 h-px bg-border" />

        <Link href="/profile" onClick={() => setOpen(false)} className={itemClass}>
          <User className="size-4 text-foreground-subtle" /> Profile
        </Link>
        <Link href="/settings" onClick={() => setOpen(false)} className={itemClass}>
          <SettingsIcon className="size-4 text-foreground-subtle" /> Settings
        </Link>
        <button
          type="button"
          onClick={() => {
            setOpen(false);
            setAssistantOpen(true);
          }}
          className={itemClass}
        >
          <LifeBuoy className="size-4 text-foreground-subtle" /> Help &amp; assistant
        </button>
        <button
          type="button"
          onClick={() => {
            setOpen(false);
            setUploadOpen(true);
          }}
          className={itemClass}
        >
          <Upload className="size-4 text-foreground-subtle" /> Upload statement
        </button>

        <div className="my-1.5 h-px bg-border" />

        <ThemeControl />

        <div className="my-1.5 h-px bg-border" />

        <button
          type="button"
          onClick={onLogout}
          className={cn(itemClass, "text-[var(--color-danger)] hover:bg-[var(--color-danger-surface)]")}
        >
          <LogOut className="size-4" /> Log out
        </button>
      </PopoverContent>
    </Popover>
  );
}

/** Inline light/dark segmented control inside the menu. */
function ThemeControl() {
  const { theme, setTheme, resolvedTheme } = useTheme();
  const [mounted, setMounted] = React.useState(false);
  React.useEffect(() => setMounted(true), []);
  const current = mounted ? (theme === "system" ? resolvedTheme : theme) : undefined;

  const opts = [
    { value: "light", label: "Light", icon: Sun },
    { value: "dark", label: "Dark", icon: Moon },
  ] as const;

  return (
    <div className="px-1.5 py-1">
      <p className="px-1 pb-1.5 text-[11px] font-semibold uppercase tracking-[0.05em] text-foreground-subtle">
        Theme
      </p>
      <div className="grid grid-cols-2 gap-1 rounded-[var(--radius-sm)] bg-surface-muted p-1">
        {opts.map((o) => {
          const active = current === o.value;
          return (
            <button
              key={o.value}
              type="button"
              onClick={() => setTheme(o.value)}
              aria-pressed={active}
              className={cn(
                "flex items-center justify-center gap-1.5 rounded-[calc(var(--radius-sm)-2px)] px-2 py-1.5 text-xs font-medium transition-colors",
                active
                  ? "bg-surface text-foreground shadow-[var(--shadow-sm)]"
                  : "text-foreground-muted hover:text-foreground",
              )}
            >
              <o.icon className="size-3.5" /> {o.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}
