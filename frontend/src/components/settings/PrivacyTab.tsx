"use client";

import { Card } from "@/components/shared/ui";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";

// The real hosted policy URL is set at deploy time; this is the documented placeholder.
export const PRIVACY_POLICY_URL = "https://spendwise.app/privacy";

function ComingSoon() {
  return <Badge tone="neutral">Coming soon</Badge>;
}

/**
 * Placeholder privacy/data settings (2026-07-09, UI/UX polish phase) — the DPDP Act 2023 consent
 * itself is recorded once at onboarding (docs/database.md `user_consent`, docs/security.md); this
 * tab sketches the ongoing data-management controls a compliant fintech app typically surfaces
 * (export-my-data, delete-my-account) around that, not wired to any endpoint yet.
 */
export function PrivacyTab() {
  return (
    <div className="max-w-xl space-y-6">
      <Card>
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-foreground-muted">Your data</h2>
        <p className="text-sm text-foreground-muted">
          You consented to SpendWise processing your transaction data during onboarding, per the DPDP Act 2023. See
          the privacy policy below for what&apos;s collected and why.
        </p>
        <a
          href={PRIVACY_POLICY_URL}
          target="_blank"
          rel="noreferrer"
          className="mt-3 inline-block text-sm text-brand-700 underline"
        >
          Privacy policy
        </a>
      </Card>

      <Card>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-foreground-muted">Download your data</h2>
          <ComingSoon />
        </div>
        <p className="mb-3 text-sm text-foreground-muted">
          Get a copy of everything SpendWise has stored about you, beyond the transaction exports on the Export tab.
        </p>
        <Button variant="secondary" size="sm" disabled>
          Request data export
        </Button>
      </Card>

      <Card className="border-[var(--color-danger-border)]">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-[var(--color-danger)]">Delete account</h2>
          <ComingSoon />
        </div>
        <p className="mb-3 text-sm text-foreground-muted">
          Permanently delete your account and all associated data. This can&apos;t be undone.
        </p>
        <Button variant="danger" size="sm" disabled>
          Delete my account
        </Button>
      </Card>
    </div>
  );
}
