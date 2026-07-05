"use client";

import { useState } from "react";

/**
 * Irreversible delete-user confirmation gate (E11-S3-T1 DoD: "requires a confirmation step
 * naming the user before executing"). The delete button stays disabled until the admin types the
 * exact identifier (phone/email) shown for the target user — this is the Required Test for this
 * story: the delete request must not be possible to fire without that exact match.
 */
export function DeleteUserDialog({
  identifier,
  busy,
  onConfirm,
  onCancel,
}: {
  identifier: string;
  busy: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const [typed, setTyped] = useState("");
  const confirmed = typed === identifier;

  return (
    <div role="dialog" aria-label="Confirm user deletion" className="rounded-md border border-red-300 bg-red-50 p-4 dark:border-red-900 dark:bg-red-950">
      <p className="mb-2 text-sm font-semibold text-red-800 dark:text-red-200">
        This permanently deletes all data for <span className="font-mono">{identifier}</span>. This cannot be undone.
      </p>
      <label className="mb-1 block text-sm" htmlFor="delete-confirm-input">
        Type <span className="font-mono">{identifier}</span> to confirm
      </label>
      <input
        id="delete-confirm-input"
        type="text"
        value={typed}
        onChange={(e) => setTyped(e.target.value)}
        className="mb-3 w-full rounded-md border border-black/15 px-3 py-2 text-sm dark:border-white/15 dark:bg-neutral-800"
      />
      <div className="flex gap-2">
        <button
          type="button"
          onClick={onConfirm}
          disabled={!confirmed || busy}
          className="rounded-md bg-red-600 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {busy ? "Deleting…" : "Permanently delete user"}
        </button>
        <button type="button" onClick={onCancel} className="rounded-md border border-black/15 px-3 py-2 text-sm dark:border-white/15">
          Cancel
        </button>
      </div>
    </div>
  );
}
