"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { logout } from "@/lib/authApi";

const NAV_ITEMS = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/transactions", label: "Transactions" },
  { href: "/budget", label: "Budget" },
  { href: "/emis", label: "EMIs & Subscriptions" },
  { href: "/chatbot", label: "Chatbot" },
  { href: "/export", label: "Export" },
  { href: "/settings", label: "Settings" },
];

export function AppNav() {
  const pathname = usePathname();
  const router = useRouter();

  async function onLogout() {
    await logout();
    router.replace("/login");
  }

  return (
    <nav className="flex w-56 shrink-0 flex-col border-r border-black/10 bg-neutral-50 p-4 dark:border-white/10 dark:bg-neutral-950">
      <div className="mb-6 px-2 text-lg font-semibold">SpendWise</div>
      <ul className="flex flex-1 flex-col gap-1">
        {NAV_ITEMS.map((item) => {
          const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
          return (
            <li key={item.href}>
              <Link
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={`block rounded-md px-3 py-2 text-sm ${
                  active
                    ? "bg-blue-600 text-white"
                    : "text-neutral-700 hover:bg-black/5 dark:text-neutral-300 dark:hover:bg-white/5"
                }`}
              >
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
      <button
        type="button"
        onClick={onLogout}
        className="mt-4 rounded-md border border-black/15 px-3 py-2 text-sm dark:border-white/15"
      >
        Log out
      </button>
    </nav>
  );
}
