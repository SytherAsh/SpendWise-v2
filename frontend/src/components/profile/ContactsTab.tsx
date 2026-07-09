"use client";

import { useState } from "react";
import { Users } from "lucide-react";
import { apiClient } from "@/lib/apiClient";
import { useContacts, RELATIONSHIP_TYPES, type Contact } from "@/lib/contacts";
import { Card, EmptyState, ErrorState, Spinner } from "@/components/shared/ui";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/cn";

interface ContactFields {
  name: string;
  relationshipType: Contact["relationshipType"];
  recipientNamePattern: string;
  upiId: string;
  phoneNumber: string;
}

const EMPTY: ContactFields = { name: "", relationshipType: "family", recipientNamePattern: "", upiId: "", phoneNumber: "" };

function toBody(fields: ContactFields) {
  return {
    name: fields.name.trim(),
    relationshipType: fields.relationshipType,
    recipientNamePattern: fields.recipientNamePattern.trim() || null,
    upiId: fields.upiId.trim() || null,
    phoneNumber: fields.phoneNumber.trim() || null,
  };
}

function validate(fields: ContactFields): string | null {
  if (!fields.name.trim()) return "Enter a name.";
  if (!fields.recipientNamePattern.trim() && !fields.upiId.trim() && !fields.phoneNumber.trim()) {
    return "Enter at least one of: the name as it appears in transactions, a UPI ID, or a phone number.";
  }
  return null;
}

function fieldsFromContact(contact: Contact): ContactFields {
  return {
    name: contact.name,
    relationshipType: contact.relationshipType,
    recipientNamePattern: contact.recipientNamePattern ?? "",
    upiId: contact.upiId ?? "",
    phoneNumber: contact.phoneNumber ?? "",
  };
}

