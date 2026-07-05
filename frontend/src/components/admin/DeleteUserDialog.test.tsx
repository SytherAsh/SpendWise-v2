import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { DeleteUserDialog } from "@/components/admin/DeleteUserDialog";

const IDENTIFIER = "+911111111111";

/** E11-S3-T1 Required Test: the delete request cannot fire without typing the exact identifier first. */
describe("DeleteUserDialog", () => {
  it("keeps the delete button disabled until the exact identifier is typed", async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();

    render(<DeleteUserDialog identifier={IDENTIFIER} busy={false} onConfirm={onConfirm} onCancel={vi.fn()} />);

    const deleteButton = screen.getByRole("button", { name: /permanently delete user/i });
    expect(deleteButton).toBeDisabled();

    await user.type(screen.getByLabelText(/type/i), IDENTIFIER.slice(0, -1)); // one character short
    expect(deleteButton).toBeDisabled();
    await user.click(deleteButton);
    expect(onConfirm).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText(/type/i), IDENTIFIER.slice(-1)); // completes the exact match
    expect(deleteButton).toBeEnabled();

    await user.click(deleteButton);
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it("does not fire on a mismatched identifier", async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();

    render(<DeleteUserDialog identifier={IDENTIFIER} busy={false} onConfirm={onConfirm} onCancel={vi.fn()} />);
    await user.type(screen.getByLabelText(/type/i), "not-the-right-value");

    expect(screen.getByRole("button", { name: /permanently delete user/i })).toBeDisabled();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it("calls onCancel when cancel is clicked", async () => {
    const user = userEvent.setup();
    const onCancel = vi.fn();

    render(<DeleteUserDialog identifier={IDENTIFIER} busy={false} onConfirm={vi.fn()} onCancel={onCancel} />);
    await user.click(screen.getByRole("button", { name: /^cancel$/i }));

    expect(onCancel).toHaveBeenCalledTimes(1);
  });
});
