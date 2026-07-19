"use client";

import { useState } from "react";
import { adminApiClient } from "@/lib/adminApiClient";
import { useAdminApi } from "@/lib/useAdminApi";
import { Card, ErrorState, Spinner } from "@/components/shared/ui";

interface JobSchedule {
  jobKey: string;
  displayName: string;
  scheduleType: "INTERVAL" | "WEEKLY";
  intervalValue: number | null;
  intervalUnit: "MINUTES" | "HOURS" | "DAYS" | null;
  dayOfWeek: string | null;
  hourOfDay: number | null;
  updatedAt: string;
}

const DAYS_OF_WEEK = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];
const INTERVAL_UNITS: Array<NonNullable<JobSchedule["intervalUnit"]>> = ["MINUTES", "HOURS", "DAYS"];
const DAY_LABELS: Record<string, string> = {
  MON: "Monday",
  TUE: "Tuesday",
  WED: "Wednesday",
  THU: "Thursday",
  FRI: "Friday",
  SAT: "Saturday",
  SUN: "Sunday",
};

function describeSchedule(job: JobSchedule): string {
  if (job.scheduleType === "WEEKLY") {
    return `Every ${DAY_LABELS[job.dayOfWeek ?? ""] ?? job.dayOfWeek} at ${String(job.hourOfDay).padStart(2, "0")}:00 UTC`;
  }
  const unit = (job.intervalUnit ?? "minutes").toLowerCase();
  const plural = job.intervalValue === 1 ? unit.replace(/s$/, "") : unit;
  return `Every ${job.intervalValue} ${plural}`;
}

/** Admin's "adjust every background job's schedule" page (ML strategy phase, 2026-07-19, ADR-018)
 * — separate from `/admin/ops`'s "run now" buttons, which trigger a job immediately regardless of
 * its schedule. Each row edits and saves independently; a save takes effect immediately (no
 * redeploy) per `DynamicJobScheduler#reschedule` on the backend. */
export function JobSchedulesPanel() {
  const { data, error, isLoading, refresh } = useAdminApi<JobSchedule[]>("/admin/job-schedules");

  if (isLoading && !data) return <Spinner />;
  if (error && !data) return <ErrorState message="Could not load job schedules." onRetry={refresh} />;

  return (
    <div className="max-w-2xl space-y-4">
      {data?.map((job) => <JobScheduleRow key={job.jobKey} job={job} onSaved={refresh} />)}
    </div>
  );
}

function JobScheduleRow({ job, onSaved }: { job: JobSchedule; onSaved: () => void }) {
  const [scheduleType, setScheduleType] = useState<JobSchedule["scheduleType"]>(job.scheduleType);
  // Held as a string so the field can be cleared and retyped — a numeric state forced every empty
  // keystroke back to a value (previously Math.max(1, Number("")) === 1), so clearing then typing
  // "45" produced "145". Parsed and clamped to a valid integer only where a number is needed.
  const [intervalValue, setIntervalValue] = useState(String(job.intervalValue ?? 30));
  const [intervalUnit, setIntervalUnit] = useState<NonNullable<JobSchedule["intervalUnit"]>>(job.intervalUnit ?? "MINUTES");
  const [dayOfWeek, setDayOfWeek] = useState(job.dayOfWeek ?? "SUN");
  const [hourOfDay, setHourOfDay] = useState(job.hourOfDay ?? 4);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const intervalNumber = Math.max(1, Math.floor(Number(intervalValue) || 1));

  const dirty =
    scheduleType !== job.scheduleType
    || (scheduleType === "INTERVAL" && (intervalNumber !== job.intervalValue || intervalUnit !== job.intervalUnit))
    || (scheduleType === "WEEKLY" && (dayOfWeek !== job.dayOfWeek || hourOfDay !== job.hourOfDay));

  async function onSave() {
    setSaving(true);
    setMessage(null);
    try {
      const body =
        scheduleType === "INTERVAL"
          ? { scheduleType, intervalValue: intervalNumber, intervalUnit }
          : { scheduleType, dayOfWeek, hourOfDay };
      await adminApiClient.put(`/admin/job-schedules/${job.jobKey}`, body);
      setMessage("Saved — takes effect immediately.");
      onSaved();
    } catch {
      setMessage("Could not save this schedule. Please check the values and try again.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card>
      <div className="mb-3">
        <h3 className="text-sm font-semibold">{job.displayName}</h3>
        <p className="text-xs text-foreground-muted">Currently: {describeSchedule(job)}</p>
      </div>

      <div className="mb-3 flex flex-wrap items-center gap-2 text-sm">
        <select
          value={scheduleType}
          onChange={(e) => setScheduleType(e.target.value as JobSchedule["scheduleType"])}
          className="rounded-md border border-black/15 px-2 py-1.5 dark:border-white/15 dark:bg-neutral-800"
        >
          <option value="INTERVAL">Every N minutes/hours/days</option>
          <option value="WEEKLY">Every day-of-week at a time</option>
        </select>

        {scheduleType === "INTERVAL" ? (
          <>
            <span>Every</span>
            <input
              type="number"
              min={1}
              value={intervalValue}
              onChange={(e) => setIntervalValue(e.target.value)}
              className="w-20 rounded-md border border-black/15 px-2 py-1.5 dark:border-white/15 dark:bg-neutral-800"
            />
            <select
              value={intervalUnit}
              onChange={(e) => setIntervalUnit(e.target.value as NonNullable<JobSchedule["intervalUnit"]>)}
              className="rounded-md border border-black/15 px-2 py-1.5 dark:border-white/15 dark:bg-neutral-800"
            >
              {INTERVAL_UNITS.map((unit) => (
                <option key={unit} value={unit}>
                  {unit?.toLowerCase()}
                </option>
              ))}
            </select>
          </>
        ) : (
          <>
            <span>Every</span>
            <select
              value={dayOfWeek}
              onChange={(e) => setDayOfWeek(e.target.value)}
              className="rounded-md border border-black/15 px-2 py-1.5 dark:border-white/15 dark:bg-neutral-800"
            >
              {DAYS_OF_WEEK.map((day) => (
                <option key={day} value={day}>
                  {DAY_LABELS[day]}
                </option>
              ))}
            </select>
            <span>at</span>
            <select
              value={hourOfDay}
              onChange={(e) => setHourOfDay(Number(e.target.value))}
              className="rounded-md border border-black/15 px-2 py-1.5 dark:border-white/15 dark:bg-neutral-800"
            >
              {Array.from({ length: 24 }, (_, h) => (
                <option key={h} value={h}>
                  {String(h).padStart(2, "0")}:00 UTC
                </option>
              ))}
            </select>
          </>
        )}
      </div>

      <button
        type="button"
        onClick={onSave}
        disabled={saving || !dirty}
        className="rounded-md bg-brand-700 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
      >
        {saving ? "Saving…" : "Save"}
      </button>
      {message && (
        <p role="status" className="mt-2 text-sm text-foreground-muted">
          {message}
        </p>
      )}
    </Card>
  );
}
