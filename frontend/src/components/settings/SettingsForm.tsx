"use client";

import { useState } from "react";
import { useApi } from "@/lib/useApi";
import { apiClient } from "@/lib/apiClient";
import { Card, ErrorState, Spinner } from "@/components/shared/ui";

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

// The real hosted policy URL is set at deploy time; this is the documented placeholder.
export const PRIVACY_POLICY_URL = "https://spendwise.app/privacy";

function toggle(list: string[], value: string): string[] {
  return list.includes(value) ? list.filter((v) => v !== value) : [...list, value];
}

export function SettingsForm() {
  const { data, error, isLoading, refresh } = useApi<Preferences>("/users/me/preferences");
  const [draft, setDraft] = useState<Preferences | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  // Initialize the editable draft from the first successful load.
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
        <label className="flex items-center gap-3 py-1 text-sm">
          <input
            type="checkbox"
            checked={push}
            onChange={(e) => update({ alertChannels: { ...prefs.alertChannels, push: e.target.checked } })}
          />
          Push notifications
        </label>
        <label className="flex items-center gap-3 py-1 text-sm">
          <input
            type="checkbox"
            checked={email}
            onChange={(e) => update({ alertChannels: { ...prefs.alertChannels, email: e.target.checked } })}
          />
          Email alerts
        </label>
      </Card>

      <Card>
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-foreground-muted">Payment apps</h2>
        <div className="flex flex-wrap gap-3">
          {PAYMENT_APPS.map((app) => (
            <label key={app.value} className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={prefs.selectedApps.includes(app.value)}
                onChange={() => update({ selectedApps: toggle(prefs.selectedApps, app.value) })}
              />
              {app.label}
            </label>
          ))}
        </div>
      </Card>

      <Card>
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-foreground-muted">Banks</h2>
        <div className="flex flex-wrap gap-3">
          {BANKS.map((bank) => (
            <label key={bank.value} className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={prefs.selectedBanks.includes(bank.value)}
                onChange={() => update({ selectedBanks: toggle(prefs.selectedBanks, bank.value) })}
              />
              {bank.label}
            </label>
          ))}
        </div>
      </Card>

      <Card>
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-foreground-muted">Monthly spend estimate</h2>
        <div className="flex items-center gap-2">
          <span className="text-sm text-foreground-muted">₹</span>
          <input
            type="number"
            min={0}
            aria-label="Monthly spend estimate"
            value={prefs.monthlySpendEstimate ?? ""}
            onChange={(e) => update({ monthlySpendEstimate: e.target.value === "" ? null : Number(e.target.value) })}
            className="w-40 rounded-md border border-black/15 px-3 py-2 text-sm dark:border-white/15 dark:bg-neutral-800"
          />
        </div>
      </Card>

      {saveError && <ErrorState message={saveError} />}

      <div className="flex items-center gap-4">
        <button
          type="submit"
          disabled={saving}
          className="rounded-md bg-brand-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {saving ? "Saving…" : "Save preferences"}
        </button>
        {saved && (
          <span role="status" className="text-sm text-green-600 dark:text-green-400">
            Preferences saved.
          </span>
        )}
      </div>

      <p className="pt-2 text-sm">
        <a href={PRIVACY_POLICY_URL} target="_blank" rel="noreferrer" className="text-brand-700 underline">
          Privacy policy
        </a>
      </p>
    </form>
  );
}
