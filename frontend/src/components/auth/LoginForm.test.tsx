import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { LoginForm } from "@/components/auth/LoginForm";

/**
 * E10-S1-T1 required test: the login form's success and error states, with the Firebase
 * and API layers mocked (no real Firebase project / backend needed).
 */

const replace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
}));

const startPhoneSignIn = vi.fn();
const confirmOtp = vi.fn();
const signInWithGoogle = vi.fn();
vi.mock("@/lib/firebaseLogin", () => ({
  startPhoneSignIn: (...args: unknown[]) => startPhoneSignIn(...args),
  confirmOtp: (...args: unknown[]) => confirmOtp(...args),
  signInWithGoogle: (...args: unknown[]) => signInWithGoogle(...args),
}));

const verifyOtp = vi.fn();
const googleLogin = vi.fn();
vi.mock("@/lib/authApi", () => ({
  verifyOtp: (...args: unknown[]) => verifyOtp(...args),
  googleLogin: (...args: unknown[]) => googleLogin(...args),
}));

afterEach(() => {
  vi.clearAllMocks();
});

describe("LoginForm", () => {
  it("completes phone-OTP login and redirects to the dashboard on success", async () => {
    const user = userEvent.setup();
    const confirmation = { confirm: vi.fn() };
    startPhoneSignIn.mockResolvedValue(confirmation);
    confirmOtp.mockResolvedValue("firebase-id-token");
    verifyOtp.mockResolvedValue({ accessToken: "a", refreshToken: "r" });

    render(<LoginForm />);

    await user.type(screen.getByLabelText(/phone number/i), "+919999999999");
    await user.click(screen.getByRole("button", { name: /send otp/i }));

    // OTP step appears once the confirmation resolves.
    const otpInput = await screen.findByLabelText(/enter otp/i);
    await user.type(otpInput, "123456");
    await user.click(screen.getByRole("button", { name: /verify & sign in/i }));

    await waitFor(() => {
      expect(verifyOtp).toHaveBeenCalledWith("+919999999999", "firebase-id-token");
      expect(replace).toHaveBeenCalledWith("/dashboard");
    });
  });

  it("shows an inline error and does not redirect when OTP verification fails", async () => {
    const user = userEvent.setup();
    const confirmation = { confirm: vi.fn() };
    startPhoneSignIn.mockResolvedValue(confirmation);
    confirmOtp.mockResolvedValue("firebase-id-token");
    verifyOtp.mockRejectedValue(new Error("OTP expired or invalid"));

    render(<LoginForm />);

    await user.type(screen.getByLabelText(/phone number/i), "+919999999999");
    await user.click(screen.getByRole("button", { name: /send otp/i }));
    await user.type(await screen.findByLabelText(/enter otp/i), "000000");
    await user.click(screen.getByRole("button", { name: /verify & sign in/i }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(/OTP expired or invalid/i);
    expect(replace).not.toHaveBeenCalled();
  });

  it("completes Google login and redirects on success", async () => {
    const user = userEvent.setup();
    signInWithGoogle.mockResolvedValue("google-id-token");
    googleLogin.mockResolvedValue({ accessToken: "a", refreshToken: "r" });

    render(<LoginForm />);
    await user.click(screen.getByRole("button", { name: /continue with google/i }));

    await waitFor(() => {
      expect(googleLogin).toHaveBeenCalledWith("google-id-token");
      expect(replace).toHaveBeenCalledWith("/dashboard");
    });
  });

  it("surfaces an inline error when Google sign-in fails", async () => {
    const user = userEvent.setup();
    signInWithGoogle.mockRejectedValue(new Error("popup closed"));

    render(<LoginForm />);
    await user.click(screen.getByRole("button", { name: /continue with google/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/popup closed/i);
    expect(replace).not.toHaveBeenCalled();
  });
});
