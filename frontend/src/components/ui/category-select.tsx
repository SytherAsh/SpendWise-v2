"use client";

import * as React from "react";
import * as SelectPrimitive from "@radix-ui/react-select";
import { Check, ChevronDown, ChevronUp } from "lucide-react";
import { cn } from "@/lib/cn";
import { CategoryChip } from "@/components/ui/category-chip";

export interface CategorySelectOption {
  id: number;
  name: string;
}

/**
 * Category picker whose trigger and each dropdown option render as a colored, rounded
 * <CategoryChip> — every category gets its own distinct pill rather than plain text, or a
 * flat color wash behind the whole row. Built on Radix Select (first use in this app) since
 * a native <select>'s <option> elements can't contain styled child markup at all, only a
 * background/text color on the whole row.
 *
 * Reusable across any category picker in the app (Transactions row, Budget, ...), not just
 * the one that originally asked for this.
 */
export function CategorySelect({
  categories,
  value,
  onChange,
  placeholder = "Uncategorized",
  ariaLabel,
  className,
  variant = "default",
}: {
  categories: CategorySelectOption[];
  value: number | "";
  onChange: (categoryId: number) => void;
  placeholder?: string;
  ariaLabel: string;
  className?: string;
  /** "action" is for a trigger that always shows its placeholder and exists to *start* a bulk
   * action (e.g. TransactionsBrowser's group-header "Change all…") rather than to display a
   * transaction's current category — the neutral `border-border-strong`/`text-foreground-subtle`
   * styling both share otherwise reads as inert display chrome for that case, camouflaging the
   * one control in a group header that actually does something. */
  variant?: "default" | "action";
}) {
  const selected = typeof value === "number" ? categories.find((c) => c.id === value) : undefined;

  return (
    <SelectPrimitive.Root
      value={typeof value === "number" ? String(value) : undefined}
      onValueChange={(v) => onChange(Number(v))}
    >
      <SelectPrimitive.Trigger
        aria-label={ariaLabel}
        className={cn(
          "inline-flex h-8 items-center justify-between gap-2 rounded-[var(--radius-sm)] border bg-surface px-2 text-sm transition-shadow",
          variant === "action"
            ? "border-brand-500/50 hover:border-brand-500 dark:border-brand-400/40 dark:hover:border-brand-400"
            : "border-border-strong",
          "focus-visible:border-brand-500 focus-visible:outline-none",
          "focus-visible:shadow-[0_0_0_3px_color-mix(in_oklab,var(--color-brand-400)_25%,transparent)]",
          "data-[placeholder]:text-foreground-subtle",
          className,
        )}
      >
        <span className="min-w-0 flex-1 overflow-hidden">
          {selected ? (
            <CategoryChip name={selected.name} id={selected.id} size="sm" />
          ) : (
            <span
              className={cn(
                "px-1",
                variant === "action" ? "font-medium text-brand-700 dark:text-brand-300" : "text-foreground-subtle",
              )}
            >
              {placeholder}
            </span>
          )}
        </span>
        <SelectPrimitive.Icon>
          <ChevronDown
            className={cn(
              "size-3.5 shrink-0",
              variant === "action" ? "text-brand-600 dark:text-brand-300" : "text-foreground-subtle",
            )}
          />
        </SelectPrimitive.Icon>
      </SelectPrimitive.Trigger>
      <SelectPrimitive.Portal>
        <SelectPrimitive.Content
          position="popper"
          sideOffset={4}
          className={cn(
            "z-50 w-[--radix-select-trigger-width] overflow-hidden rounded-[var(--radius-md)] border border-border bg-surface p-1 shadow-[var(--shadow-lg)]",
            "data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95",
            "data-[state=closed]:animate-out data-[state=closed]:fade-out-0",
          )}
        >
          <SelectPrimitive.ScrollUpButton className="flex items-center justify-center py-1 text-foreground-subtle">
            <ChevronUp className="size-3.5" />
          </SelectPrimitive.ScrollUpButton>
          <SelectPrimitive.Viewport className="max-h-72 space-y-0.5">
            {categories.map((c) => (
              <SelectPrimitive.Item
                key={c.id}
                value={String(c.id)}
                className={cn(
                  "flex cursor-pointer items-center justify-between gap-2 rounded-[var(--radius-sm)] px-1.5 py-1 outline-none",
                  "data-[highlighted]:bg-surface-muted",
                )}
              >
                <SelectPrimitive.ItemText>
                  <CategoryChip name={c.name} id={c.id} size="sm" />
                </SelectPrimitive.ItemText>
                <SelectPrimitive.ItemIndicator className="shrink-0 pr-1 text-brand-600 dark:text-brand-300">
                  <Check className="size-3.5" />
                </SelectPrimitive.ItemIndicator>
              </SelectPrimitive.Item>
            ))}
          </SelectPrimitive.Viewport>
          <SelectPrimitive.ScrollDownButton className="flex items-center justify-center py-1 text-foreground-subtle">
            <ChevronDown className="size-3.5" />
          </SelectPrimitive.ScrollDownButton>
        </SelectPrimitive.Content>
      </SelectPrimitive.Portal>
    </SelectPrimitive.Root>
  );
}
