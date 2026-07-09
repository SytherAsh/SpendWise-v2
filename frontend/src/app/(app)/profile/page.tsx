"use client";

import { User, Users } from "lucide-react";
import { PageHeader } from "@/components/shared/ui";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { PersonalInfoTab } from "@/components/profile/PersonalInfoTab";
import { ContactsTab } from "@/components/profile/ContactsTab";

export default function ProfilePage() {
  return (
    <>
      <PageHeader title="Profile" subtitle="Your personal information and the contacts used for transaction tagging" />
      <Tabs defaultValue="personal-info">
        <TabsList>
          <TabsTrigger value="personal-info">
            <User className="size-4" />
            Personal Info
          </TabsTrigger>
          <TabsTrigger value="contacts">
            <Users className="size-4" />
            Contacts
          </TabsTrigger>
        </TabsList>
        <TabsContent value="personal-info">
          <PersonalInfoTab />
        </TabsContent>
        <TabsContent value="contacts">
          <ContactsTab />
        </TabsContent>
      </Tabs>
    </>
  );
}
