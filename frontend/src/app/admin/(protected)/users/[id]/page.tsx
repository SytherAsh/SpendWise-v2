import { PageHeader } from "@/components/shared/ui";
import { UserDetailView } from "@/components/admin/UserDetailView";

export default async function AdminUserDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <>
      <PageHeader title="User detail" subtitle="Full transaction, budget, and alert data for this user" />
      <UserDetailView userId={id} />
    </>
  );
}
