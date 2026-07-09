"use client";

import { Card } from "@/components/shared/ui";
import { ThemeToggle } from "./ThemeToggle";

export function AppearanceTab() {
  return (
    <div className="max-w-xl space-y-6">
      <Card>
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-foreground-muted">Theme</h2>
        <p className="mb-3 text-sm text-foreground-muted">Choose how SpendWise looks on this device.</p>
        <ThemeToggle />
      </Card>
    </div>
  );
}
