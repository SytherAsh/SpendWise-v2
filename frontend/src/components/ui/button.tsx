"use client";

import * as React from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/cn";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-[var(--radius-sm)] text-sm font-medium transition-all focus-visible:outline-none disabled:pointer-events-none disabled:opacity-50 [&_svg]:size-4 [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        // brand-700 keeps white label text ≥4.5:1 (WCAG AA) on light; dark uses the
        // vivid gradient with near-black ink. Signature glow on hover.
        primary:
          "bg-brand-700 text-white hover:bg-brand-800 active:bg-brand-900 hover:shadow-[var(--glow-brand-sm)] dark:bg-[image:var(--gradient-brand-vivid)] dark:text-[#04170d] dark:hover:brightness-110",
        secondary:
          "border border-border-strong bg-surface text-foreground hover:border-brand-500 hover:bg-surface-muted",
        ghost: "text-foreground-muted hover:bg-surface-muted hover:text-foreground",
        danger: "bg-[var(--color-danger)] text-white hover:brightness-95",
        link: "text-brand-700 underline-offset-4 hover:underline dark:text-brand-300",
      },
      size: {
        sm: "h-8 px-3 text-xs",
        md: "h-10 px-4",
        lg: "h-11 px-6 text-base",
        icon: "size-10",
      },
    },
    defaultVariants: { variant: "primary", size: "md" },
  },
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, type, ...props }, ref) => {
    const Comp = asChild ? Slot : "button";
    return (
      <Comp
        ref={ref}
        type={asChild ? undefined : (type ?? "button")}
        className={cn(buttonVariants({ variant, size }), className)}
        {...props}
      />
    );
  },
);
Button.displayName = "Button";

export { buttonVariants };
