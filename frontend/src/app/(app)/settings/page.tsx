"use client";

import { SlidersHorizontal, Palette, ShieldCheck, Lock, Download } from "lucide-react";
import { PageHeader } from "@/components/shared/ui";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { PreferencesTab } from "@/components/settings/PreferencesTab";
import { AppearanceTab } from "@/components/settings/AppearanceTab";
import { SecurityTab } from "@/components/settings/SecurityTab";
import { PrivacyTab } from "@/components/settings/PrivacyTab";
import { ExportForm } from "@/components/export/ExportForm";

/** Application-wide settings — personal info and contacts live on the Profile page instead
 *  (see app/(app)/profile). Security and Privacy & Data are placeholders for now (2026-07-09,
 *  UI/UX polish phase): sketched to look like a real settings page, not wired to any endpoint. */
export default function SettingsPage() {
  return (
    <>
      <PageHeader
        title="Settings"
        subtitle="App preferences, appearance, security, privacy, and data export"
      />
      <Tabs defaultValue="preferences">
        <TabsList>
          <TabsTrigger value="preferences">
            <SlidersHorizontal className="size-4" />
            Preferences
          </TabsTrigger>
          <TabsTrigger value="appearance">
            <Palette className="size-4" />
            Appearance
          </TabsTrigger>
          <TabsTrigger value="security">
            <Lock className="size-4" />
            Security
          </TabsTrigger>
          <TabsTrigger value="privacy">
            <ShieldCheck className="size-4" />
            Privacy &amp; Data
          </TabsTrigger>
          <TabsTrigger value="export">
            <Download className="size-4" />
            Export
          </TabsTrigger>
        </TabsList>
        <TabsContent value="preferences">
          <PreferencesTab />
        </TabsContent>
        <TabsContent value="appearance">
          <AppearanceTab />
        </TabsContent>
        <TabsContent value="security">
          <SecurityTab />
        </TabsContent>
        <TabsContent value="privacy">
          <PrivacyTab />
        </TabsContent>
        <TabsContent value="export">
          <ExportForm />
        </TabsContent>
      </Tabs>
    </>
  );
}
