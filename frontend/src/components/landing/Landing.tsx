"use client";

import * as React from "react";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { motion, MotionConfig } from "framer-motion";
import {
  Smartphone,
  Sparkles,
  BarChart3,
  ShieldCheck,
  Bell,
  Target,
  MessageSquare,
  ArrowRight,
  Check,
  TrendingUp,
  Lock,
  PlayCircle,
} from "lucide-react";
import { categoryColor } from "@/lib/categories";
import { BrandMark } from "@/components/shared/BrandMark";
import { ThemeToggle } from "@/components/shared/ThemeToggle";
import { demoLogin } from "@/lib/authApi";

function demoErrorMessage(err: unknown): string {
  if (err instanceof Error && err.message) return err.message;
  return "Demo account unavailable. Please try again or sign up for a regular account.";
}

const EASE = [0.2, 0.8, 0.2, 1] as const;

const CATEGORIES = [
  "Shopping", "Food / Dine Out", "Groceries", "Travel", "Entertainment", "Subscriptions",
  "Sports & Fitness", "Cosmetics", "Medical", "Fees & Debt", "Bills", "Transfers", "Miscellaneous",
];

const FEATURES = [
  { icon: BarChart3, title: "One unified dashboard", body: "Every UPI and bank transaction across Paytm, GPay, PhonePe and SBI, aggregated into a single clear view." },
  { icon: Sparkles, title: "Smart categorization", body: "Transactions are auto-sorted into 13 categories by an ML model that learns from your corrections." },
  { icon: Target, title: "Budgets & progress", body: "Set monthly limits per category and watch progress — with suggestions drawn from your own history." },
  { icon: Bell, title: "Timely alerts", body: "Three-tier alerts warn you before you overspend, not after the damage is done." },
  { icon: MessageSquare, title: "Ask the assistant", body: "A built-in assistant answers questions about your spending, grounded in your real data." },
  { icon: ShieldCheck, title: "Private by design", body: "Raw SMS never leaves your phone. Only structured fields sync — DPDP Act 2023 aligned." },
];

const STEPS = [
  { icon: Smartphone, title: "Captured on device", body: "The Android app reads transaction SMS and parses the amount, payee and date — entirely on your phone." },
  { icon: Sparkles, title: "Categorized automatically", body: "Structured fields sync securely and are classified into spending categories in seconds." },
  { icon: BarChart3, title: "Visualized for you", body: "Open the web dashboard to see trends, budgets, and where every rupee went." },
];

const STATS = [
  { v: "12", label: "Spending categories" },
  { v: "4", label: "Sources unified" },
  { v: "0", label: "Raw SMS off device" },
  { v: "<15m", label: "To your first view" },
];

/* ---------------------------------------------------------------- helpers -- */

function Reveal({
  children,
  delay = 0,
  className,
}: {
  children: React.ReactNode;
  delay?: number;
  className?: string;
}) {
  return (
    <motion.div
      className={className}
      initial={{ opacity: 0, y: 16 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "-80px" }}
      transition={{ duration: 0.5, delay, ease: EASE }}
    >
      {children}
    </motion.div>
  );
}

/** Glowing gradient hero chart — the signature SpendWise data treatment. */
function HeroChart() {
  const line =
    "M0,150 C60,140 100,118 150,122 C205,127 232,92 288,90 C348,88 372,112 432,84 C486,60 508,52 548,42";
  const area = `${line} L560,42 L560,200 L0,200 Z`;
  return (
    <svg viewBox="0 0 560 200" preserveAspectRatio="none" className="h-40 w-full" aria-hidden>
      <defs>
        <linearGradient id="heroFill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--color-brand-400)" stopOpacity="0.42" />
          <stop offset="100%" stopColor="var(--color-brand-400)" stopOpacity="0" />
        </linearGradient>
        <filter id="heroGlow" x="-20%" y="-50%" width="140%" height="200%">
          <feGaussianBlur stdDeviation="3.2" result="b" />
          <feMerge>
            <feMergeNode in="b" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>
      {[47, 95, 143].map((y) => (
        <line key={y} x1="0" y1={y} x2="560" y2={y} stroke="var(--border)" strokeWidth="1" />
      ))}
      <motion.path
        d={area}
        fill="url(#heroFill)"
        initial={{ opacity: 0 }}
        whileInView={{ opacity: 1 }}
        viewport={{ once: true }}
        transition={{ duration: 0.8, delay: 0.5, ease: EASE }}
      />
      <motion.path
        d={line}
        fill="none"
        stroke="var(--color-brand-500)"
        strokeWidth="2.4"
        strokeLinecap="round"
        filter="url(#heroGlow)"
        vectorEffect="non-scaling-stroke"
        initial={{ pathLength: 0 }}
        whileInView={{ pathLength: 1 }}
        viewport={{ once: true }}
        transition={{ duration: 1.1, ease: EASE }}
      />
      <motion.circle
        cx="548"
        cy="42"
        r="4.5"
        fill="var(--color-brand-500)"
        filter="url(#heroGlow)"
        initial={{ scale: 0, opacity: 0 }}
        whileInView={{ scale: 1, opacity: 1 }}
        viewport={{ once: true }}
        transition={{ duration: 0.4, delay: 1.05, ease: EASE }}
      />
    </svg>
  );
}

