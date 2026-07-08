import Link from "next/link";
import { cn } from "@/lib/cn";

interface BrandMarkProps {
  /** Pixel size of the square mark. */
  size?: number;
  /** Render the "SpendWise" wordmark beside the mark. */
  withWordmark?: boolean;
  /** Add the signature emerald glow behind the mark. */
  glow?: boolean;
  /** Wrap in a link. Per the design system, the logo always → landing page. */
  href?: string | null;
  className?: string;
}

/**
 * SpendWise brand mark — an ascending "growth" stroke (reads as a rising chart)
 * on a vibrant emerald gradient tile. Used in nav, footer, splash, avatar
 * fallback. See docs/design-system.md § 1.1.
 */
export function BrandMark({
  size = 32,
  withWordmark = true,
  glow = false,
  href = "/",
  className,
}: BrandMarkProps) {
  const mark = (
    <span className={cn("inline-flex items-center gap-2.5", className)}>
      <span
        aria-hidden
        className={cn(
          "relative grid shrink-0 place-items-center rounded-[0.5rem]",
          "bg-[image:var(--gradient-brand-vivid)]",
          glow && "shadow-[var(--glow-brand-md)]",
        )}
        style={{ width: size, height: size }}
      >
        <svg
          width={size * 0.56}
          height={size * 0.56}
          viewBox="0 0 24 24"
          fill="none"
          stroke="#04170d"
          strokeWidth={2.6}
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M4 16.5 L9.5 11 L13 14 L20 6" />
          <path d="M15 6 L20 6 L20 11" />
        </svg>
      </span>
      {withWordmark && (
        <span className="font-display text-lg font-semibold tracking-tight text-foreground">
          Spend<span className="text-brand-700 dark:text-brand-400">Wise</span>
        </span>
      )}
    </span>
  );

  if (href) {
    return (
      <Link href={href} aria-label="SpendWise home" className="inline-flex items-center">
        {mark}
      </Link>
    );
  }
  return mark;
}
