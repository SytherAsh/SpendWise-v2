"use client";

import { useState } from "react";
import { useApi } from "@/lib/useApi";
import { apiClient } from "@/lib/apiClient";
import { Card, ErrorState, Spinner } from "@/components/shared/ui";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/cn";

/** Matches UserPreferencesResponse (backend). */
interface Preferences {
  alertChannels: Record<string, boolean>;
  selectedApps: string[];
  selectedBanks: string[];
  monthlySpendEstimate: number | null;
}

const PAYMENT_APPS = [
  { value: "paytm", label: "Paytm" },
  { value: "gpay", label: "Google Pay" },
  { value: "phonepe", label: "PhonePe" },
];

const BANKS = [
  { value: "sbi", label: "SBI" },
  { value: "hdfc", label: "HDFC" },
  { value: "icici", label: "ICICI" },
  { value: "axis", label: "Axis" },
  { value: "kotak", label: "Kotak" },
];

function toggle(list: string[], value: string): string[] {
  return list.includes(value) ? list.filter((v) => v !== value) : [...list, value];
}

function ToggleChip({ selected, onClick, children }: { selected: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={selected}
      className={cn(
        "rounded-full border px-3.5 py-1.5 text-sm font-medium transition-colors",
        selected
          ? "border-brand-600 bg-brand-50 text-brand-800"
          : "border-border text-foreground-muted hover:border-border-strong hover:text-foreground",
      )}
    >
      {children}
    </button>
  );
}

export function PreferencesTab() {
  const { data, error, isLoading, refresh } = useApi<Preferences>("/users/me/preferences");
  const [draft, setDraft] = useState<Preferences | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const prefs = draft ?? data ?? null;

  if (isLoading && !data) return <Spinner />;
  if (error && !data) return <ErrorState message="Could not load preferences." onRetry={refresh} />;
  if (!prefs) return <Spinner />;

  function update(patch: Partial<Preferences>) {
    setDraft({ ...(prefs as Preferences), ...patch });
    setSaved(false);
  }

  async function onSave(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setSaveError(null);
    setSaved(false);
    try {
      const updated = await apiClient.put<Preferences>("/users/me/preferences", {
        alertChannels: prefs!.alertChannels,
        selectedApps: prefs!.selectedApps,
        selectedBanks: prefs!.selectedBanks,
        monthlySpendEstimate: prefs!.monthlySpendEstimate,
      });
      setDraft(updated);
      setSaved(true);
    } catch {
      setSaveError("Could not save preferences. Please try again.");
    } finally {
      setSaving(false);
    }
  }

  const push = prefs.alertChannels?.push ?? false;
  const email = prefs.alertChannels?.email ?? false;

  return (
    <form onSubmit={onSave} className="max-w-xl space-y-6">
      <Card>
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-foreground-muted">Alert channels</h2>
        <div className="flex flex-wrap gap-2">
          <ToggleChip selected={push} onClick={() => update({ alertChannels: { ...prefs.alertChannels, push: !push } })}>
            Push notifications
          </ToggleChip>
          <ToggleChip selected={email} onClick={() => update({ alertChannels: { ...prefs.alertChannels, email: !email } })}>
            Email alerts
          </ToggleChip>
        </div>
      </Card>

      <Card>
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-foreground-muted">Payment apps</h2>
        <div className="flex flex-wrap gap-2">
          {PAYMENT_APPS.map((app) => (
            <ToggleChip
              key={app.value}
              selected={prefs.selectedApps.includes(app.value)}
              onClick={() => update({ selectedApps: toggle(prefs.selectedApps, app.value) })}
            >
              {app.label}
            </ToggleChip>
          ))}
        </div>
      </Card>

      <Card>
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-foreground-muted">Banks</h2>
        <div className="flex flex-wrap gap-2">
          {BANKS.map((bank) => (
            <ToggleChip
              key={bank.value}
              selected={prefs.selectedBanks.includes(bank.value)}
              onClick={() => update({ selectedBanks: toggle(prefs.selectedBanks, bank.value) })}
            >
              {bank.label}
            </ToggleChip>
          ))}
        </div>
      </Card>

      <Card>
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-foreground-muted">Monthly spend estimate</h2>
        <label className="flex items-center gap-2">
          <span className="text-sm text-foreground-muted">₹</span>
          <Input
            type="number"
            min={0}
            aria-label="Monthly spend estimate"
            value={prefs.monthlySpendEstimate ?? ""}
            onChange={(e) => update({ monthlySpendEstimate: e.target.value === "" ? null : Number(e.target.value) })}
            className="w-40"
          />
        </label>
      </Card>

      {saveError && <ErrorState message={saveError} />}

      <div className="flex items-center gap-4">
        <Button type="submit" disabled={saving}>
          {saving ? "Saving…" : "Save preferences"}
        </Button>
        {saved && (
          <span role="status" className="text-sm text-brand-700">
            Preferences saved.
          </span>
        )}
      </div>
    </form>
  );
}
