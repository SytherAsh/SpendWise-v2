"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ChevronRight } from "lucide-react";
import { NAV_ITEMS } from "@/lib/shell";

const LABELS: Record<string, string> = Object.fromEntries(
  NAV_ITEMS.map((i) => [i.href.replace("/", ""), i.label]),
);

function labelFor(segment: string): string {
  return LABELS[segment] ?? segment.replace(/-/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

/**
 * Contextual breadcrumbs — rendered only on nested pages (2+ path segments),
 * e.g. Transactions › Detail. Top-level pages show nothing. See design-system § 7.3.
 */
export function Breadcrumbs() {
  const pathname = usePathname();
  const segments = pathname.split("/").filter(Boolean);
  if (segments.length < 2) return null;

  return (
    <nav aria-label="Breadcrumb" className="mb-4">
      <ol className="flex items-center gap-1.5 text-xs text-foreground-subtle">
        {segments.map((seg, i) => {
          const href = "/" + segments.slice(0, i + 1).join("/");
          const isLast = i === segments.length - 1;
          return (
            <li key={href} className="flex items-center gap-1.5">
              {i > 0 && <ChevronRight className="size-3.5 text-foreground-subtle/60" aria-hidden />}
              {isLast ? (
                <span className="font-medium text-foreground" aria-current="page">
                  {labelFor(seg)}
                </span>
              ) : (
                <Link href={href} className="transition-colors hover:text-foreground">
                  {labelFor(seg)}
                </Link>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
