import Link from "next/link";
import {
  Wallet,
  Smartphone,
  Sparkles,
  BarChart3,
  ShieldCheck,
  Bell,
  Target,
  MessageSquare,
  ArrowRight,
  Check,
} from "lucide-react";
import { categoryColor } from "@/lib/categories";

const CATEGORIES = [
  "Shopping", "Food / Dine Out", "Groceries", "Travel", "Entertainment", "Subscriptions",
  "Sports & Fitness", "Cosmetics", "Medical", "Fees & Debt", "Transfers", "Miscellaneous",
];

const FEATURES = [
  { icon: BarChart3, title: "One unified dashboard", body: "Every UPI and bank transaction across Paytm, GPay, PhonePe and SBI, aggregated into a single clear view." },
  { icon: Sparkles, title: "Smart categorization", body: "Transactions are auto-sorted into 12 categories by an ML model that learns from your corrections." },
  { icon: Target, title: "Budgets & progress", body: "Set monthly limits per category and watch progress bars — with suggestions from your own history." },
  { icon: Bell, title: "Timely alerts", body: "Three-tier alerts warn you before you overspend, not after the damage is done." },
  { icon: MessageSquare, title: "Ask the assistant", body: "A built-in assistant answers questions about your spending, grounded in your real data." },
  { icon: ShieldCheck, title: "Private by design", body: "Raw SMS never leaves your phone. Only structured fields sync — DPDP Act 2023 aligned." },
];

const STEPS = [
  { icon: Smartphone, title: "Captured on device", body: "The Android app reads transaction SMS and parses the amount, payee and date — entirely on your phone." },
  { icon: Sparkles, title: "Categorized automatically", body: "Structured fields sync securely and are classified into spending categories in seconds." },
  { icon: BarChart3, title: "Visualized for you", body: "Open the web dashboard to see trends, budgets, and where every rupee went." },
];

