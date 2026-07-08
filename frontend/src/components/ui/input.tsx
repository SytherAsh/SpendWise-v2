"use client";

import * as React from "react";
import { cn } from "@/lib/cn";

export const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  ({ className, type = "text", ...props }, ref) => (
    <input
      ref={ref}
      type={type}
      className={cn(
        "h-10 w-full rounded-[var(--radius-sm)] border border-border-strong bg-surface px-3 text-sm text-foreground",
        "placeholder:text-foreground-subtle focus-visible:border-brand-600 focus-visible:outline-none",
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
      "h-10 w-full rounded-[var(--radius-sm)] border border-border-strong bg-surface px-3 text-sm text-foreground",
      "focus-visible:border-brand-600 focus-visible:outline-none disabled:opacity-50",
      className,
    )}
    {...props}
  />
));
Select.displayName = "Select";
