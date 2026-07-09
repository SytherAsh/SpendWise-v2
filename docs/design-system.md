# SpendWise Design System — "Emerald Command Center"

> **Status:** Draft v1 for approval. This document is the source of truth for the SpendWise
> visual identity and product-UX standards. It **elevates** the existing emerald token
> architecture (`frontend/src/styles/globals.css`, `components/ui`, `components/shared`,
> Recharts + `lib/chart-theme.ts`) — it does not replace the engineering. All backend
> services, `lib/apiClient.ts`, `lib/useApi.ts`/SWR, `AuthGuard`, and the module architecture
> are reused unchanged, per [CLAUDE.md](../CLAUDE.md) (reuse, don't rewrite).
>
> **Scope of this redesign:** Next.js **landing page + web app + admin portal**. The Android
> app inherits the *design language* (color, type, motion, component intent) in a later pass
> as a native Kotlin implementation — not shared code.

---

## 1. Brand Identity

**SpendWise is a green-first financial command center.** Where Paytm owns blue, SpendWise owns
a **vibrant, living emerald** — the color of growth, wealth, and momentum — rendered with the
precision of an analytics terminal. The product should feel **bold, energetic, intelligent, and
trustworthy**: confident enough to be memorable, disciplined enough to be a place you trust with
money.

**Personality:** Bold & energetic. Trust through confidence, not through timidity.

**The three brand pillars**

| Pillar | Meaning | How it shows up |
|---|---|---|
| **Alive** | Money is motion; the app feels responsive and kinetic | Glow, gradients, count-ups, chart draw-in |
| **Precise** | This is a serious analytics tool, not a toy | Sharp corners, tabular mono numerals, dense hierarchy |
| **Green** | One unmistakable identity, everywhere | Emerald as the single accent; green-tinted neutrals |

**Signature DNA (what makes a screen recognizably SpendWise at a glance):**
1. **Vibrant emerald glow** on key surfaces (hero cards, primary CTAs, active nav, the "line" of the hero chart).
2. **Green-tinted near-black** in dark mode — not neutral gray, a deep green-black command center.
3. **Mono numerals** — every figure reads in a tabular monospace, a "financial terminal" tell.
4. **Crisp corners + hairline borders** — precise, engineered, dense.
5. **The mark** — an upward emerald motion glyph paired with a grotesk wordmark.

### 1.1 Logo & Mark

- **Mark:** an abstract **upward-motion "S"** — a stylized ascending stroke that reads as both an
  *S* and a rising chart line, built as SVG with an emerald gradient (`brand-400 → brand-600`) and
  an optional soft glow. Works at 16px (favicon, avatar fallback) through hero scale.
- **Wordmark:** `SpendWise` set in the display face (Clash Display, 600), with the **"W" or the
  dot accent** carrying `brand-500`. Tight tracking.
- **Lockups:** mark-left horizontal (nav, default), stacked (landing hero, splash), mark-only
  (favicon, mobile collapsed nav, avatar fallback).
- **Clear space:** ≥ the mark's cap-height on all sides. **Never** recolor the mark off-emerald,
  add a second accent, or place it on a low-contrast ground.
- **Logo behavior:** clicking the logo **always navigates to the landing page** (marketing root),
  from anywhere in the app, admin, or auth.

---

## 2. Design Principles

1. **Green is the only accent.** Semantic colors (warning amber, danger red) exist for *meaning*
   only and never compete for the accent slot. If everything is highlighted, nothing is — spend the
   emerald where it drives action or signals health.
2. **Density with hierarchy.** We show a lot, but never a wall. Every dense surface has one clear
   focal summary before the detail (summary-before-detail).
3. **Numbers are the hero.** Money figures get the mono face, tabular alignment, and the most
   visual weight. Charts and figures get the same craft as typography.
4. **Motion has a job.** Every animation communicates (a value changing, data arriving, a state
   transition). No motion for decoration; all motion respects `prefers-reduced-motion`.
5. **Glow is earned.** The glow signals importance and interactivity. Reserve it for primary
   actions, active states, AI surfaces, and hero data — never as ambient wallpaper.
6. **Dark and light are equals.** Both are designed, not inverted. The glow is the star in dark;
   in light it becomes a restrained tint + ring.
7. **Accessible by construction.** AA contrast is a gate, not a nice-to-have. Bright greens are
   *display-only*; text-bearing greens are the darker steps. State is never color-alone.

---

## 3. Color System

The core discipline: **a vibrant "signal" green for light/glow/accent, and a deeper "text-safe"
green for text and labels on light grounds.** Bright emeralds (`brand-300/400/500`) fail WCAG as
text on white; the darker steps (`brand-700/800`) are the text-bearing greens. Dark mode inverts
this — the bright steps become text-safe on the deep green-black surface.

### 3.1 Brand ramp (emerald)

| Token | Hex | Role |
|---|---|---|
| `brand-50`  | `#E7FCEF` | tint wash, hover fills (light) |
| `brand-100` | `#C6F7DA` | subtle fills, chips |
| `brand-200` | `#92EFB9` | borders on tinted surfaces |
| `brand-300` | `#55E390` | **text-safe on dark**, chart accents on dark |
| `brand-400` | `#24D46E` | **SIGNAL green** — glow, gradients, dark-mode primary |
| `brand-500` | `#0FBF5E` | bright primary, gradient origin |
| `brand-600` | `#06A052` | **interactive DEFAULT** (buttons, active nav on light) |
| `brand-700` | `#067A45` | **text-safe on light** (labels, links ≥ AA) |
| `brand-800` | `#0A6338` | strong text-bearing fills |
| `brand-900` | `#0B4A2C` | deepest, on-dark washes |

> **Contrast rule of thumb:** on **light** grounds use `brand-700`+ for text and `brand-600` for
> solid CTA fills (white label). On **dark** grounds use `brand-300/400` for text/lines. Never set
> body text in `brand-400/500`. Validate every text pairing at build time.

### 3.2 Neutrals — chosen, green-biased

Neutrals carry a faint green undertone so they read *chosen*, not defaulted. Light keeps the warm
Mercury base of the current build, nudged green; dark is a deep green-black.

**Light**
| Token | Hex |
|---|---|
| `--background` | `#F5F7F3` |
| `--surface` | `#FFFFFF` |
| `--surface-muted` | `#EDF1EC` |
| `--foreground` | `#0E1611` |
| `--foreground-muted` | `#55605A` |
| `--foreground-subtle` | `#86928B` |
| `--border` | `#E2E7DF` |
| `--border-strong` | `#D2D9CF` |

**Dark (signature command center)**
| Token | Hex |
|---|---|
| `--background` | `#08100B` |
| `--surface` | `#0F1813` |
| `--surface-muted` | `#14201A` |
| `--surface-elevated` | `#1A281F` |
| `--foreground` | `#EAF3EE` |
| `--foreground-muted` | `#9DB0A6` |
| `--foreground-subtle` | `#6C7F75` |
| `--border` | `#22322A` |
| `--border-strong` | `#2E4137` |

### 3.3 Semantic (meaning-only, never the accent)

Warning and danger are distinct hues. Positive/healthy mostly **folds into brand emerald** — there
is no separate "success" hue competing with the brand — **except money direction**, which is a
deliberate exception: **updated 2026-07-09, at the user's explicit request.** Wherever a figure's
sign is meaningful and both directions can appear in the same view (a transaction row, a "money
spent" + "money received" pair), credited/received money is green and debited/spent money is
`--danger` red — not neutral ink. Before this change, spend was neutral and only credit was green;
users found that gave too little contrast to tell the two apart at a glance ("everything is green").
This does **not** apply to spend-only surfaces with nothing to contrast against — a category tile,
a category deep-dive's "Spent" figure, a budget total — those stay neutral/brand as before; painting
every spend figure red when there's no credit alongside it doesn't add information, it just makes
red ambient (see the rule this whole section opens with).

| Token | Light | Dark | Use |
|---|---|---|---|
| `--warning` | `#B45309` | `#F5B547` | approaching limit, stale data |
| `--warning-surface` | `#FFF7ED` | `#2A2011` | |
| `--danger` | `#DC2626` | `#FF6B6B` | over-budget, destructive, **money spent (in a mixed credit/debit view)** |
| `--danger-surface` | `#FEF2F2` | `#2A1414` | |
| `--positive` | `--brand-700` | `--brand-300` | **money received/credited (in a mixed credit/debit view)** — a theme-flipping alias so inline (non-Tailwind) color values stay text-safe on both grounds, same reasoning as `--chart-line`/`--chart-line-strong` |

Semantic colors always ship with an **icon + label**, never color alone.

### 3.4 Category palette (12 fixed categories)

Unchanged from the current build and **dataviz-validated** (passes lightness/chroma/CVD-floor;
the sub-3:1 slots are relieved by the secondary encoding the UI always ships — chip icon+label,
the ranked list beside every donut, direct slice labels, and a table view).

`shopping #2a78d6` · `food #eb6834` · `groceries #16a34a` · `travel #0891b2` ·
`entertainment #7c5cff` · `subscriptions #4f46e5` · `sports #84a800` · `cosmetics #e0559d` ·
`medical #1baf7a` · `fees #e34948` · `transfers #eda100` · `misc #8b8b82`

> Category greens (`groceries`, `medical`) intentionally differ from the *brand* emerald; brand
> green is reserved for interactive/health signaling, category greens for identity in charts.
> Assign categorical hues in **fixed order, never cycled**; a 13th category folds into `misc`.

### 3.5 Glow tokens (the signature)

```
--glow-brand-sm: 0 0 0 1px color-mix(in oklab, var(--brand-400) 30%, transparent),
                 0 0 16px -4px color-mix(in oklab, var(--brand-400) 45%, transparent);
--glow-brand-md: 0 0 24px -2px color-mix(in oklab, var(--brand-400) 40%, transparent);
--gradient-brand: linear-gradient(135deg, var(--brand-500), var(--brand-700));
--gradient-brand-vivid: linear-gradient(135deg, var(--brand-400), var(--brand-600));
```
Glow is **stronger in dark, restrained in light** (reduce alpha ~40% on light).

---

## 4. Typography

Three roles, all self-hosted via `next/font` (no CDN — respects free-tier + CSP):

| Role | Family | Fallback | Used for |
|---|---|---|---|
| **Display** | **Bricolage Grotesque** (`next/font/google`) | Inter, system grotesk | headings, hero titles, wordmark |
| **Body / UI** | **Inter** | system-ui | all running text, labels, controls |
| **Numeric** | **JetBrains Mono** (`next/font/google`) | ui-monospace | **every numeral** — KPIs, tables, chart ticks, money |

> **Shipped display face:** Bricolage Grotesque — an expressive, characterful grotesk available on
> Google Fonts, so it loads via `next/font` with no font-binary/CSP concerns. Clash Display (Fontshare)
> was the original pitch but isn't on Google Fonts; it remains an optional future swap via
> `next/font/local` if we vendor the `.woff2` files. Wired in `app/layout.tsx` as `--font-bricolage`
> → `--font-display`.

**The mono-numeral rule** is a core identity move: all figures render in the tabular mono
(`font-variant-numeric: tabular-nums`), giving the "financial terminal" feel and column alignment.
Headings/labels/prose stay in Clash/Inter.

### 4.1 Type scale (dense-but-legible)

| Token | Size / line-height | Face / weight | Tracking |
|---|---|---|---|
| `display-xl` | 44 / 1.05 | Clash 600 | −0.01em |
| `display-l` | 34 / 1.10 | Clash 600 | −0.01em |
| `h1` | 26 / 1.15 | Clash 600 | −0.005em |
| `h2` | 20 / 1.20 | Clash 600 / Inter 700 | 0 |
| `h3` | 16 / 1.30 | Inter 600 | 0 |
| `body` | 14 / 1.50 | Inter 400 | 0 |
| `body-sm` | 13 / 1.45 | Inter 400 | 0 |
| `label` | 11–12 / 1.30 | Inter 600 **UPPERCASE** | 0.06em |
| `data-xl` (KPI) | 30–34 / 1.05 | Mono 500 tnum | 0 |
| `data` | 14 / 1.40 | Mono 450 tnum | 0 |
| `data-sm` | 12 / 1.30 | Mono 450 tnum | 0 |

Body default is **14px** (dense app standard). Headings use `text-wrap: balance`. Keep prose
measure near 65ch.

---

## 5. Spacing, Shape & Elevation

- **Spacing scale (4px base):** 2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64. Layout via flex/grid `gap`,
  never per-element margins. Dense surfaces use 12–16 gutters; page rhythm uses 24–32.
- **Radius (crisp):** `--radius-sm: 0.375rem` (6px — inputs, chips, buttons), `--radius: 0.5rem`
  (8px — cards, panels), `--radius-lg: 0.75rem` (12px — modals, hero cards). Sharper than the old
  0.75rem default — the "serious analytics" tell.
- **Elevation:** two systems that stack —
  - *Structural shadows* (subtle, for layering): `shadow-sm/md/lg` as today, tuned darker on dark.
  - *Glow* (brand signal): applied to primary/active/AI/hero only, via the glow tokens.
- **Borders:** hairline `--border` is the default separator (dense grids lean on borders over
  shadow). `--border-strong` for emphasized dividers.

---

## 6. Component Library

Built on the existing `components/ui` primitives (`button`, `input`, `badge`, `skeleton`, `dialog`,
`sheet`, `tabs`, `popover`, `tooltip`, `category-chip`). Elevate; keep the APIs.

### 6.1 Buttons
| Variant | Light | Dark | Use |
|---|---|---|---|
| **Primary** | `brand-600` fill, white label, `glow-brand-sm` on hover | `gradient-brand-vivid` fill, ink label, glow | main action, 1 per view |
| **Secondary** | surface fill, `border-strong`, `foreground` label | `surface-elevated`, border | secondary action |
| **Ghost** | transparent, `brand-700` label, tint hover | transparent, `brand-300` label | tertiary, toolbars |
| **Danger** | `danger` fill / outline | same | destructive (confirm required) |

Sizes: sm (28h), md (36h, default), lg (44h). Radius `sm`. Focus = 2px `--ring` (brand) offset ring.
Hover lifts 1px + glow; active presses. Disabled = 40% + no glow. Loading = inline spinner, label
stays. Icon buttons are square with the same states.

### 6.2 Cards & panels
- **Base card:** `surface`, hairline border, `radius`, `shadow-sm`. Header (label + optional action),
  body, optional footer.
- **Hero/stat card:** may carry `glow-brand-sm` and a faint corner gradient; KPI in `data-xl` mono
  with a delta chip (▲ emerald / ▼ danger) and an optional sparkline.
- **AI card:** signature treatment — see §11.

### 6.3 `StatTile` (KPI)
Label (uppercase) · big mono value · delta chip · optional sparkline endpoint dot. The workhorse of
the dashboard. Summary-before-detail: tiles row sits above every detail grid.

### 6.4 Tables (dense, financial)
- Sticky header in `surface-muted`, uppercase `label` columns, hairline row dividers, zebra optional
  via `surface-muted` at 50%.
- **Amount column:** right-aligned mono tabular; debits `--danger` red, credits `--positive` (§3.3) — updated 2026-07-09, was "debits neutral ink" before.
- Row hover: `surface-muted` + left 2px brand edge. Row density: comfortable (44) / compact (36) toggle.
- Sortable headers (caret), multi-select checkboxes, sticky bulk-action bar, pagination footer
  (mono page numbers), CSV/export action in the toolbar.
- **Mobile:** table collapses to stacked cards (label:value pairs), never a horizontal-scroll page.

### 6.5 Forms & filters
- Inputs: 36h, `radius-sm`, hairline border, `surface` fill; focus = brand ring + faint glow.
  Error state = `danger` border + helper text with icon. Labels above, uppercase-optional.
- Filter bar: **one row above the content** (dataviz rule) — date range (`DateRangePicker`),
  dimension selects (`popover`), search, and a "clear filters" ghost. Active filters render as
  removable chips.
- Controls reuse: `input`, `popover`, `tabs`, `DateRangePicker`, `category-chip`.

### 6.6 Chips & badges
- **Category chip:** color dot/icon (category color) + label — the secondary encoding that keeps the
  12-color palette accessible. Never color-only.
- **Status badge:** pill, semantic surface + icon + label (Healthy=brand, Approaching=warning,
  Over=danger, Stale=warning).
- **Delta chip:** ▲/▼ + mono %, emerald up / danger down.

### 6.7 Overlays
- **Dialog / confirm:** `radius-lg`, `surface`, backdrop blur+dim, glow accent on the primary action.
  Destructive confirms name the consequence in the button ("Delete 12 transactions").
- **Sheet:** right/bottom slide for filters, detail, quick-add (`QuickAddTransaction`).
- **Toast:** bottom-right stack, `surface-elevated`, left 2px semantic edge, icon + message + optional
  action; success uses brand, auto-dismiss with a thin progress line.
- **Tooltip / popover:** dark `surface-elevated` chip, `data-sm` for numbers.

### 6.8 States (system-wide)
- **Loading:** content-shaped **shimmer skeletons** with a subtle emerald sheen (`skeleton.tsx`
  elevated). Global route transitions show a top progress bar.
- **Empty:** branded illustrative empty (mark motif, one-line explanation, a primary CTA) — e.g.
  "No transactions yet — connect your Android app to start syncing."
- **Error:** contained error card (icon, what went wrong, how to fix, retry action) — never a raw
  stack. 401 handled silently by `apiClient` refresh.
- **Success:** toast + inline confirmation; optimistic UI where safe (SWR revalidate).

---

## 7. Navigation System

**Model: top bar (primary) + contextual per-section sidebar.** This replaces today's
sidebar-primary shell — reworking `AppShell`, `TopBar`, `Sidebar`.

### 7.1 Top bar (global, persistent)
Left→right: **logo lockup** (→ landing) · primary nav (Dashboard · Transactions · Analytics ·
Planning · Assistant) with clear active state (emerald underline/pill + glow) · **flex spacer** ·
**global search / ⌘K** · **notifications bell** (badge count) · **avatar menu**.

### 7.2 Contextual sidebar (per section)
Sections with sub-views (Analytics → Overview/Categories/Trends/Export; Settings → Profile/
Preferences/Budgets/Devices/Security) get a **left contextual sidebar** with the sub-nav; simple
sections (Dashboard) render full-width with no sidebar. Sidebar is collapsible to an icon rail
(density for power users); active item shows the emerald edge + tint.

### 7.3 Breadcrumbs
Under the top bar on **deep pages** (e.g. `Analytics › Categories › Food`), mono/label styling,
last crumb non-link. Not shown on top-level pages.

### 7.4 Avatar / user menu (top-right, always available)
Avatar (photo or mark-fallback initials) → menu: **Profile · Settings · Help · Theme (toggle) ·
Log out**. Theme toggle is inline (sun/moon), persists choice.

### 7.5 Command palette / global search (⌘K)
Elevate the existing `CommandPalette.tsx`: fuzzy search across **transactions, pages, actions,
categories**; recent + suggested; keyboard-first. Opens from the top-bar search and ⌘K anywhere.

### 7.6 Notifications center
Bell → dropdown/panel of alerts from the Alerts module (budget overspend, EMI due, ML retrain,
sync status). Grouped by recency, unread dot, "mark all read", deep-links to the source. Read-only
consumer of existing alert data.

### 7.7 Responsive / mobile nav
Top bar collapses: logo + search + avatar remain; primary nav moves to a **bottom tab bar** (mobile)
or a hamburger `Sheet`. Contextual sidebar becomes a `Sheet`. Touch targets ≥ 44px.

---

## 8. Theme Guidelines

- **Mechanism:** `data-theme` on `<html>` (already the seam in `globals.css`) driving Tailwind's
  `dark:`. Add `next-themes`.
- **Default:** **follow system** on first visit; **persist** the user's manual toggle thereafter.
- **Parity:** every token has a designed value in both themes (§3.2). Components read tokens, never
  hard-coded hex. Charts pick **dark-specific** steps, not an auto-flip.
- **Glow discipline:** dark = full glow; light = reduced-alpha tint + ring so it never muddies.

---

## 9. Motion Guidelines

- **Durations:** 120ms (micro/hover), 200ms (default enter/exit), 320ms (page/section), 600–900ms
  (count-ups, chart draw-in).
- **Easing:** `cubic-bezier(0.2, 0.8, 0.2, 1)` (confident spring-out) for enters; ease-in for exits.
- **Signature motions:** KPI **count-up** on load, **chart draw-in** (line trace + area fade),
  **hover glow** on interactive cards/buttons, **route transition** (fade+rise 8px + top progress),
  toast slide, skeleton shimmer.
- **Discipline:** motion communicates only; nothing blocks input; stagger lists ≤ 5 items.
- **`prefers-reduced-motion`:** all of the above collapse to instant/opacity-only (already globally
  guarded in `globals.css`).

---

## 10. Dashboard & Chart Design Language

### 10.1 Dashboard composition
`Summary → detail`: a **KPI tile row** (spend this month, vs last, top category, budget health) with
count-ups and sparklines → a **hero chart** (glowing gradient area of spend-over-time) → a grid of
category breakdown (donut + ranked list), budgets progress, recent transactions, and an AI insight
card. Dense but with one clear focal path.

### 10.2 Chart language (Recharts + `chart-theme.ts`)
**Applied by purpose:**
- **Hero (dashboard):** *glowing gradient area* — line in `brand-500` with a soft glow, area fill
  `brand-500 → transparent` vertical gradient, emphasized **endpoint dot**, faint grid, mono axis
  ticks. The signature chart.
- **Analytics (comparison/density):** *crisp/precise* — thin lines, small multiples, tight grid,
  minimal fill — where reading exact values and comparing series matters.
- **Category:** donut + **ranked list beside it** (never donut alone), direct slice labels, table
  view toggle.

**Shared rules (dataviz method):** one y-axis ever (never dual-axis); recessive grid/axes in token
ink; ≥2 series always get a legend and ≤4 also direct-labeled; text wears text tokens, not series
color; 2px lines, ≥8px markers, 4px rounded data-ends; hover crosshair+tooltip on line/area, per-mark
tooltip on bar/dot; a table view exists for every chart; dark-mode steps validated against the dark
surface. Category color follows the entity, never its rank.

---

## 11. AI Feature Presentation (differentiator)

AI surfaces (the **Assistant/chatbot** and **recommendation one-liners**) share a **signature
treatment** so intelligence feels consistent wherever it appears:
- **AI accent:** a `gradient-brand-vivid` hairline/edge or sparkle mark, faint glow, and a small
  "AI" glyph. Distinct from ordinary cards but still emerald (not a second brand color).
- **Insight cards:** appear inline on dashboard/category/budget — one-line recommendation, the AI
  glyph, and an optional "why" expander. Gentle, never alarmist.
- **Assistant page/panel:** full chat with the AI treatment on the composer and streamed responses;
  message bubbles use `surface` (user) vs AI-edged (assistant); numbers in mono.
- **Honesty:** AI content is clearly attributed; the LLM stays provider-abstracted per CLAUDE.md
  (no SDK in UI — calls go through existing endpoints).

---

## 12. Responsive Design Rules

- **Breakpoints:** `sm 640 · md 768 · lg 1024 · xl 1280 · 2xl 1536`.
- **Grids:** KPI row 4→2→1; content grids collapse to single column below `lg`.
- **Tables → stacked cards** below `md` (§6.4). Contextual sidebar → `Sheet`. Primary nav → bottom
  tabs/hamburger.
- **Charts:** maintain aspect ratio, drop to fewer ticks; hero chart stays full-bleed width.
- **No horizontal page scroll ever** — wide content scrolls inside its own `overflow-x:auto` container.
- **Touch:** targets ≥ 44px; hover-only affordances get tap equivalents.

---

## 13. Accessibility Guidelines

- **Target:** WCAG 2.1 **AA**.
- **Contrast:** body text ≥ 4.5:1, large text/UI ≥ 3:1. **Bright greens are display-only**; text
  uses `brand-700`+ on light, `brand-300/400` on dark. Validate every text pairing at build.
- **State never color-alone:** always pair with icon/label/shape (delta arrows, status icons,
  category chips).
- **Focus:** visible 2px brand ring on every interactive element (already global).
- **Keyboard:** full operability — nav, ⌘K, dialogs (focus trap + Esc), menus, tables. Documented
  shortcuts.
- **Motion:** `prefers-reduced-motion` honored globally.
- **Semantics:** landmarks, labeled controls, `aria-live` for toasts/async, chart data available as
  a table.
- **Glow caution:** glow is decorative — it never carries the only signal for state or focus.

---

## 14. Tailwind Design Tokens (v4, `@theme inline`)

Extends the existing `globals.css` token seam. Illustrative (final hexes per §3):

```css
:root {
  color-scheme: light;
  /* neutrals (green-biased, light) */
  --background:#F5F7F3; --surface:#FFFFFF; --surface-muted:#EDF1EC;
  --foreground:#0E1611; --foreground-muted:#55605A; --foreground-subtle:#86928B;
  --border:#E2E7DF; --border-strong:#D2D9CF; --ring:#06A052;
  /* brand emerald ramp */
  --color-brand-50:#E7FCEF; --color-brand-100:#C6F7DA; --color-brand-200:#92EFB9;
  --color-brand-300:#55E390; --color-brand-400:#24D46E; --color-brand-500:#0FBF5E;
  --color-brand-600:#06A052; --color-brand-700:#067A45; --color-brand-800:#0A6338;
  --color-brand-900:#0B4A2C;
  /* semantic */
  --color-warning:#B45309; --color-danger:#DC2626; --color-positive:var(--color-brand-700);
  /* shape */
  --radius-sm:.375rem; --radius:.5rem; --radius-lg:.75rem;
  /* glow */
  --glow-brand-sm:0 0 0 1px color-mix(in oklab,var(--color-brand-400) 30%,transparent),
                  0 0 16px -4px color-mix(in oklab,var(--color-brand-400) 45%,transparent);
  --glow-brand-md:0 0 24px -2px color-mix(in oklab,var(--color-brand-400) 40%,transparent);
  --gradient-brand-vivid:linear-gradient(135deg,var(--color-brand-400),var(--color-brand-600));
}
:root[data-theme="dark"]{
  color-scheme:dark;
  --background:#08100B; --surface:#0F1813; --surface-muted:#14201A; --surface-elevated:#1A281F;
  --foreground:#EAF3EE; --foreground-muted:#9DB0A6; --foreground-subtle:#6C7F75;
  --border:#22322A; --border-strong:#2E4137; --ring:#24D46E;
}
/* fonts wired via next/font CSS vars in layout.tsx */
@theme inline{
  --font-display:var(--font-clash),var(--font-space-grotesk),ui-sans-serif,system-ui,sans-serif;
  --font-sans:var(--font-inter),ui-sans-serif,system-ui,sans-serif;
  --font-mono:var(--font-jetbrains-mono),ui-monospace,monospace;
  /* + all --color-* / --radius-* / --shadow-* / --glow-* exposed as today */
}
```

---

## 15. Implementation Notes (compatibility with CLAUDE.md)

- **Reuse, don't rewrite:** all changes are presentational/token/component; **no** backend or service
  logic changes unless a UI genuinely needs a new field/shape — then the *minimal* API change, with
  docs updated in the same change (frozen-spec rule).
- **Invariants preserved:** `sms_raw_text` never surfaced; `/ingest` dual-auth untouched; admin JWT
  separation untouched; RLS scoping untouched; Analytics read-only; FastAPI only via Categorization;
  free-tier only (all fonts self-hosted, no new infra).
- **Structure:** keep `components/ui` primitives + `components/shared` shell; extend, don't fork.
  Reuse `apiClient`, `useApi`/SWR (+ `isStale`), `AuthGuard`, route groups, Recharts + `chart-theme`.
- **Rollout order:** (1) tokens + fonts + dark-mode plumbing, (2) app shell (top nav + contextual
  sidebar + avatar menu + notifications + ⌘K), (3) primitives (button/card/table/form/states),
  (4) dashboard + charts, (5) transactions/analytics/planning, (6) AI surfaces, (7) landing page,
  (8) admin portal. Each surface ships behind the same tokens so nothing regresses.
- **Docs:** this file is the source of truth; update it and `implementation/tracking/STATUS.md` as
  surfaces land.

---

*Approve this spec to proceed to implementation (starting with tokens + fonts + dark-mode plumbing).
A companion visual style-tile artifact accompanies this document for at-a-glance sign-off.*
