"use client";

import useSWR, { type SWRConfiguration } from "swr";
import { adminSwrFetcher } from "@/lib/adminApiClient";

export interface UseAdminApiResult<T> {
  data: T | undefined;
  error: Error | undefined;
  isLoading: boolean;
  refresh: () => void;
}

/** Admin Portal counterpart to `lib/useApi.ts` — same shape, fetches via the admin token instead. */
export function useAdminApi<T>(key: string | null, config?: SWRConfiguration<T>): UseAdminApiResult<T> {
  const { data, error, isLoading, mutate } = useSWR<T>(key, adminSwrFetcher, {
    revalidateOnFocus: false,
    ...config,
  });

  return {
    data,
    error: error as Error | undefined,
    isLoading,
    refresh: () => {
      void mutate().catch(() => {});
    },
  };
}
