// QA 1.50 "with Percentage" (job placed % of certified, dropouts % of enrolled): a cards field
// with `percentOf` prints "count (p%)". Worked out at print time; stored values stay plain
// counts. Shared rule with android: shared/report-templates/fixtures/qa-percent-1.json.

const text = (value) => String(value ?? '').trim()

// whole percent, rounded half up; null when count or base can't give one
function percentOf(count, base) {
  const numerator = Number(text(count))
  const denominator = Number(text(base))
  if (!text(count) || !text(base) || !Number.isFinite(numerator) || !Number.isFinite(denominator) || denominator <= 0) return null
  return Math.round((numerator * 100) / denominator)
}

// "12 (48%)"; base unusable -> "12"; count blank -> "" (tables print "-")
export function countWithPercent(count, base) {
  const percent = percentOf(count, base)
  return percent == null ? text(count) : `${text(count)} (${percent}%)`
}

// editor hint beside the field: "48%", "" when it can't be worked out
export function percentOnly(count, base) {
  const percent = percentOf(count, base)
  return percent == null ? '' : `${percent}%`
}

export function percentFields(block) {
  return (block?.fields ?? []).filter((field) => field.percentOf)
}
