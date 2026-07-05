import { PageHeader } from "@/components/shared/ui";
import { UsersTable } from "@/components/admin/UsersTable";

export default function AdminUsersPage() {
  return (
    <>
      <PageHeader title="Users" subtitle="Every SpendWise user, with basic activity stats" />
      <UsersTable />
    </>
  );
}
