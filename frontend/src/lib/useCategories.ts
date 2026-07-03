"use client";

import { useApi } from "@/lib/useApi";

export interface Category {
  id: number;
  name: string;
  icon: string;
}

/**
 * Fetches the shared category list (`GET /categories`, the 12 predefined categories) and
 * exposes a lookup by id. Cached by SWR, so the several pages that need category names
 * (Budget, Transactions, Dashboard) share a single request.
 */
export function useCategories() {
  const { data, error, isLoading } = useApi<Category[]>("/categories");
  const byId = new Map((data ?? []).map((c) => [c.id, c]));
  return {
    categories: data ?? [],
    categoryName: (id: number) => byId.get(id)?.name ?? `Category ${id}`,
    isLoading,
    error,
  };
}
