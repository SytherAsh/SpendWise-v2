import { cn } from "@/lib/cn";

/** Shimmer placeholder for loading states. Respects reduced-motion via globals.css. */
export function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      aria-hidden
      className={cn("sw-skeleton rounded-[var(--radius-sm)] bg-surface-muted", className)}
      {...props}
    />
  );
}
