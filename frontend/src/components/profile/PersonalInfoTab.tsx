"use client";

import { useState } from "react";
import { apiClient } from "@/lib/apiClient";
import { useApi } from "@/lib/useApi";
import { formatDate } from "@/lib/format";
import { Card, ErrorState, Spinner } from "@/components/shared/ui";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

/** Matches UserProfileResponse (backend). */
interface Profile {
  id: string;
  phone: string;
  email: string | null;
  createdAt: string;
}

export function PersonalInfoTab() {
  const { data, error, isLoading, refresh } = useApi<Profile>("/users/me");
  const [emailDraft, setEmailDraft] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  if (isLoading && !data) return <Spinner />;
  if (error && !data) return <ErrorState message="Could not load your profile." onRetry={refresh} />;
  if (!data) return <Spinner />;

  const email = emailDraft ?? data.email ?? "";

  async function onSave(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setSaveError(null);
    setSaved(false);
    try {
      await apiClient.put("/users/me", { email });
      setSaved(true);
      refresh();
    } catch {
      setSaveError("Could not save your email. Please try again.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="max-w-xl space-y-6">
      <Card>
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-foreground-muted">Account</h2>
        <dl className="space-y-3 text-sm">
          <div>
            <dt className="text-foreground-subtle">Phone number</dt>
            <dd className="font-medium text-foreground">{data.phone}</dd>
          </div>
          <div>
            <dt className="text-foreground-subtle">Member since</dt>
            <dd className="font-medium text-foreground">{formatDate(data.createdAt)}</dd>
          </div>
        </dl>
      </Card>

      <Card>
        <form onSubmit={onSave} className="space-y-3">
          <label className="block text-sm">
            <span className="mb-1.5 block font-semibold uppercase tracking-wide text-foreground-muted">Email</span>
            <Input
              type="email"
              aria-label="Email"
              value={email}
              onChange={(e) => {
                setEmailDraft(e.target.value);
                setSaved(false);
              }}
            />
          </label>
          {saveError && <ErrorState message={saveError} />}
          <div className="flex items-center gap-4">
            <Button type="submit" disabled={saving}>
              {saving ? "Saving…" : "Save email"}
            </Button>
            {saved && (
              <span role="status" className="text-sm text-brand-700">
                Email saved.
              </span>
            )}
          </div>
        </form>
      </Card>
    </div>
  );
}
