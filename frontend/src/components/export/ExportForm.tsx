"use client";

import { useState } from "react";
import { downloadFile } from "@/lib/apiClient";
import { currentYear } from "@/lib/format";
import { Card, ErrorState } from "@/components/shared/ui";
import { Button } from "@/components/ui/button";
import { Input, Select } from "@/components/ui/input";

type RangeMode = "custom" | "financialYear";
type Format = "pdf" | "csv";

/**
 * Indian financial year YYYY = YYYY-04-01 .. (YYYY+1)-03-31. We convert an FY selection to
 * an explicit from/to range on the client so both export endpoints (the CSV endpoint takes
 * only from/to) can be driven uniformly — see docs/api.md Epic 7 addendum.
 */
function financialYearRange(fy: number): { from: string; to: string } {
  return { from: `${fy}-04-01`, to: `${fy + 1}-03-31` };
}

export function ExportForm() {
  const [mode, setMode] = useState<RangeMode>("custom");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [fy, setFy] = useState(currentYear());
  const [format, setFormat] = useState<Format>("pdf");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function resolveRange(): { from: string; to: string } | null {
    if (mode === "financialYear") return financialYearRange(fy);
    if (!from || !to) {
      setError("Select both a start and end date.");
      return null;
    }
    if (from > to) {
      setError("The end date must be on or after the start date.");
      return null;
    }
    return { from, to };
  }

  async function onDownload(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    const range = resolveRange();
    if (!range) return;

    setBusy(true);
    try {
      const query = `from=${range.from}&to=${range.to}`;
      const filename =
        mode === "financialYear"
          ? `spendwise-FY${fy}-${fy + 1}.${format}`
          : `spendwise-${range.from}_to_${range.to}.${format}`;
      await downloadFile(`/analytics/export/${format}?${query}`, filename);
    } catch {
      setError("Could not generate the export. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  const years = Array.from({ length: 6 }, (_, i) => currentYear() - i);

  return (
    <Card className="max-w-xl">
      <form onSubmit={onDownload} className="space-y-5">
        <fieldset>
          <legend className="mb-2 text-sm font-semibold uppercase tracking-wide text-foreground-muted">Range</legend>
          <div className="flex gap-4 text-sm">
            <label className="flex items-center gap-2">
              <input
                type="radio"
                name="mode"
                className="accent-brand-600"
                checked={mode === "custom"}
                onChange={() => setMode("custom")}
              />
              Custom dates
            </label>
            <label className="flex items-center gap-2">
              <input
                type="radio"
                name="mode"
                className="accent-brand-600"
                checked={mode === "financialYear"}
                onChange={() => setMode("financialYear")}
              />
              Financial year
            </label>
          </div>

          {mode === "custom" ? (
            <div className="mt-3 flex flex-wrap gap-4">
              <label className="text-sm">
                <span className="mb-1 block text-foreground-muted">From</span>
                <Input type="date" aria-label="Start date" value={from} onChange={(e) => setFrom(e.target.value)} className="w-40" />
              </label>
              <label className="text-sm">
                <span className="mb-1 block text-foreground-muted">To</span>
                <Input type="date" aria-label="End date" value={to} onChange={(e) => setTo(e.target.value)} className="w-40" />
              </label>
            </div>
          ) : (
            <label className="mt-3 block text-sm">
              <span className="mb-1 block text-foreground-muted">Financial year (Apr–Mar)</span>
              <Select aria-label="Financial year" value={fy} onChange={(e) => setFy(Number(e.target.value))} className="w-40">
                {years.map((y) => (
                  <option key={y} value={y}>
                    FY {y}–{y + 1}
                  </option>
                ))}
              </Select>
            </label>
          )}
        </fieldset>

        <fieldset>
          <legend className="mb-2 text-sm font-semibold uppercase tracking-wide text-foreground-muted">Format</legend>
          <div className="flex gap-4 text-sm">
            <label className="flex items-center gap-2">
              <input type="radio" name="format" className="accent-brand-600" checked={format === "pdf"} onChange={() => setFormat("pdf")} />
              PDF report
            </label>
            <label className="flex items-center gap-2">
              <input type="radio" name="format" className="accent-brand-600" checked={format === "csv"} onChange={() => setFormat("csv")} />
              CSV data
            </label>
          </div>
        </fieldset>

        {error && <ErrorState message={error} />}

        <Button type="submit" disabled={busy}>
          {busy ? "Preparing…" : "Download"}
        </Button>
      </form>
    </Card>
  );
}
