"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { clearAdminAccessToken } from "@/lib/adminAuth";

const NAV_ITEMS = [
  { href: "/admin/users", label: "Users" },
  { href: "/admin/logs", label: "Logs" },
  { href: "/admin/ml", label: "ML Accuracy" },
  { href: "/admin/ops", label: "Run Jobs Now" },
  { href: "/admin/schedules", label: "Job Schedules" },
];

export function AdminNav() {
  const pathname = usePathname();
  const router = useRouter();

  function onLogout() {
    clearAdminAccessToken();
    router.replace("/admin/login");
  }

  return (
    <nav className="flex w-56 shrink-0 flex-col border-r border-black/10 bg-neutral-50 p-4 dark:border-white/10 dark:bg-neutral-950">
      <div className="mb-6 px-2 text-lg font-semibold">SpendWise Admin</div>
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
                    ? "bg-brand-700 text-white"
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
