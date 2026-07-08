"use client";

import * as React from "react";
import { cn } from "@/lib/cn";

export const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  ({ className, type = "text", ...props }, ref) => (
    <input
      ref={ref}
      type={type}
      className={cn(
        "h-10 w-full rounded-[var(--radius-sm)] border border-border-strong bg-surface px-3 text-sm text-foreground transition-shadow",
        "placeholder:text-foreground-subtle focus-visible:border-brand-500 focus-visible:outline-none",
        "focus-visible:shadow-[0_0_0_3px_color-mix(in_oklab,var(--color-brand-400)_25%,transparent)]",
        "disabled:cursor-not-allowed disabled:opacity-50",
        className,
      )}
      {...props}
    />
  ),
);
Input.displayName = "Input";

export const Select = React.forwardRef<
  HTMLSelectElement,
  React.SelectHTMLAttributes<HTMLSelectElement>
>(({ className, ...props }, ref) => (
  <select
    ref={ref}
    className={cn(
      "h-10 w-full rounded-[var(--radius-sm)] border border-border-strong bg-surface px-3 text-sm text-foreground transition-shadow",
      "focus-visible:border-brand-500 focus-visible:outline-none disabled:opacity-50",
      "focus-visible:shadow-[0_0_0_3px_color-mix(in_oklab,var(--color-brand-400)_25%,transparent)]",
      className,
    )}
    {...props}
  />
));
Select.displayName = "Select";
