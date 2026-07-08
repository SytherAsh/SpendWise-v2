"use client";

import { useState } from "react";
import { Repeat } from "lucide-react";
import { apiClient } from "@/lib/apiClient";
import { useApi } from "@/lib/useApi";
import { formatCurrency } from "@/lib/format";
import { Card, EmptyState, ErrorState, Spinner } from "@/components/shared/ui";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";

interface Emi {
  id: string;
  label: string;
  amount: number;
  dueDay: number | null;
  detectedFromSms: boolean;
  isActive: boolean;
  sourceTransactionId: string | null;
}

interface EmiFields {
  label: string;
  amount: string;
  dueDay: string;
}

const EMPTY: EmiFields = { label: "", amount: "", dueDay: "" };

function toBody(fields: EmiFields) {
  return {
    label: fields.label.trim(),
    amount: Number(fields.amount),
    dueDay: fields.dueDay === "" ? null : Number(fields.dueDay),
  };
}

function validate(fields: EmiFields): string | null {
  if (!fields.label.trim()) return "Enter a label.";
  const amount = Number(fields.amount);
  if (!fields.amount || Number.isNaN(amount) || amount <= 0) return "Enter an amount greater than zero.";
  if (fields.dueDay !== "") {
    const day = Number(fields.dueDay);
    if (Number.isNaN(day) || day < 1 || day > 31) return "Due day must be between 1 and 31.";
  }
  return null;
}

export function EmiManager() {
  const { data, error, isLoading, refresh } = useApi<Emi[]>("/emis");
  const [editing, setEditing] = useState<string | null>(null);
  const [fields, setFields] = useState<EmiFields>(EMPTY);
  const [adding, setAdding] = useState(false);
  const [addFields, setAddFields] = useState<EmiFields>(EMPTY);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  if (isLoading && !data) return <Spinner />;
  if (error && !data) return <ErrorState message="Could not load EMIs & subscriptions." onRetry={refresh} />;

  const emis = data ?? [];

  function startEdit(emi: Emi) {
    setEditing(emi.id);
    setFields({ label: emi.label, amount: String(emi.amount), dueDay: emi.dueDay == null ? "" : String(emi.dueDay) });
    setFormError(null);
  }

  async function saveEdit(id: string) {
    const err = validate(fields);
    if (err) return setFormError(err);
    setBusy(true);
    setFormError(null);
    try {
      await apiClient.put(`/emis/${id}`, toBody(fields));
      setEditing(null);
      refresh();
    } catch {
      setFormError("Could not save. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  async function deactivate(id: string) {
    setBusy(true);
    try {
      await apiClient.patch(`/emis/${id}`);
      refresh();
    } finally {
      setBusy(false);
    }
  }

  async function createEmi() {
    const err = validate(addFields);
    if (err) return setFormError(err);
    setBusy(true);
    setFormError(null);
    try {
      await apiClient.post("/emis", toBody(addFields));
      setAdding(false);
      setAddFields(EMPTY);
      refresh();
    } catch {
      setFormError("Could not add. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-4">
      <div>
        {!adding ? (
          <Button onClick={() => { setAdding(true); setFormError(null); }}>
            Add EMI / subscription
          </Button>
        ) : (
          <Card className="max-w-2xl">
            <h2 className="mb-3 text-sm font-semibold text-foreground">New EMI / subscription</h2>
            <EmiFieldset fields={addFields} onChange={setAddFields} />
            {formError && <p role="alert" className="mt-2 text-sm text-[var(--color-danger)]">{formError}</p>}
            <div className="mt-3 flex gap-2">
              <Button size="sm" onClick={createEmi} disabled={busy}>
                {busy ? "Adding…" : "Add"}
              </Button>
              <Button size="sm" variant="secondary" onClick={() => { setAdding(false); setAddFields(EMPTY); }}>
                Cancel
              </Button>
            </div>
          </Card>
        )}
      </div>

      {emis.length === 0 ? (
        <Card>
          <EmptyState message="No active EMIs or subscriptions." />
        </Card>
      ) : (
        <div className="grid grid-cols-[repeat(auto-fit,minmax(16rem,1fr))] gap-3">
          {emis.map((emi) => (
            <Card key={emi.id}>
              {editing === emi.id ? (
                <>
                  <EmiFieldset fields={fields} onChange={setFields} />
                  {formError && <p role="alert" className="mt-2 text-sm text-[var(--color-danger)]">{formError}</p>}
                  <div className="mt-3 flex gap-2">
                    <Button size="sm" onClick={() => saveEdit(emi.id)} disabled={busy}>
                      {busy ? "Saving…" : "Save"}
                    </Button>
                    <Button size="sm" variant="secondary" onClick={() => setEditing(null)}>
                      Cancel
                    </Button>
                  </div>
                </>
              ) : (
                <div className="flex flex-col gap-3">
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span
                        aria-hidden
                        className="flex size-8 shrink-0 items-center justify-center rounded-full bg-surface-muted text-foreground-muted"
                      >
                        <Repeat className="size-4" />
                      </span>
                      <p className="font-medium text-foreground">{emi.label}</p>
                    </div>
                    {emi.detectedFromSms && <Badge tone="brand">Auto-detected</Badge>}
                  </div>
                  <p className="tnum text-lg font-semibold text-foreground">{formatCurrency(emi.amount)}</p>
                  {emi.dueDay != null && (
                    <p className="text-xs text-foreground-subtle">Due day {emi.dueDay}</p>
                  )}
                  <div className="flex gap-2">
                    <Button size="sm" variant="secondary" onClick={() => startEdit(emi)}>
                      Edit
                    </Button>
                    <Button
                      size="sm"
                      variant="danger"
                      onClick={() => deactivate(emi.id)}
                      disabled={busy}
                      aria-label={`Deactivate ${emi.label}`}
                    >
                      Deactivate
                    </Button>
                  </div>
                </div>
              )}
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}

function EmiFieldset({ fields, onChange }: { fields: EmiFields; onChange: (f: EmiFields) => void }) {
  return (
    <div className="grid gap-3 sm:grid-cols-3">
      <label className="block text-sm">
        <span className="mb-1 block text-foreground-muted">Label</span>
        <Input
          type="text"
          aria-label="Label"
          value={fields.label}
          onChange={(e) => onChange({ ...fields, label: e.target.value })}
        />
      </label>
      <label className="block text-sm">
        <span className="mb-1 block text-foreground-muted">Amount (₹)</span>
        <Input
          type="number"
          min={1}
          aria-label="Amount"
          value={fields.amount}
          onChange={(e) => onChange({ ...fields, amount: e.target.value })}
        />
      </label>
      <label className="block text-sm">
        <span className="mb-1 block text-foreground-muted">Due day (optional)</span>
        <Input
          type="number"
          min={1}
          max={31}
          aria-label="Due day"
          value={fields.dueDay}
          onChange={(e) => onChange({ ...fields, dueDay: e.target.value })}
        />
      </label>
    </div>
  );
}
