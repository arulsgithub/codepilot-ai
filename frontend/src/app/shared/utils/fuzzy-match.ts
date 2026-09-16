/**
 * Minimal subsequence-based fuzzy matcher for the command palette and
 * conversation search. Deliberately hand-rolled rather than pulling in a
 * library (e.g. fuse.js) — the corpus here is a few dozen commands/
 * conversation titles at most, so a proper scored-index matcher would be
 * dependency weight for no real benefit.
 *
 * A match succeeds when every character of `query` appears in `text`, in
 * order, case-insensitively (not necessarily contiguously) — the same
 * behavior as VS Code's "Go to File" / command palette filtering. The score
 * rewards contiguous and early matches so tighter, more relevant hits sort
 * first.
 */
export function fuzzyScore(query: string, text: string): number | null {
  const q = query.trim().toLowerCase();
  if (!q) {
    return 0;
  }
  const t = text.toLowerCase();

  let qi = 0;
  let score = 0;
  let lastMatchIndex = -1;

  for (let ti = 0; ti < t.length && qi < q.length; ti++) {
    if (t[ti] === q[qi]) {
      // Contiguous matches score higher than scattered ones.
      score += lastMatchIndex === ti - 1 ? 3 : 1;
      lastMatchIndex = ti;
      qi++;
    }
  }

  if (qi !== q.length) {
    return null;
  }
  return score;
}

export function fuzzyMatches(query: string, text: string): boolean {
  return fuzzyScore(query, text) !== null;
}
