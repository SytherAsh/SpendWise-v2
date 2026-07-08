import { categoryColor } from "@/lib/categories";
import { cn } from "@/lib/cn";

/**
 * The canonical way to render a category. Always pairs the category color with its
 * icon + name — this icon+label pairing is the mandatory secondary encoding that makes
 * the 12-color palette accessible (color is never the sole identifier). Prefer this over
 * a bare colored dot or a plain <Badge> for anything category-related.
 */
export function CategoryChip({
  name,
  id,
  icon,
  size = "md",
  className,
}: {
  name: string;
  id?: number;
  icon?: string;
  size?: "sm" | "md";
  className?: string;
}) {
  const color = categoryColor(name, id);
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border font-medium",
        size === "sm" ? "px-2 py-0.5 text-xs" : "px-2.5 py-1 text-sm",
        className,
      )}
      style={{
        color,
        borderColor: `color-mix(in srgb, ${color} 35%, transparent)`,
        backgroundColor: `color-mix(in srgb, ${color} 10%, transparent)`,
      }}
    >
      {icon ? (
        <span aria-hidden>{icon}</span>
      ) : (
        <span
          aria-hidden
          className="size-2 rounded-full"
          style={{ backgroundColor: color }}
        />
      )}
      <span className="text-foreground">{name}</span>
    </span>
  );
}
