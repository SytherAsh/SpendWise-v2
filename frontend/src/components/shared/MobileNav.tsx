"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import {
  LayoutDashboard,
  ArrowLeftRight,
  ChartPie,
  Target,
  Settings,
  LogOut,
  type LucideIcon,
} from "lucide-react";
import { BrandMark } from "@/components/shared/BrandMark";
import { logout } from "@/lib/authApi";
import { NAV_ITEMS, useShell } from "@/lib/shell";
import { cn } from "@/lib/cn";

const ICONS: Record<string, LucideIcon> = {
  LayoutDashboard,
  ArrowLeftRight,
  ChartPie,
  Target,
  Settings,
};

/**
 * Mobile navigation drawer. On small screens the top nav hides its primary
 * links, so the hamburger opens this sheet with the full destination list
 * (including Settings) plus Log out. Desktop uses the top nav; there is no
 * persistent left sidebar.
 */
export function MobileNav() {
  const { mobileNavOpen, setMobileNavOpen } = useShell();
  const pathname = usePathname();
  const router = useRouter();

  async function onLogout() {
    setMobileNavOpen(false);
    await logout();
    router.replace("/login");
  }

  return (
    <DialogPrimitive.Root open={mobileNavOpen} onOpenChange={setMobileNavOpen}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm data-[state=open]:animate-in data-[state=open]:fade-in-0 lg:hidden" />
        <DialogPrimitive.Content
          className="fixed inset-y-0 left-0 z-50 flex w-72 flex-col border-r border-border bg-surface data-[state=open]:animate-in data-[state=open]:slide-in-from-left lg:hidden"
          aria-label="Navigation"
        >
          <DialogPrimitive.Title className="sr-only">Navigation</DialogPrimitive.Title>
          <div className="px-5 py-5">
            <BrandMark />
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
                      onClick={() => setMobileNavOpen(false)}
                      aria-current={active ? "page" : undefined}
                      className={cn(
                        "group flex items-center gap-3 rounded-[var(--radius-sm)] px-3 py-2.5 text-sm font-medium transition-colors",
                        active
                          ? "bg-brand-400/12 text-foreground shadow-[inset_2px_0_0_0_var(--color-brand-500)]"
                          : "text-foreground-muted hover:bg-surface-muted hover:text-foreground",
                      )}
                    >
                      {Icon && (
                        <Icon
                          className={cn(
                            "size-[18px] shrink-0",
                            active
                              ? "text-brand-600 dark:text-brand-400"
                              : "text-foreground-subtle group-hover:text-foreground",
                          )}
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
              className="flex w-full items-center gap-3 rounded-[var(--radius-sm)] px-3 py-2.5 text-sm font-medium text-foreground-muted transition-colors hover:bg-surface-muted hover:text-foreground"
            >
              <LogOut className="size-[18px] text-foreground-subtle" />
              Log out
            </button>
          </div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
