"use client";

import { User, SlidersHorizontal, Download } from "lucide-react";
import { PageHeader } from "@/components/shared/ui";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { ProfileTab } from "@/components/settings/ProfileTab";
import { PreferencesTab } from "@/components/settings/PreferencesTab";
import { ExportForm } from "@/components/export/ExportForm";

export default function SettingsPage() {
  return (
    <>
      <PageHeader title="Settings" subtitle="Your profile, alert and payment-source preferences, appearance, and data export" />
      <Tabs defaultValue="profile">
        <TabsList>
          <TabsTrigger value="profile">
            <User className="size-4" />
            Profile
          </TabsTrigger>
          <TabsTrigger value="preferences">
            <SlidersHorizontal className="size-4" />
            Preferences
          </TabsTrigger>
          <TabsTrigger value="export">
            <Download className="size-4" />
            Export
          </TabsTrigger>
        </TabsList>
        <TabsContent value="profile">
          <ProfileTab />
        </TabsContent>
        <TabsContent value="preferences">
          <PreferencesTab />
        </TabsContent>
        <TabsContent value="export">
          <ExportForm />
        </TabsContent>
      </Tabs>
    </>
  );
}
