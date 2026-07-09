"use client";

import { Upload, AlertTriangle } from "lucide-react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { useShell } from "@/lib/shell";

/**
 * Bulk-import entry point (PDF/CSV bank statement) from the account menu. The backend endpoint
 * this would call (`POST /users/me/bank-statement`) is documented in docs/api.md but has no
 * server-side implementation yet (implementation/tracking/STATUS.md flags it as an acknowledged,
 * unscoped gap — same soft-fail treatment the Android app already uses). This dialog ships the
 * UI shape now — file picker included, disabled — so wiring it up later is a backend-plus-one-line
 * change, not a redesign; it explains the gap rather than pretending to accept a file.
 */
export function UploadStatementDialog() {
  const { uploadOpen, setUploadOpen } = useShell();

  return (
    <Dialog open={uploadOpen} onOpenChange={setUploadOpen}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Import a bank statement</DialogTitle>
          <DialogDescription>Add transactions in bulk from a PDF or CSV statement.</DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <label
            className="flex cursor-not-allowed flex-col items-center gap-2 rounded-[var(--radius-sm)] border border-dashed border-border-strong px-6 py-8 text-center opacity-50"
            aria-disabled="true"
          >
            <Upload className="size-6 text-foreground-subtle" />
            <span className="text-sm text-foreground-muted">Drop a PDF or CSV file, or click to browse</span>
            <input type="file" accept=".pdf,.csv" disabled className="sr-only" />
          </label>

          <div
            role="status"
            className="flex items-start gap-2.5 rounded-[var(--radius-sm)] border border-[var(--color-warning-border)] bg-[var(--color-warning-surface)] px-4 py-3 text-sm text-[var(--color-warning)]"
          >
            <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
            <p>
              Statement import isn&apos;t available yet — we&apos;re working on it. In the meantime, add transactions
              one at a time with the <span className="font-medium">Add</span> button.
            </p>
          </div>
        </div>

        <div className="mt-1 flex justify-end">
          <Button type="button" variant="secondary" onClick={() => setUploadOpen(false)}>
            Close
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
