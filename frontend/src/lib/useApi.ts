"use client";

import useSWR, { type SWRConfiguration } from "swr";
import { swrFetcher } from "@/lib/apiClient";

export interface UseApiResult<T> {
  data: T | undefined;
  error: Error | undefined;
  /** True only on the very first load, before any data has arrived. */
  isLoading: boolean;
  /** True while a background revalidation is in flight. */
  isValidating: boolean;
  /**
   * True when the last fetch failed but we still hold previously-fetched data (E10-S3).
   * Callers render the stale data plus a `<StaleBanner />` in this state instead of an
   * error screen — SWR keeps `data` populated across a failed revalidation by default, so
   * this is just the derived flag.
   */
  isStale: boolean;
  refresh: () => void;
}

/**
 * Shared data hook for every dashboard page. `key` is an API path (e.g.
 * `/analytics/trends?from=...&to=...`) or `null` to skip fetching. Built on SWR so that a
 * successful fetch followed by a backend outage keeps rendering the last-good data
 * (`isStale`), which is the core of the offline/stale-handling story.
 */
export function useApi<T>(key: string | null, config?: SWRConfiguration<T>): UseApiResult<T> {
  const { data, error, isLoading, isValidating, mutate } = useSWR<T>(key, swrFetcher, {
    revalidateOnFocus: false,
    keepPreviousData: true,
    ...config,
  });

  return {
    data,
    error: error as Error | undefined,
    isLoading,
    isValidating,
    isStale: error != null && data !== undefined,
    refresh: () => {
      // A failed revalidation is expected while the backend is unreachable — swallow the
      // rejection so it doesn't surface as an unhandled promise rejection; SWR keeps the
      // last-good `data` and sets `error`, which is what drives `isStale`.
      void mutate().catch(() => {});
    },
  };
}
