"use client";

import { useState } from "react";
import { adminApiClient } from "@/lib/adminApiClient";
import { useAdminApi } from "@/lib/useAdminApi";
import { Card, ErrorState, Spinner } from "@/components/shared/ui";

interface MlCategoryMetrics {
  category_id: number;
  category_name: string;
  precision: number;
  recall: number;
  f1_score: number;
  support: number;
}

interface MlEvaluationResponse {
  generated_at: string;
  n_samples: number;
  accuracy: number;
  per_category: MlCategoryMetrics[];
  report_path: string;
}

/** ML accuracy + manual retrain trigger (E11-S2-T4 / E11-S3-T1). Retrain always goes through Categorization's service interface, never FastAPI directly. */
export function MlAccuracyPanel() {
  const { data, error, isLoading, refresh } = useAdminApi<MlEvaluationResponse>("/admin/ml/accuracy");
  const [retraining, setRetraining] = useState(false);
  const [retrainMessage, setRetrainMessage] = useState<string | null>(null);

  async function onRetrain() {
    setRetraining(true);
    setRetrainMessage(null);
    try {
      await adminApiClient.post("/admin/ml/retrain");
      setRetrainMessage("Retrain triggered.");
      refresh();
    } catch {
      setRetrainMessage("Could not trigger retrain. Please try again.");
    } finally {
      setRetraining(false);
    }
  }

  if (isLoading && !data) return <Spinner />;
  if (error && !data) return <ErrorState message="Could not load ML accuracy metrics." onRetry={refresh} />;

  return (
    <div className="max-w-2xl space-y-4">
      <Card>
        <div className="mb-3 flex items-center justify-between">
          <h3 className="text-sm font-semibold">Model accuracy</h3>
          <button
            type="button"
            onClick={onRetrain}
            disabled={retraining}
            className="rounded-md bg-brand-700 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
          >
            {retraining ? "Triggering…" : "Trigger retrain"}
          </button>
        </div>
        {retrainMessage && <p role="status" className="mb-3 text-sm text-foreground-muted">{retrainMessage}</p>}
        {data && (
          <>
            <p className="mb-1 text-2xl font-semibold">{(data.accuracy * 100).toFixed(1)}%</p>
            <p className="mb-4 text-sm text-foreground-muted">
              {data.n_samples} samples · generated {new Date(data.generated_at).toLocaleString()}
            </p>
            <table className="w-full text-left text-sm">
              <thead className="border-b border-black/10 text-foreground-muted dark:border-white/10">
                <tr>
                  <th className="py-2 font-medium">Category</th>
                  <th className="py-2 font-medium">Precision</th>
                  <th className="py-2 font-medium">Recall</th>
                  <th className="py-2 font-medium">F1</th>
                </tr>
              </thead>
              <tbody>
                {data.per_category.map((c) => (
                  <tr key={c.category_id} className="border-b border-black/5 last:border-0 dark:border-white/5">
                    <td className="py-2">{c.category_name}</td>
                    <td className="py-2">{c.precision.toFixed(2)}</td>
                    <td className="py-2">{c.recall.toFixed(2)}</td>
                    <td className="py-2">{c.f1_score.toFixed(2)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </Card>
    </div>
  );
}
