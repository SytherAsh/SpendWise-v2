/**
 * Word-level diff between two payee names, for the Merge Payees card (2026-07-20) — highlights
 * which tokens actually differ between the anchor and a candidate name, instead of asking the
 * user to read both names carefully to spot the difference themselves. Names in this app are
 * short (1-4 tokens), so a whitespace-token diff is enough signal without the complexity of a
 * character-level LCS diff.
 */

export interface NameDiffToken {
  text: string;
  /** True when this token has no exact match among the other name's tokens. */
  differs: boolean;
}

/** Splits `name` into tokens, marking each one that has no counterpart in `otherName`. */
export function diffTokens(name: string, otherName: string): NameDiffToken[] {
  const otherTokens = new Set(otherName.split(/\s+/).filter(Boolean));
  return name
    .split(/\s+/)
    .filter(Boolean)
    .map((text) => ({ text, differs: !otherTokens.has(text) }));
}