/** Elevated dashboard preview shown in the hero. */
function ProductMock() {
  const bars = [
    { c: "Food / Dine Out", w: "100%", v: "₹8,200" },
    { c: "Shopping", w: "78%", v: "₹6,400" },
    { c: "Groceries", w: "52%", v: "₹3,300" },
    { c: "Travel", w: "34%", v: "₹1,900" },
  ];
  const stats = [
    { label: "Income", v: "₹68,000" },
    { label: "Net saved", v: "₹45,200" },
    { label: "Budgets", v: "6 / 8" },
  ];
  return (
    <div className="rounded-[var(--radius-lg)] border border-brand-400/25 bg-surface p-5 shadow-[var(--shadow-lg),var(--glow-brand-md)]">
      <div className="mb-4 flex items-start justify-between">
        <div>
          <p className="text-[11px] font-semibold uppercase tracking-[0.06em] text-foreground-subtle">
            Spent · this month
          </p>
          <p className="mono mt-1 text-3xl font-medium tracking-tight text-foreground">₹48,250</p>
        </div>
        <span className="mono inline-flex items-center gap-1 rounded-full bg-brand-50 px-2.5 py-1 text-xs font-semibold text-brand-700 dark:bg-brand-400/15 dark:text-brand-300">
          <TrendingUp className="size-3.5" /> 12.4%
        </span>
      </div>

      <HeroChart />

      <div className="mt-4 grid grid-cols-3 gap-2">
        {stats.map((s) => (
          <div key={s.label} className="rounded-[var(--radius-sm)] border border-border bg-surface-muted p-2.5">
            <p className="text-[10px] uppercase tracking-[0.05em] text-foreground-subtle">{s.label}</p>
            <p className="mono mt-1 text-sm font-medium text-foreground">{s.v}</p>
          </div>
        ))}
      </div>

      <div className="mt-4 space-y-2.5">
        {bars.map((b) => (
          <div key={b.c} className="flex items-center gap-3">
            <span className="w-24 shrink-0 truncate text-xs text-foreground-muted">{b.c}</span>
            <span className="h-2 flex-1 overflow-hidden rounded-full bg-surface-muted">
              <span className="block h-full rounded-full" style={{ width: b.w, backgroundColor: categoryColor(b.c) }} />
            </span>
            <span className="mono w-14 shrink-0 text-right text-xs text-foreground-muted">{b.v}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ page --- */

export function Landing() {
  const router = useRouter();
  const [demoBusy, setDemoBusy] = useState(false);
  const [demoError, setDemoError] = useState<string | null>(null);

  async function onTryDemo() {
    setDemoError(null);
    setDemoBusy(true);
    try {
      await demoLogin();
      router.replace("/dashboard");
    } catch (err) {
      setDemoError(demoErrorMessage(err));
      setDemoBusy(false);
    }
  }

  return (
    <MotionConfig reducedMotion="user">
      <div className="relative min-h-screen overflow-hidden bg-background text-foreground">
        {/* Ambient emerald glow — stronger in dark, restrained in light. */}
        <div aria-hidden className="pointer-events-none absolute inset-0 -z-10">
          <div
            className="absolute -top-40 left-1/2 h-[560px] w-[900px] -translate-x-1/2 opacity-[0.55] dark:opacity-90"
            style={{
              background:
                "radial-gradient(50% 50% at 50% 50%, color-mix(in oklab, var(--color-brand-400) 26%, transparent), transparent 70%)",
            }}
          />
          <div
            className="absolute right-[-10%] top-[38%] h-[420px] w-[420px] opacity-40 dark:opacity-70"
            style={{
              background:
                "radial-gradient(50% 50% at 50% 50%, color-mix(in oklab, var(--color-brand-500) 22%, transparent), transparent 70%)",
            }}
          />
        </div>

        {/* Header */}
        <header className="sticky top-0 z-30 border-b border-border bg-background/70 backdrop-blur-xl">
          <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 md:px-6">
            <BrandMark />
            <nav className="hidden items-center gap-7 text-sm text-foreground-muted md:flex">
              <a href="#how" className="transition-colors hover:text-foreground">How it works</a>
              <a href="#features" className="transition-colors hover:text-foreground">Features</a>
              <a href="#privacy" className="transition-colors hover:text-foreground">Privacy</a>
            </nav>
            <div className="flex items-center gap-2">
              <ThemeToggle />
              <button
                type="button"
                onClick={onTryDemo}
                disabled={demoBusy}
                className="hidden items-center gap-1.5 rounded-[var(--radius-sm)] px-3 py-2 text-sm font-medium text-foreground-muted transition-colors hover:text-foreground disabled:pointer-events-none disabled:opacity-60 sm:inline-flex"
              >
                <PlayCircle className="size-4" /> {demoBusy ? "Loading…" : "Try demo"}
              </button>
              <Link
                href="/login"
                className="inline-flex items-center gap-1.5 rounded-[var(--radius-sm)] bg-brand-700 px-4 py-2 text-sm font-semibold text-white transition-all hover:shadow-[var(--glow-brand-sm)] dark:bg-[image:var(--gradient-brand-vivid)] dark:text-[#04170d]"
              >
                Get started
              </Link>
            </div>
          </div>
        </header>

        {/* Hero */}
        <section className="relative">
          <div className="mx-auto grid max-w-6xl items-center gap-12 px-4 py-16 md:px-6 md:py-24 lg:grid-cols-2">
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, ease: EASE }}
            >
              <span className="inline-flex items-center gap-1.5 rounded-full border border-brand-400/40 bg-brand-50 px-3 py-1 text-xs font-medium text-brand-700 dark:bg-brand-400/10 dark:text-brand-300">
                <Sparkles className="size-3.5" /> For India&apos;s UPI generation
              </span>
              <h1 className="mt-5 font-display text-[2.6rem] font-semibold leading-[1.05] tracking-tight sm:text-6xl">
                All your spending,
                <br />
                <span className="bg-[image:linear-gradient(135deg,var(--color-brand-600),var(--color-brand-800))] bg-clip-text text-transparent dark:bg-[image:var(--gradient-brand-vivid)]">
                  in one clear view.
                </span>
              </h1>
              <p className="mt-6 max-w-md text-lg text-foreground-muted">
                SpendWise pulls together every payment across Paytm, GPay, PhonePe and your bank —
                so you finally know where your money goes.
              </p>
              <div className="mt-8 flex flex-wrap items-center gap-3">
                <Link
                  href="/login"
                  className="inline-flex items-center gap-2 rounded-[var(--radius-sm)] bg-brand-700 px-6 py-3 text-sm font-semibold text-white transition-all hover:-translate-y-0.5 hover:shadow-[var(--glow-brand-sm)] dark:bg-[image:var(--gradient-brand-vivid)] dark:text-[#04170d]"
                >
                  Get started <ArrowRight className="size-4" />
                </Link>
                <button
                  type="button"
                  onClick={onTryDemo}
                  disabled={demoBusy}
                  className="inline-flex items-center gap-2 rounded-[var(--radius-sm)] border border-border-strong bg-surface px-6 py-3 text-sm font-medium text-foreground transition-colors hover:border-brand-500 disabled:pointer-events-none disabled:opacity-60"
                >
                  <PlayCircle className="size-4" /> {demoBusy ? "Loading demo…" : "Try the demo"}
                </button>
                <a
                  href="#how"
                  className="inline-flex items-center gap-2 rounded-[var(--radius-sm)] px-2 py-3 text-sm font-medium text-foreground-muted transition-colors hover:text-foreground"
                >
                  See how it works
                </a>
              </div>
              {demoError && (
                <p
                  role="alert"
                  className="mt-3 max-w-md rounded-[var(--radius-sm)] border border-[var(--color-danger-border)] bg-[var(--color-danger-surface)] px-3 py-2 text-sm text-[var(--color-danger)]"
                >
                  {demoError}
                </p>
              )}
              <div className="mt-8 flex flex-wrap items-center gap-x-5 gap-y-2 text-sm text-foreground-subtle">
                {["Paytm", "GPay", "PhonePe", "SBI"].map((b) => (
                  <span key={b} className="flex items-center gap-1.5">
                    <Check className="size-4 text-brand-700 dark:text-brand-400" /> {b}
                  </span>
                ))}
              </div>
            </motion.div>

            <motion.div
              initial={{ opacity: 0, y: 28 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.7, delay: 0.15, ease: EASE }}
              className="lg:pl-8"
            >
              <ProductMock />
            </motion.div>
          </div>
        </section>

        {/* Stat strip */}
        <section className="border-y border-border bg-surface/40 backdrop-blur-sm">
          <div className="mx-auto grid max-w-6xl grid-cols-2 gap-px overflow-hidden px-4 md:grid-cols-4 md:px-6">
            {STATS.map((s) => (
              <div key={s.label} className="px-2 py-8 text-center">
                <p className="mono text-3xl font-medium text-brand-700 dark:text-brand-400">{s.v}</p>
                <p className="mt-1 text-xs uppercase tracking-[0.05em] text-foreground-subtle">{s.label}</p>
              </div>
            ))}
          </div>
        </section>

        {/* How it works */}
        <section id="how" className="mx-auto max-w-6xl px-4 py-16 md:px-6 md:py-24">
          <Reveal className="mx-auto max-w-2xl text-center">
            <p className="text-xs font-semibold uppercase tracking-[0.08em] text-brand-700 dark:text-brand-400">
              How it works
            </p>
            <h2 className="mt-2 font-display text-3xl font-semibold tracking-tight sm:text-4xl">
              From a payment SMS to a clear month
            </h2>
            <p className="mt-3 text-foreground-muted">Automatically — no receipts, no spreadsheets, no app-switching.</p>
          </Reveal>
          <div className="mt-12 grid gap-5 md:grid-cols-3">
            {STEPS.map((s, i) => (
              <Reveal key={s.title} delay={i * 0.08}>
                <div className="group h-full rounded-[var(--radius)] border border-border bg-surface p-6 shadow-[var(--shadow-sm)] transition-all hover:border-brand-400/40 hover:shadow-[var(--shadow-md),var(--glow-brand-sm)]">
                  <div className="flex size-11 items-center justify-center rounded-[var(--radius-sm)] bg-[image:var(--gradient-brand-vivid)] text-[#04170d] shadow-[var(--glow-brand-sm)]">
                    <s.icon className="size-5" />
                  </div>
                  <p className="mono mt-5 text-xs text-foreground-subtle">0{i + 1}</p>
                  <h3 className="mt-1 font-display text-lg font-semibold">{s.title}</h3>
                  <p className="mt-2 text-sm text-foreground-muted">{s.body}</p>
                </div>
              </Reveal>
            ))}
          </div>
        </section>

        {/* Features */}
        <section id="features" className="border-y border-border bg-surface-muted/40">
          <div className="mx-auto max-w-6xl px-4 py-16 md:px-6 md:py-24">
            <Reveal className="mx-auto max-w-2xl text-center">
              <p className="text-xs font-semibold uppercase tracking-[0.08em] text-brand-700 dark:text-brand-400">
                Everything in one place
              </p>
              <h2 className="mt-2 font-display text-3xl font-semibold tracking-tight sm:text-4xl">
                Built to spend wisely
              </h2>
              <p className="mt-3 text-foreground-muted">A complete picture, thoughtful nudges, and answers when you want them.</p>
            </Reveal>
            <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {FEATURES.map((f, i) => (
                <Reveal key={f.title} delay={(i % 3) * 0.06}>
                  <div className="group h-full rounded-[var(--radius)] border border-border bg-surface p-6 shadow-[var(--shadow-sm)] transition-all hover:-translate-y-0.5 hover:border-brand-400/40 hover:shadow-[var(--shadow-md),var(--glow-brand-sm)]">
                    <div className="flex size-10 items-center justify-center rounded-[var(--radius-sm)] bg-brand-50 text-brand-700 transition-colors group-hover:bg-brand-400/15 dark:bg-brand-400/10 dark:text-brand-300">
                      <f.icon className="size-5" />
                    </div>
                    <h3 className="mt-4 font-display font-semibold">{f.title}</h3>
                    <p className="mt-2 text-sm text-foreground-muted">{f.body}</p>
                  </div>
                </Reveal>
              ))}
            </div>
          </div>
        </section>

        {/* Category showcase */}
        <section className="mx-auto max-w-6xl px-4 py-16 md:px-6 md:py-24">
          <Reveal className="mx-auto max-w-2xl text-center">
            <p className="text-xs font-semibold uppercase tracking-[0.08em] text-brand-700 dark:text-brand-400">
              Colour-coded
            </p>
            <h2 className="mt-2 font-display text-3xl font-semibold tracking-tight sm:text-4xl">
              Twelve categories, always consistent
            </h2>
            <p className="mt-3 text-foreground-muted">Every transaction finds its place — so patterns jump out at a glance.</p>
          </Reveal>
          <Reveal className="mt-10 flex flex-wrap justify-center gap-2.5">
            {CATEGORIES.map((c) => (
              <span
                key={c}
                className="inline-flex items-center gap-2 rounded-[var(--radius-sm)] border px-3.5 py-1.5 text-sm font-medium"
                style={{
                  borderColor: `color-mix(in srgb, ${categoryColor(c)} 35%, transparent)`,
                  backgroundColor: `color-mix(in srgb, ${categoryColor(c)} 8%, transparent)`,
                }}
              >
                <span className="size-2.5 rounded-[3px]" style={{ backgroundColor: categoryColor(c) }} />
                <span className="text-foreground">{c}</span>
              </span>
            ))}
          </Reveal>
        </section>

        {/* Privacy */}
        <section id="privacy" className="border-y border-border bg-surface-muted/40">
          <div className="mx-auto flex max-w-4xl flex-col items-center px-4 py-16 text-center md:px-6 md:py-24">
            <Reveal className="flex flex-col items-center">
              <div className="flex size-12 items-center justify-center rounded-[var(--radius)] bg-[image:var(--gradient-brand-vivid)] text-[#04170d] shadow-[var(--glow-brand-md)]">
                <Lock className="size-6" />
              </div>
              <h2 className="mt-5 font-display text-3xl font-semibold tracking-tight sm:text-4xl">Your data stays yours</h2>
              <p className="mt-3 max-w-xl text-foreground-muted">
                Raw SMS messages are parsed on your device and never leave it — only structured fields
                sync. SpendWise is built around a single, clear consent step, aligned with the DPDP Act
                2023, with the right to erase your data at any time.
              </p>
            </Reveal>
          </div>
        </section>

        {/* Final CTA */}
        <section className="mx-auto max-w-6xl px-4 py-16 md:px-6 md:py-24">
          <Reveal>
            <div className="relative overflow-hidden rounded-[var(--radius-lg)] border border-brand-400/30 bg-[image:linear-gradient(135deg,var(--color-brand-700),var(--color-brand-900))] px-6 py-14 text-center shadow-[var(--shadow-lg),var(--glow-brand-md)] md:px-12">
              <div
                aria-hidden
                className="pointer-events-none absolute inset-0 opacity-60"
                style={{
                  background:
                    "radial-gradient(60% 120% at 50% -10%, color-mix(in oklab, var(--color-brand-300) 45%, transparent), transparent 60%)",
                }}
              />
              <div className="relative">
                <h2 className="font-display text-3xl font-semibold tracking-tight text-white sm:text-4xl">
                  Take control of your spending
                </h2>
                <p className="mx-auto mt-3 max-w-md text-brand-50">
                  Set up in minutes and see your first unified view of where your money goes.
                </p>
                <Link
                  href="/login"
                  className="mt-8 inline-flex items-center gap-2 rounded-[var(--radius-sm)] bg-white px-6 py-3 text-sm font-semibold text-brand-800 transition-transform hover:-translate-y-0.5"
                >
                  Get started free <ArrowRight className="size-4" />
                </Link>
              </div>
            </div>
          </Reveal>
        </section>

        {/* Footer */}
        <footer className="border-t border-border">
          <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-4 px-4 py-8 text-sm text-foreground-subtle md:flex-row md:px-6">
            <BrandMark size={24} />
            <p>Built for India&apos;s UPI generation · Privacy-first · DPDP 2023 aligned</p>
          </div>
        </footer>
      </div>
    </MotionConfig>
  );
}
