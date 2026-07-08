"use client";

import { createContext, useContext, useMemo, useState, type ReactNode } from "react";

/**
 * Coordinates the app shell's global overlays so any surface (top bar buttons, the
 * command palette, keyboard shortcuts) can open the same command palette, quick-add
 * dialog, chat assistant, or mobile nav.
 */
interface ShellContextValue {
  commandOpen: boolean;
  setCommandOpen: (v: boolean) => void;
  quickAddOpen: boolean;
  setQuickAddOpen: (v: boolean) => void;
  assistantOpen: boolean;
  setAssistantOpen: (v: boolean) => void;
  mobileNavOpen: boolean;
  setMobileNavOpen: (v: boolean) => void;
}

const ShellContext = createContext<ShellContextValue | null>(null);

export function ShellProvider({ children }: { children: ReactNode }) {
  const [commandOpen, setCommandOpen] = useState(false);
  const [quickAddOpen, setQuickAddOpen] = useState(false);
  const [assistantOpen, setAssistantOpen] = useState(false);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  const value = useMemo(
    () => ({
      commandOpen,
      setCommandOpen,
      quickAddOpen,
      setQuickAddOpen,
      assistantOpen,
      setAssistantOpen,
      mobileNavOpen,
      setMobileNavOpen,
    }),
    [commandOpen, quickAddOpen, assistantOpen, mobileNavOpen],
  );

  return <ShellContext.Provider value={value}>{children}</ShellContext.Provider>;
}

export function useShell(): ShellContextValue {
  const ctx = useContext(ShellContext);
  if (!ctx) throw new Error("useShell must be used within ShellProvider");
  return ctx;
}

/** Shared nav definition — the 5 primary destinations. */
export const NAV_ITEMS = [
  { href: "/dashboard", label: "Dashboard", icon: "LayoutDashboard" },
  { href: "/transactions", label: "Transactions", icon: "ArrowLeftRight" },
  { href: "/analytics", label: "Analytics", icon: "ChartPie" },
  { href: "/planning", label: "Planning", icon: "Target" },
  { href: "/settings", label: "Settings", icon: "Settings" },
] as const;