export function ContactsTab() {
  const { contacts, error, isLoading, refresh } = useContacts();
  const [editing, setEditing] = useState<string | null>(null);
  const [fields, setFields] = useState<ContactFields>(EMPTY);
  const [adding, setAdding] = useState(false);
  const [addFields, setAddFields] = useState<ContactFields>(EMPTY);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  if (isLoading && contacts.length === 0 && !error) return <Spinner />;
  if (error && contacts.length === 0) return <ErrorState message="Could not load contacts." onRetry={refresh} />;

  function startEdit(contact: Contact) {
    setEditing(contact.id);
    setFields(fieldsFromContact(contact));
    setFormError(null);
  }

  async function saveEdit(id: string) {
    const err = validate(fields);
    if (err) return setFormError(err);
    setBusy(true);
    setFormError(null);
    try {
      await apiClient.put(`/contacts/${id}`, toBody(fields));
      setEditing(null);
      refresh();
    } catch {
      setFormError("Could not save. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  async function createContact() {
    const err = validate(addFields);
    if (err) return setFormError(err);
    setBusy(true);
    setFormError(null);
    try {
      await apiClient.post("/contacts", toBody(addFields));
      setAdding(false);
      setAddFields(EMPTY);
      refresh();
    } catch {
      setFormError("Could not add. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  async function deleteContact(id: string) {
    setBusy(true);
    try {
      await apiClient.delete(`/contacts/${id}`);
      refresh();
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="max-w-3xl space-y-4">
      <p className="text-sm text-foreground-muted">
        Tag people and accounts so Transfer transactions on the Transactions page can be grouped and labeled — e.g. every
        payment to or from &ldquo;Prachi&rdquo; grouped and tagged as Family. This never changes a transaction&apos;s
        spending category; it&apos;s a separate, additive label.
      </p>

      {!adding ? (
        <Button
          onClick={() => {
            setAdding(true);
            setFormError(null);
          }}
        >
          Add contact
        </Button>
      ) : (
        <Card className="max-w-2xl">
          <h2 className="mb-3 text-sm font-semibold text-foreground">New contact</h2>
          <ContactFieldset fields={addFields} onChange={setAddFields} />
          {formError && (
            <p role="alert" className="mt-2 text-sm text-[var(--color-danger)]">
              {formError}
            </p>
          )}
          <div className="mt-3 flex gap-2">
            <Button size="sm" onClick={createContact} disabled={busy}>
              {busy ? "Adding…" : "Add"}
            </Button>
            <Button
              size="sm"
              variant="secondary"
              onClick={() => {
                setAdding(false);
                setAddFields(EMPTY);
                setFormError(null);
              }}
            >
              Cancel
            </Button>
          </div>
        </Card>
      )}

      {contacts.length === 0 ? (
        <Card>
          <EmptyState
            icon={<Users className="size-6" />}
            title="No contacts yet"
            message="Add family and friends to group and label their Transfer transactions on the Transactions page."
          />
        </Card>
      ) : (
        <div className="grid grid-cols-[repeat(auto-fit,minmax(18rem,1fr))] gap-3">
          {contacts.map((contact) => (
            <Card key={contact.id}>
              {editing === contact.id ? (
                <>
                  <ContactFieldset fields={fields} onChange={setFields} />
                  {formError && (
                    <p role="alert" className="mt-2 text-sm text-[var(--color-danger)]">
                      {formError}
                    </p>
                  )}
                  <div className="mt-3 flex gap-2">
                    <Button size="sm" onClick={() => saveEdit(contact.id)} disabled={busy}>
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
                    <p className="font-medium text-foreground">{contact.name}</p>
                    <Badge tone="brand">{RELATIONSHIP_TYPES.find((r) => r.value === contact.relationshipType)?.label}</Badge>
                  </div>
                  <dl className="space-y-1 text-xs text-foreground-subtle">
                    {contact.recipientNamePattern && (
                      <div>
                        Name match: <span className="text-foreground-muted">{contact.recipientNamePattern}</span>
                      </div>
                    )}
                    {contact.upiId && (
                      <div>
                        UPI ID: <span className="text-foreground-muted">{contact.upiId}</span>
                      </div>
                    )}
                    {contact.phoneNumber && (
                      <div>
                        Phone: <span className="text-foreground-muted">{contact.phoneNumber}</span>
                      </div>
                    )}
                  </dl>
                  <div className="flex gap-2">
                    <Button size="sm" variant="secondary" onClick={() => startEdit(contact)}>
                      Edit
                    </Button>
                    <Button
                      size="sm"
                      variant="danger"
                      onClick={() => deleteContact(contact.id)}
                      disabled={busy}
                      aria-label={`Delete ${contact.name}`}
                    >
                      Delete
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

function ContactFieldset({ fields, onChange }: { fields: ContactFields; onChange: (f: ContactFields) => void }) {
  return (
    <div className="space-y-3">
      <label className="block text-sm">
        <span className="mb-1 block text-foreground-muted">Name</span>
        <Input type="text" aria-label="Name" value={fields.name} onChange={(e) => onChange({ ...fields, name: e.target.value })} />
      </label>

      <div className="text-sm">
        <span className="mb-1.5 block text-foreground-muted">Relationship</span>
        <div className="grid grid-cols-4 gap-2">
          {RELATIONSHIP_TYPES.map((r) => (
            <button
              key={r.value}
              type="button"
              onClick={() => onChange({ ...fields, relationshipType: r.value })}
              aria-pressed={fields.relationshipType === r.value}
              className={cn(
                "rounded-[var(--radius-sm)] border px-2 py-1.5 text-xs font-medium transition-colors",
                fields.relationshipType === r.value
                  ? "border-brand-600 bg-brand-50 text-brand-800"
                  : "border-border-strong text-foreground-muted hover:bg-surface-muted",
              )}
            >
              {r.label}
            </button>
          ))}
        </div>
      </div>

      <p className="text-xs text-foreground-subtle">
        Provide at least one way to recognize this person&apos;s transactions:
      </p>

      <label className="block text-sm">
        <span className="mb-1 block text-foreground-muted">Name as it appears in transactions</span>
        <Input
          type="text"
          aria-label="Name as it appears in transactions"
          placeholder="e.g. PRACHI SAVANT"
          value={fields.recipientNamePattern}
          onChange={(e) => onChange({ ...fields, recipientNamePattern: e.target.value })}
        />
      </label>

      <label className="block text-sm">
        <span className="mb-1 block text-foreground-muted">UPI ID (optional)</span>
        <Input
          type="text"
          aria-label="UPI ID"
          placeholder="e.g. prachi@okhdfcbank"
          value={fields.upiId}
          onChange={(e) => onChange({ ...fields, upiId: e.target.value })}
        />
      </label>

      <label className="block text-sm">
        <span className="mb-1 block text-foreground-muted">Phone number (optional)</span>
        <Input
          type="text"
          aria-label="Phone number"
          placeholder="e.g. 9876543210"
          value={fields.phoneNumber}
          onChange={(e) => onChange({ ...fields, phoneNumber: e.target.value })}
        />
      </label>
    </div>
  );
}
