"use client";

import { useState } from "react";
import { useSWRConfig } from "swr";
import { ArrowDownLeft, ArrowUpRight } from "lucide-react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { apiClient } from "@/lib/apiClient";
import { useShell } from "@/lib/shell";
import { cn } from "@/lib/cn";

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export function QuickAddTransaction() {
  const { quickAddOpen, setQuickAddOpen } = useShell();
  const { mutate } = useSWRConfig();

  const [direction, setDirection] = useState<"out" | "in">("out");
  const [amount, setAmount] = useState("");
  const [recipient, setRecipient] = useState("");
  const [date, setDate] = useState(today());
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function reset() {
    setDirection("out");
    setAmount("");
    setRecipient("");
    setDate(today());
    setError(null);
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    const value = Number(amount);
    if (!Number.isFinite(value) || value <= 0) {
      setError("Enter a valid amount.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await apiClient.post("/transactions", {
        transactionDate: new Date(`${date}T12:00:00`).toISOString(),
        amount: direction === "out" ? -Math.abs(value) : Math.abs(value),
        recipientName: recipient.trim() || null,
        transactionMode: "MANUAL",
      });
      // Revalidate any transaction/analytics list currently in the SWR cache.
      await mutate((key) => typeof key === "string" && (key.startsWith("/transactions") || key.startsWith("/analytics")));
      reset();
      setQuickAddOpen(false);
    } catch {
      setError("Could not save the transaction. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={quickAddOpen} onOpenChange={(o) => (o ? setQuickAddOpen(true) : (setQuickAddOpen(false), reset()))}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add a transaction</DialogTitle>
          <DialogDescription>Log a payment manually. It will be auto-categorized.</DialogDescription>
        </DialogHeader>

        <form onSubmit={onSubmit} className="flex flex-col gap-4">
          <div className="grid grid-cols-2 gap-2">
            {(["out", "in"] as const).map((d) => (
              <button
                key={d}
                type="button"
                onClick={() => setDirection(d)}
                aria-pressed={direction === d}
                className={cn(
                  "flex items-center justify-center gap-2 rounded-[var(--radius-sm)] border px-3 py-2 text-sm font-medium transition-colors",
                  direction === d
                    ? "border-brand-600 bg-brand-50 text-brand-800"
                    : "border-border-strong text-foreground-muted hover:bg-surface-muted",
                )}
              >
                {d === "out" ? <ArrowUpRight className="size-4" /> : <ArrowDownLeft className="size-4" />}
                {d === "out" ? "Money out" : "Money in"}
              </button>
            ))}
          </div>

          <label className="flex flex-col gap-1.5 text-sm font-medium text-foreground">
            Amount (₹)
            <Input
              type="number"
              inputMode="decimal"
              min="0"
              step="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0"
              autoFocus
              required
            />
          </label>

          <label className="flex flex-col gap-1.5 text-sm font-medium text-foreground">
            Payee / recipient
            <Input value={recipient} onChange={(e) => setRecipient(e.target.value)} placeholder="e.g. Swiggy" />
          </label>

          <label className="flex flex-col gap-1.5 text-sm font-medium text-foreground">
            Date
            <Input type="date" value={date} max={today()} onChange={(e) => setDate(e.target.value)} required />
          </label>

          {error && <p className="text-sm text-[var(--color-danger)]">{error}</p>}

          <div className="mt-1 flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => setQuickAddOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting ? "Saving…" : "Add transaction"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
