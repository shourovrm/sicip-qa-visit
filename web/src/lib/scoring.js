// visit scoring: category -> points, rank aggregation.
// ported 1:1 from android/app/src/main/java/bd/sicip/qavisit/domain/Scoring.kt

// fixed points scale per category. classic ladder (nights = days-1) vs plus ladder
// (nights >= days, i.e. an extra night beyond the classic span).
export const POINTS = {
  'A***': 116, 'A**+': 112, 'A**': 100, 'A++*': 96, 'A++': 84,
  'A+*': 80, 'A+': 68, 'A*': 64, 'A': 52, 'B+': 48, 'B': 36,
  'C+': 32, 'C': 20, 'D+': 16, 'D': 4, 'E': 1, 'N/A': 0,
}

export function points(category) {
  return POINTS[category] ?? 0
}

// category -> [days, nights] span it represents. single source for bill allowances.
export const CATEGORY_SPANS = {
  'A***': [8, 7], 'A**+': [7, 7], 'A**': [7, 6], 'A++*': [6, 6], 'A++': [6, 5],
  'A+*': [5, 5], 'A+': [5, 4], 'A*': [4, 4], 'A': [4, 3], 'B+': [3, 3], 'B': [3, 2],
  'C+': [2, 2], 'C': [2, 1], 'D+': [1, 1], 'D': [1, 0], 'E': [0, 0], 'N/A': [0, 0],
}

// accommodation nights = the category's night count, flat.
export function suggestedNights(category) {
  return CATEGORY_SPANS[category]?.[1] ?? 0
}

// food-days = every night full + a half-day for each day beyond the last night.
export function suggestedFood(category) {
  const [d, n] = CATEGORY_SPANS[category] ?? [0, 0]
  return n + 0.5 * (d - n)
}

// full explanation shown wherever the user picks/reads a category (dropdowns).
export const CATEGORY_LABELS = {
  'A***': 'A*** — 8D7N (116 pts)',
  'A**+': 'A**+ — 7D7N (112 pts)',
  'A**': 'A** — 7D6N (100 pts)',
  'A++*': 'A++* — 6D6N (96 pts)',
  'A++': 'A++ — 6D5N (84 pts)',
  'A+*': 'A+* — 5D5N (80 pts)',
  'A+': 'A+ — 5D4N (68 pts)',
  'A*': 'A* — 4D4N (64 pts)',
  'A': 'A — 4D3N (52 pts)',
  'B+': 'B+ — 3D3N (48 pts)',
  'B': 'B — 3D2N (36 pts)',
  'C+': 'C+ — 2D2N (32 pts)',
  'C': 'C — 2D1N (20 pts)',
  'D+': 'D+ — 1D1N (16 pts)',
  'D': 'D — 1 day / Dhaka non-metro (4 pts)',
  'E': 'E — Dhaka metro (1 pt)',
  'N/A': 'N/A — Additional (0 pts)',
}

// only done, non-deleted visits score (scheduled visits don't count yet). missing `done` = true.
export function totalPoints(visits) {
  return visits.filter((v) => !v.deleted && v.done !== false).reduce((sum, v) => sum + points(v.category), 0)
}

// officers ranked by summed points, fewest first (#1 = fewest points). returns [[officerId, points], ...]
export function rank(visits) {
  const byOfficer = new Map()
  for (const v of visits) {
    if (v.deleted || v.done === false) continue
    byOfficer.set(v.officerId, (byOfficer.get(v.officerId) ?? 0) + points(v.category))
  }
  return [...byOfficer.entries()].sort((a, b) => a[1] - b[1])
}

// visits within a calendar month (yearMonth = "yyyy-MM"), for "this month: n visits · m pts".
export function monthSummary(visits, yearMonth) {
  const inMonth = visits.filter((v) => !v.deleted && v.done !== false && v.startDate.slice(0, 7) === yearMonth)
  return [inMonth.length, inMonth.reduce((sum, v) => sum + points(v.category), 0)]
}

// "Last month" rank cutoff: last day of the month before `today` (iso "yyyy-MM-dd"). day 0 of
// the current month is the previous month's last day -- rolls Jan back into prior-year Dec 31,
// leap Feb included, for free (no manual month-length table). mirrors Rank.kt 1:1.
export function lastDayOfPreviousMonth(todayIso) {
  const [y, m] = todayIso.split('-').map(Number)
  return new Date(Date.UTC(y, m - 1, 0)).toISOString().slice(0, 10)
}
