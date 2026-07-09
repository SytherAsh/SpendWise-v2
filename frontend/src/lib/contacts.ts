"use client";

import { useApi } from "@/lib/useApi";

/** Matches ContactResponse (backend, com.spendwise.user) — ADR-010 counterparty metadata. */
export interface Contact {
  id: string;
  name: string;
  relationshipType: "family" | "friend" | "self" | "settlement";
  recipientNamePattern: string | null;
  upiId: string | null;
  phoneNumber: string | null;
  createdAt: string;
}

export const RELATIONSHIP_TYPES: Array<{ value: Contact["relationshipType"]; label: string }> = [
  { value: "family", label: "Family" },
  { value: "friend", label: "Friend" },
  { value: "self", label: "Self" },
  { value: "settlement", label: "Settlement" },
];

export function relationshipLabel(type: Contact["relationshipType"]): string {
  return RELATIONSHIP_TYPES.find((r) => r.value === type)?.label ?? type;
}

/** Cached by SWR — the Contacts tab, and the Transactions page's grouping/tagging, share one request. */
export function useContacts() {
  const { data, error, isLoading, refresh } = useApi<Contact[]>("/contacts");
  return { contacts: data ?? [], error, isLoading, refresh };
}

interface MatchableTransaction {
  recipientName: string | null;
  upiId: string | null;
}

/**
 * Family is matched by name only — trusted, since a user's own family names are distinctive
 * (family names collide with strangers' far less often than friends' first names do). Every
 * other relationship type matches on UPI ID, a phone-number prefix of the UPI ID (the common
 * `<phone>@bank` UPI format), or name — whichever identifiers the contact has set. First match
 * wins; contacts are checked in list order.
 */
export function matchContact(transaction: MatchableTransaction, contacts: Contact[]): Contact | null {
  const name = transaction.recipientName?.trim().toLowerCase() || null;
  const upiId = transaction.upiId?.trim().toLowerCase() || null;

  for (const contact of contacts) {
    const pattern = contact.recipientNamePattern?.trim().toLowerCase() || null;
    const nameMatches = pattern != null && name != null && (name === pattern || name.includes(pattern));

    if (contact.relationshipType === "family") {
      if (nameMatches) return contact;
      continue;
    }

    const contactUpiId = contact.upiId?.trim().toLowerCase() || null;
    const upiMatches = contactUpiId != null && upiId != null && upiId === contactUpiId;
    const phonePrefix = contact.phoneNumber?.trim() || null;
    const phoneMatches = phonePrefix != null && upiId != null && upiId.startsWith(phonePrefix.toLowerCase());

    if (upiMatches || phoneMatches || nameMatches) return contact;
  }
  return null;
}

export interface TransactionGroup {
  key: string;
  label: string;
  relationshipType: Contact["relationshipType"] | null;
  amount: number;
  count: number;
  transactionIds: string[];
}

/**
 * Groups a bounded set of transactions by matched contact, falling back to the raw
 * recipient/UPI/bank string when nothing matches — the "Airtel x10, summed" view on the
 * Transactions page. Sorted by absolute amount, largest first.
 */
export function groupTransactions<
  T extends MatchableTransaction & { id: string; amount: number; bank?: string | null },
>(transactions: T[], contacts: Contact[]): TransactionGroup[] {
  const groups = new Map<string, TransactionGroup>();
  for (const t of transactions) {
    const contact = matchContact(t, contacts);
    const fallbackLabel = t.recipientName ?? t.upiId ?? t.bank ?? "Unknown";
    const key = contact ? `contact:${contact.id}` : `raw:${fallbackLabel}`;
    const existing = groups.get(key);
    if (existing) {
      existing.amount += t.amount;
      existing.count += 1;
      existing.transactionIds.push(t.id);
    } else {
      groups.set(key, {
        key,
        label: contact ? contact.name : fallbackLabel,
        relationshipType: contact?.relationshipType ?? null,
        amount: t.amount,
        count: 1,
        transactionIds: [t.id],
      });
    }
  }
  return Array.from(groups.values()).sort((a, b) => Math.abs(b.amount) - Math.abs(a.amount));
}
