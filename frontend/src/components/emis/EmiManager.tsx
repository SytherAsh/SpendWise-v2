"use client";

import { useState } from "react";
import { apiClient } from "@/lib/apiClient";
import { useApi } from "@/lib/useApi";
import { formatCurrency } from "@/lib/format";
import { Card, EmptyState, ErrorState, Spinner } from "@/components/shared/ui";

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
    <div className="max-w-2xl space-y-4">
      <div>
        {!adding ? (
          <button
            type="button"
            onClick={() => { setAdding(true); setFormError(null); }}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white"
          >
            Add EMI / subscription
          </button>
        ) : (
          <Card>
            <h2 className="mb-3 text-sm font-semibold">New EMI / subscription</h2>
            <EmiFieldset fields={addFields} onChange={setAddFields} />
            {formError && <p role="alert" className="mt-2 text-sm text-red-600">{formError}</p>}
            <div className="mt-3 flex gap-2">
              <button type="button" onClick={createEmi} disabled={busy} className="rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white disabled:opacity-50">
                {busy ? "Adding…" : "Add"}
              </button>
              <button type="button" onClick={() => { setAdding(false); setAddFields(EMPTY); }} className="rounded-md border border-black/15 px-3 py-2 text-sm dark:border-white/15">
                Cancel
              </button>
            </div>
          </Card>
        )}
      </div>

      {emis.length === 0 ? (
        <EmptyState message="No active EMIs or subscriptions." />
      ) : (
        emis.map((emi) => (
          <Card key={emi.id}>
            {editing === emi.id ? (
              <>
                <EmiFieldset fields={fields} onChange={setFields} />
                {formError && <p role="alert" className="mt-2 text-sm text-red-600">{formError}</p>}
                <div className="mt-3 flex gap-2">
                  <button type="button" onClick={() => saveEdit(emi.id)} disabled={busy} className="rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white disabled:opacity-50">
                    {busy ? "Saving…" : "Save"}
                  </button>
                  <button type="button" onClick={() => setEditing(null)} className="rounded-md border border-black/15 px-3 py-2 text-sm dark:border-white/15">
                    Cancel
                  </button>
                </div>
              </>
            ) : (
              <div className="flex items-center justify-between gap-4">
                <div>
                  <p className="font-medium">{emi.label}</p>
                  <p className="text-sm text-neutral-500">
                    {formatCurrency(emi.amount)}
                    {emi.dueDay != null && ` · due day ${emi.dueDay}`}
                    {emi.detectedFromSms && " · auto-detected"}
                  </p>
                </div>
                <div className="flex shrink-0 gap-2">
                  <button type="button" onClick={() => startEdit(emi)} className="text-sm text-blue-600 underline">
                    Edit
                  </button>
                  <button
                    type="button"
                    onClick={() => deactivate(emi.id)}
                    disabled={busy}
                    aria-label={`Deactivate ${emi.label}`}
                    className="text-sm text-red-600 underline disabled:opacity-50"
                  >
                    Deactivate
                  </button>
                </div>
              </div>
            )}
          </Card>
        ))
      )}
    </div>
  );
}

function EmiFieldset({ fields, onChange }: { fields: EmiFields; onChange: (f: EmiFields) => void }) {
  return (
    <div className="grid gap-3 sm:grid-cols-3">
      <label className="block text-sm">
        <span className="mb-1 block text-neutral-500">Label</span>
        <input
          type="text"
          aria-label="Label"
          value={fields.label}
          onChange={(e) => onChange({ ...fields, label: e.target.value })}
          className="w-full rounded-md border border-black/15 px-3 py-2 text-sm dark:border-white/15 dark:bg-neutral-800"
        />
      </label>
      <label className="block text-sm">
        <span className="mb-1 block text-neutral-500">Amount (₹)</span>
        <input
          type="number"
          min={1}
          aria-label="Amount"
          value={fields.amount}
          onChange={(e) => onChange({ ...fields, amount: e.target.value })}
          className="w-full rounded-md border border-black/15 px-3 py-2 text-sm dark:border-white/15 dark:bg-neutral-800"
        />
      </label>
      <label className="block text-sm">
        <span className="mb-1 block text-neutral-500">Due day (optional)</span>
        <input
          type="number"
          min={1}
          max={31}
          aria-label="Due day"
          value={fields.dueDay}
          onChange={(e) => onChange({ ...fields, dueDay: e.target.value })}
          className="w-full rounded-md border border-black/15 px-3 py-2 text-sm dark:border-white/15 dark:bg-neutral-800"
        />
      </label>
    </div>
  );
}
