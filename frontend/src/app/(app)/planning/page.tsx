"use client";

import { useState } from "react";
import { Target, RefreshCw } from "lucide-react";
import { PageHeader } from "@/components/shared/ui";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { BudgetManager } from "@/components/budget/BudgetManager";
import { BudgetTotalStat } from "@/components/budget/BudgetTotalStat";
import { EmiManager } from "@/components/emis/EmiManager";

/**
 * Planning consolidates the former Budget and EMIs/Subscriptions screens into one
 * destination (IA redesign) — budgets and recurring commitments are planned together.
 */
export default function PlanningPage() {
  const [tab, setTab] = useState("budgets");

  return (
    <>
      <PageHeader
        title="Planning"
        subtitle="Set monthly budgets and manage recurring payments"
        action={tab === "budgets" ? <BudgetTotalStat /> : undefined}
      />
      <Tabs value={tab} onValueChange={setTab}>
        <TabsList>
          <TabsTrigger value="budgets">
            <Target className="size-4" />
            Budgets
          </TabsTrigger>
          <TabsTrigger value="recurring">
            <RefreshCw className="size-4" />
            EMIs &amp; Subscriptions
          </TabsTrigger>
        </TabsList>
        <TabsContent value="budgets">
          <BudgetManager />
        </TabsContent>
        <TabsContent value="recurring">
          <EmiManager />
        </TabsContent>
      </Tabs>
    </>
  );
}
