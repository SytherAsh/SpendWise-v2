"use client";

import { Card } from "@/components/shared/ui";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";

function ComingSoon() {
  return <Badge tone="neutral">Coming soon</Badge>;
}

/**
 * Placeholder security settings — not wired to any endpoint yet (2026-07-09, UI/UX polish
 * phase). SpendWise auth is Firebase phone OTP + Google (CLAUDE.md "Auth pattern"), so there's
 * no password to manage; this sketches the login-methods/2FA/sessions shape a fintech app's
 * security tab typically has, ready to wire up when prioritized.
 */
export function SecurityTab() {
  return (
    <div className="max-w-xl space-y-6">
      <Card>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-foreground-muted">Login methods</h2>
          <ComingSoon />
        </div>
        <p className="text-sm text-foreground-muted">
          Manage how you sign in — phone OTP and Google are currently supported. Adding or removing a login method
          from here isn&apos;t available yet.
        </p>
      </Card>

      <Card>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-foreground-muted">Two-factor authentication</h2>
          <ComingSoon />
        </div>
        <div className="flex items-center justify-between">
          <p className="text-sm text-foreground-muted">Require a second step when signing in on a new device.</p>
          <button
            type="button"
            role="switch"
            aria-checked={false}
            disabled
            className="relative h-6 w-11 shrink-0 rounded-full bg-surface-muted disabled:cursor-not-allowed disabled:opacity-50"
          >
            <span className="absolute left-0.5 top-0.5 size-5 rounded-full bg-surface shadow-[var(--shadow-sm)]" />
          </button>
        </div>
      </Card>

      <Card>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-foreground-muted">Active sessions</h2>
          <ComingSoon />
        </div>
        <div className="flex items-center justify-between text-sm">
          <div>
            <p className="font-medium text-foreground">This device</p>
            <p className="text-foreground-subtle">Current session</p>
          </div>
        </div>
        <Button variant="secondary" size="sm" disabled className="mt-3">
          Sign out of all other devices
        </Button>
      </Card>
    </div>
  );
}
