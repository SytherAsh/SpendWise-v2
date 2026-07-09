"use client";

import { useState } from "react";
import { CalendarDays, Check, ChevronDown } from "lucide-react";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useDateRange, type RangePreset } from "@/lib/date-range";
import { cn } from "@/lib/cn";

const PRESETS: Array<{ id: Exclude<RangePreset, "custom">; label: string }> = [
  { id: "this-month", label: "This month" },
  { id: "last-month", label: "Last month" },
  { id: "last-3-months", label: "Last 3 months" },
  { id: "last-6-months", label: "Last 6 months" },
  { id: "ytd", label: "Year to date" },
  { id: "this-fy", label: "This financial year" },
];

export function DateRangePicker() {
  const { range, setPreset, setCustom } = useDateRange();
  const [open, setOpen] = useState(false);
  const [from, setFrom] = useState(range.from);
  const [to, setTo] = useState(range.to);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button
          type="button"
          className="inline-flex items-center gap-2 rounded-[var(--radius-sm)] border border-border-strong bg-surface px-3 py-2 text-sm font-medium text-foreground transition-colors hover:bg-surface-muted"
        >
          <CalendarDays className="size-4 text-foreground-subtle" />
          <span>{range.label}</span>
          <ChevronDown className="size-4 text-foreground-subtle" />
        </button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-64 p-2">
        <ul className="flex flex-col">
          {PRESETS.map((p) => {
            const active = range.preset === p.id;
            return (
              <li key={p.id}>
                <button
                  type="button"
                  onClick={() => {
                    setPreset(p.id);
                    setOpen(false);
                  }}
                  className={cn(
                    "flex w-full items-center justify-between rounded-[var(--radius-sm)] px-3 py-2 text-sm transition-colors hover:bg-surface-muted",
                    active ? "font-medium text-brand-800" : "text-foreground",
                  )}
                >
                  {p.label}
                  {active && <Check className="size-4 text-brand-700" />}
                </button>
              </li>
            );
          })}
        </ul>
        <div className="mt-1 border-t border-border pt-2">
          <p className="px-3 pb-1.5 text-xs font-medium text-foreground-subtle">Custom range</p>
          <div className="flex items-center gap-2 px-1">
            <Input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} className="h-9" aria-label="From date" />
            <Input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} className="h-9" aria-label="To date" />
          </div>
          <Button
            size="sm"
            className="mt-2 w-full"
            disabled={!from || !to || from > to}
            onClick={() => {
              setCustom(from, to);
              setOpen(false);
            }}
          >
            Apply
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  );
}
