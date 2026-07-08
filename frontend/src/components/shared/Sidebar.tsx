"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  LayoutDashboard,
  ArrowLeftRight,
  ChartPie,
  Target,
  Settings,
  LogOut,
  Wallet,
  type LucideIcon,
} from "lucide-react";
import { logout } from "@/lib/authApi";
import { NAV_ITEMS } from "@/lib/shell";
import { cn } from "@/lib/cn";

const ICONS: Record<string, LucideIcon> = {
  LayoutDashboard,
  ArrowLeftRight,
  ChartPie,
  Target,
  Settings,
};

export function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  const router = useRouter();

  async function onLogout() {
    await logout();
    router.replace("/login");
  }

  return (
    <div className="flex h-full w-60 shrink-0 flex-col border-r border-border bg-surface">
      <div className="flex items-center gap-2 px-5 py-5">
        <span className="flex size-8 items-center justify-center rounded-lg bg-brand-600 text-white">
          <Wallet className="size-5" />
        </span>
        <span className="text-lg font-semibold tracking-tight">SpendWise</span>
      </div>

      <nav className="flex-1 px-3">
        <ul className="flex flex-col gap-1">
          {NAV_ITEMS.map((item) => {
            const Icon = ICONS[item.icon];
            const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
            return (
              <li key={item.href}>
                <Link
                  href={item.href}
                  onClick={onNavigate}
                  aria-current={active ? "page" : undefined}
                  className={cn(
                    "group flex items-center gap-3 rounded-[var(--radius-sm)] px-3 py-2 text-sm font-medium transition-colors",
                    active
                      ? "bg-brand-50 text-brand-800"
                      : "text-foreground-muted hover:bg-surface-muted hover:text-foreground",
                  )}
                >
                  {Icon && (
                    <Icon
                      className={cn("size-[18px] shrink-0", active ? "text-brand-700" : "text-foreground-subtle group-hover:text-foreground")}
                    />
                  )}
                  {item.label}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>

      <div className="border-t border-border p-3">
        <button
          type="button"
          onClick={onLogout}
          className="flex w-full items-center gap-3 rounded-[var(--radius-sm)] px-3 py-2 text-sm font-medium text-foreground-muted transition-colors hover:bg-surface-muted hover:text-foreground"
        >
          <LogOut className="size-[18px] text-foreground-subtle" />
          Log out
        </button>
      </div>
    </div>
  );
}