function ProductMock() {
  const bars = [
    { c: "Food / Dine Out", w: "100%", v: "₹8,200" },
    { c: "Shopping", w: "78%", v: "₹6,400" },
    { c: "Groceries", w: "52%", v: "₹3,300" },
    { c: "Travel", w: "34%", v: "₹1,900" },
  ];
  const stats = [
    { label: "Income", v: "₹68,000" },
    { label: "Net", v: "₹45,200" },
    { label: "Budgets", v: "6 of 8" },
  ];
  return (
    <div className="w-full rounded-[var(--radius-lg)] border border-border bg-surface p-5 shadow-[var(--shadow-lg)]">
      <div className="mb-4 flex items-center justify-between">
        <div>
          <p className="text-xs text-foreground-subtle">Spent · This month</p>
          <p className="tnum text-2xl font-semibold text-foreground">₹22,800</p>
        </div>
        <span className="rounded-full bg-brand-50 px-2.5 py-1 text-xs font-medium text-brand-800">▼ 7%</span>
      </div>
      <div className="grid grid-cols-3 gap-2">
        {stats.map((s) => (
          <div key={s.label} className="rounded-[var(--radius-sm)] border border-border bg-surface-muted p-2.5">
            <p className="text-[10px] text-foreground-subtle">{s.label}</p>
            <p className="tnum mt-1 text-sm font-semibold text-foreground">{s.v}</p>
          </div>
        ))}
      </div>
      <div className="mt-4 space-y-2.5">
        {bars.map((b) => (
          <div key={b.c} className="flex items-center gap-2">
            <span className="w-24 shrink-0 truncate text-xs text-foreground-muted">{b.c}</span>
            <span className="h-2 flex-1 overflow-hidden rounded-full bg-surface-muted">
              <span className="block h-full rounded-full" style={{ width: b.w, backgroundColor: categoryColor(b.c) }} />
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

export function Landing() {
  return (
    <div className="min-h-screen bg-background text-foreground">
      {/* Header */}
      <header className="sticky top-0 z-30 border-b border-border bg-background/80 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 md:px-6">
          <Link href="/" className="flex items-center gap-2">
            <span className="flex size-8 items-center justify-center rounded-lg bg-brand-600 text-white">
              <Wallet className="size-5" />
            </span>
            <span className="text-lg font-semibold tracking-tight">SpendWise</span>
          </Link>
          <nav className="hidden items-center gap-6 text-sm text-foreground-muted md:flex">
            <a href="#features" className="hover:text-foreground">Features</a>
            <a href="#how" className="hover:text-foreground">How it works</a>
            <a href="#privacy" className="hover:text-foreground">Privacy</a>
          </nav>
          <div className="flex items-center gap-2">
            <Link href="/login" className="rounded-[var(--radius-sm)] px-3 py-2 text-sm font-medium text-foreground-muted hover:text-foreground">
              Sign in
            </Link>
            <Link href="/login" className="rounded-[var(--radius-sm)] bg-brand-700 px-4 py-2 text-sm font-medium text-white hover:bg-brand-800">
              Get started
            </Link>
          </div>
        </div>
      </header>

      {/* Hero */}
      <section className="relative overflow-hidden">
        <div className="pointer-events-none absolute inset-x-0 top-0 h-[420px] bg-gradient-to-b from-brand-50 to-transparent" />
        <div className="relative mx-auto grid max-w-6xl items-center gap-12 px-4 py-16 md:px-6 md:py-24 lg:grid-cols-2">
          <div className="animate-in fade-in slide-in-from-bottom-4 duration-700">
            <span className="inline-flex items-center gap-1.5 rounded-full border border-brand-200 bg-brand-50 px-3 py-1 text-xs font-medium text-brand-800">
              <Sparkles className="size-3.5" /> For India&apos;s UPI generation
            </span>
            <h1 className="mt-5 font-display text-4xl font-semibold leading-[1.1] tracking-tight text-foreground sm:text-5xl">
              All your spending,<br />in one clear view.
            </h1>
            <p className="mt-5 max-w-md text-lg text-foreground-muted">
              SpendWise pulls together every payment across Paytm, GPay, PhonePe and your bank —
              so you finally know where your money goes.
            </p>
            <div className="mt-8 flex flex-wrap items-center gap-3">
              <Link href="/login" className="inline-flex items-center gap-2 rounded-[var(--radius-sm)] bg-brand-700 px-5 py-3 text-sm font-medium text-white hover:bg-brand-800">
                Get started <ArrowRight className="size-4" />
              </Link>
              <a href="#how" className="inline-flex items-center gap-2 rounded-[var(--radius-sm)] border border-border-strong bg-surface px-5 py-3 text-sm font-medium text-foreground hover:bg-surface-muted">
                See how it works
              </a>
            </div>
            <div className="mt-8 flex flex-wrap items-center gap-x-5 gap-y-2 text-sm text-foreground-subtle">
              {["Paytm", "GPay", "PhonePe", "SBI"].map((b) => (
                <span key={b} className="flex items-center gap-1.5">
                  <Check className="size-4 text-brand-600" /> {b}
                </span>
              ))}
            </div>
          </div>
          <div className="animate-in fade-in slide-in-from-bottom-6 duration-1000 lg:pl-8">
            <ProductMock />
          </div>
        </div>
      </section>

      {/* How it works */}
      <section id="how" className="mx-auto max-w-6xl px-4 py-16 md:px-6 md:py-24">
        <div className="mx-auto max-w-2xl text-center">
          <h2 className="font-display text-3xl font-semibold tracking-tight">How it works</h2>
          <p className="mt-3 text-foreground-muted">From a payment SMS to a clear picture of your month — automatically.</p>
        </div>
        <div className="mt-12 grid gap-6 md:grid-cols-3">
          {STEPS.map((s, i) => (
            <div key={s.title} className="rounded-[var(--radius-lg)] border border-border bg-surface p-6 shadow-[var(--shadow-sm)]">
              <div className="flex size-11 items-center justify-center rounded-[var(--radius-sm)] bg-brand-50 text-brand-700">
                <s.icon className="size-5" />
              </div>
              <p className="mt-4 text-xs font-medium text-foreground-subtle">Step {i + 1}</p>
              <h3 className="mt-1 text-lg font-semibold">{s.title}</h3>
              <p className="mt-2 text-sm text-foreground-muted">{s.body}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Features */}
      <section id="features" className="border-y border-border bg-surface-muted/50">
        <div className="mx-auto max-w-6xl px-4 py-16 md:px-6 md:py-24">
          <div className="mx-auto max-w-2xl text-center">
            <h2 className="font-display text-3xl font-semibold tracking-tight">Everything you need to spend wisely</h2>
            <p className="mt-3 text-foreground-muted">A complete picture, thoughtful nudges, and answers when you want them.</p>
          </div>
          <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map((f) => (
              <div key={f.title} className="rounded-[var(--radius-lg)] border border-border bg-surface p-6 shadow-[var(--shadow-sm)]">
                <div className="flex size-10 items-center justify-center rounded-[var(--radius-sm)] bg-brand-50 text-brand-700">
                  <f.icon className="size-5" />
                </div>
                <h3 className="mt-4 font-semibold">{f.title}</h3>
                <p className="mt-2 text-sm text-foreground-muted">{f.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Category showcase */}
      <section className="mx-auto max-w-6xl px-4 py-16 md:px-6 md:py-24">
        <div className="mx-auto max-w-2xl text-center">
          <h2 className="font-display text-3xl font-semibold tracking-tight">Twelve categories, colour-coded</h2>
          <p className="mt-3 text-foreground-muted">Every transaction finds its place — so patterns jump out at a glance.</p>
        </div>
        <div className="mt-10 flex flex-wrap justify-center gap-2.5">
          {CATEGORIES.map((c) => (
            <span
              key={c}
              className="inline-flex items-center gap-2 rounded-full border px-3.5 py-1.5 text-sm font-medium"
              style={{
                color: categoryColor(c),
                borderColor: `color-mix(in srgb, ${categoryColor(c)} 35%, transparent)`,
                backgroundColor: `color-mix(in srgb, ${categoryColor(c)} 8%, transparent)`,
              }}
            >
              <span className="size-2.5 rounded-full" style={{ backgroundColor: categoryColor(c) }} />
              <span className="text-foreground">{c}</span>
            </span>
          ))}
        </div>
      </section>

      {/* Privacy */}
      <section id="privacy" className="border-y border-border bg-surface-muted/50">
        <div className="mx-auto flex max-w-4xl flex-col items-center px-4 py-16 text-center md:px-6 md:py-24">
          <div className="flex size-12 items-center justify-center rounded-full bg-brand-100 text-brand-700">
            <ShieldCheck className="size-6" />
          </div>
          <h2 className="mt-5 font-display text-3xl font-semibold tracking-tight">Your data stays yours</h2>
          <p className="mt-3 max-w-xl text-foreground-muted">
            Raw SMS messages are parsed on your device and never leave it — only structured fields sync.
            SpendWise is built around a single, clear consent step, aligned with the DPDP Act 2023, with
            the right to erase your data at any time.
          </p>
        </div>
      </section>

      {/* Final CTA */}
      <section className="mx-auto max-w-6xl px-4 py-16 md:px-6 md:py-24">
        <div className="overflow-hidden rounded-[var(--radius-lg)] bg-brand-700 px-6 py-14 text-center text-white shadow-[var(--shadow-lg)] md:px-12">
          <h2 className="font-display text-3xl font-semibold tracking-tight">Take control of your spending</h2>
          <p className="mx-auto mt-3 max-w-md text-brand-50">Set up in minutes and see your first unified view of where your money goes.</p>
          <Link href="/login" className="mt-8 inline-flex items-center gap-2 rounded-[var(--radius-sm)] bg-white px-6 py-3 text-sm font-semibold text-brand-800 hover:bg-brand-50">
            Get started free <ArrowRight className="size-4" />
          </Link>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-border">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-4 px-4 py-8 text-sm text-foreground-subtle md:flex-row md:px-6">
          <div className="flex items-center gap-2">
            <span className="flex size-6 items-center justify-center rounded-md bg-brand-600 text-white">
              <Wallet className="size-3.5" />
            </span>
            <span className="font-medium text-foreground">SpendWise</span>
          </div>
          <p>Built for India&apos;s UPI generation · Privacy-first · DPDP 2023 aligned</p>
        </div>
      </footer>
    </div>
  );
}
