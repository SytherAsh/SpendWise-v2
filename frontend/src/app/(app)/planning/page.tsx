"use client";

import { Target, RefreshCw } from "lucide-react";
import { PageHeader } from "@/components/shared/ui";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { BudgetManager } from "@/components/budget/BudgetManager";
import { EmiManager } from "@/components/emis/EmiManager";

/**
 * Planning consolidates the former Budget and EMIs/Subscriptions screens into one
 * destination (IA redesign) — budgets and recurring commitments are planned together.
 */
export default function PlanningPage() {
  return (
    <>
      <PageHeader title="Planning" subtitle="Set monthly budgets and manage recurring payments" />
      <Tabs defaultValue="budgets">
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
