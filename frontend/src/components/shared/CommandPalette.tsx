"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { Command } from "cmdk";
import {
  LayoutDashboard,
  ArrowLeftRight,
  ChartPie,
  Target,
  Settings as SettingsIcon,
  Plus,
  Sparkles,
  type LucideIcon,
} from "lucide-react";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { useShell, NAV_ITEMS } from "@/lib/shell";
import { cn } from "@/lib/cn";

const NAV_ICONS: Record<string, LucideIcon> = {
  LayoutDashboard,
  ArrowLeftRight,
  ChartPie,
  Target,
  Settings: SettingsIcon,
};

const itemClass =
  "flex cursor-pointer items-center gap-3 rounded-[var(--radius-sm)] px-3 py-2.5 text-sm text-foreground aria-selected:bg-surface-muted";

export function CommandPalette() {
  const router = useRouter();
  const { commandOpen, setCommandOpen, setQuickAddOpen, setAssistantOpen } = useShell();

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "k" && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        setCommandOpen(!commandOpen);
      }
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [commandOpen, setCommandOpen]);

  function run(action: () => void) {
    setCommandOpen(false);
    action();
  }

  return (
    <DialogPrimitive.Root open={commandOpen} onOpenChange={setCommandOpen}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm data-[state=open]:animate-in data-[state=open]:fade-in-0" />
        <DialogPrimitive.Content
          className="fixed left-1/2 top-[18%] z-50 w-full max-w-xl -translate-x-1/2 overflow-hidden rounded-[var(--radius-lg)] border border-border bg-surface shadow-[var(--shadow-lg)] focus:outline-none data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95"
          aria-label="Command menu"
        >
          <DialogPrimitive.Title className="sr-only">Command menu</DialogPrimitive.Title>
          <Command className="[&_[cmdk-input-wrapper]]:border-b [&_[cmdk-input-wrapper]]:border-border">
            <Command.Input
              placeholder="Search or jump to…"
              className="h-12 w-full bg-transparent px-4 text-sm text-foreground outline-none placeholder:text-foreground-subtle"
            />
            <Command.List className="max-h-80 overflow-y-auto p-2">
              <Command.Empty className="py-6 text-center text-sm text-foreground-subtle">
                No results found.
              </Command.Empty>

              <Command.Group heading="Go to" className="[&_[cmdk-group-heading]]:px-3 [&_[cmdk-group-heading]]:py-1.5 [&_[cmdk-group-heading]]:text-xs [&_[cmdk-group-heading]]:font-medium [&_[cmdk-group-heading]]:text-foreground-subtle">
                {NAV_ITEMS.map((item) => {
                  const Icon = NAV_ICONS[item.icon];
                  return (
                    <Command.Item
                      key={item.href}
                      value={item.label}
                      onSelect={() => run(() => router.push(item.href))}
                      className={itemClass}
                    >
                      {Icon && <Icon className="size-4 text-foreground-subtle" />}
                      {item.label}
                    </Command.Item>
                  );
                })}
              </Command.Group>

              <Command.Group heading="Actions" className="[&_[cmdk-group-heading]]:px-3 [&_[cmdk-group-heading]]:py-1.5 [&_[cmdk-group-heading]]:text-xs [&_[cmdk-group-heading]]:font-medium [&_[cmdk-group-heading]]:text-foreground-subtle">
                <Command.Item value="Add transaction" onSelect={() => run(() => setQuickAddOpen(true))} className={itemClass}>
                  <Plus className="size-4 text-foreground-subtle" />
                  Add a transaction
                </Command.Item>
                <Command.Item value="Ask the assistant" onSelect={() => run(() => setAssistantOpen(true))} className={cn(itemClass)}>
                  <Sparkles className="size-4 text-foreground-subtle" />
                  Ask the assistant
                </Command.Item>
              </Command.Group>
            </Command.List>
          </Command>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
