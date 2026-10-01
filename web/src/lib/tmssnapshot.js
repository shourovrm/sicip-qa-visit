// pure TMS snapshot math for one institute on one visit date: course totals (QA 1.40/1.50) and the
// batches running that day with their counts and 7 class-day attendance (surprise C, QA 1.60).
// Counts and course names only, never trainee records. 1:1 port of android
// data/tms/TmsSnapshotBuilder.kt (same fixture: shared/report-templates/fixtures/tms-sample.json).
import { fullCourseName } from './tmscatalog.js'

export const CLASS_DAYS_FOR_MEAN = 7
export const MAX_DAYS_LOOKBACK = 14 // calendar days walked back over holidays

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

const text = (value) => String(value ?? '').trim()
const isoDay = (value) => text(value).slice(0, 10)

// TMS sends counts as numbers or strings; anything unreadable is 0 (android lenientInt)
function count(value) {
  const number = Number(text(value))
  return Number.isFinite(number) ? Math.trunc(number) : 0
}

function addDays(isoDate, days) {
  const date = new Date(`${isoDate}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

// one day's date_wise_attendanceReport rows -> present count; null = no rows (no class that day)
export function presentCountOf(rows) {
  if (!Array.isArray(rows) || rows.length === 0) return null
  return rows.filter((row) => count(row?.attendance?.is_present) === 1).length
}

// the days whose attendance the 7 class-day mean may need: the visit date first, then back up to
// MAX_DAYS_LOOKBACK calendar days, never before the batch start
export function attendanceDates(visitDate, batchStart) {
  const start = isoDay(batchStart) || isoDay(visitDate)
  const dates = []
  for (let walked = 0; walked <= MAX_DAYS_LOOKBACK; walked++) {
    const day = addDays(isoDay(visitDate), -walked)
    if (day < start) break
    dates.push(day)
  }
  return dates
}

// dates newest first + Map(date -> present | null) -> the visit day's count and the mean of the
// first CLASS_DAYS_FOR_MEAN days that have rows, with that range
export function attendanceOf(dates, presentByDate) {
  const classDays = []
  for (const day of dates) {
    if (classDays.length >= CLASS_DAYS_FOR_MEAN) break
    const present = presentByDate.get(day)
    if (present != null) classDays.push({ day, present })
  }
  const today = presentByDate.get(dates[0]) ?? null
  if (classDays.length === 0) return { today, mean: null, from: null, to: null, classDays: 0 }
  const total = classDays.reduce((sum, entry) => sum + entry.present, 0)
  return {
    today,
    mean: Math.round((total / classDays.length) * 10) / 10,
    from: classDays[classDays.length - 1].day,
    to: classDays[0].day,
    classDays: classDays.length,
  }
}

// 4.1 -> "4.1", 25 -> "25", null -> ""
export function formatMean(mean) {
  if (mean == null) return ''
  const rounded = Math.round(mean * 10) / 10
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1)
}

function dayParts(isoDate) {
  const [year, month, day] = isoDate.split('-').map(Number)
  return { year, month: MONTHS[month - 1], day }
}

// "18–28 Sep 2026", "28 Aug – 3 Sep 2026", "29 Dec 2025 – 2 Jan 2026", "28 Sep 2026"
function dateRange(from, to) {
  const start = dayParts(from)
  const end = dayParts(to)
  const endText = `${end.day} ${end.month} ${end.year}`
  if (from === to) return endText
  if (start.year !== end.year) return `${start.day} ${start.month} ${start.year} – ${endText}`
  if (start.month !== end.month) return `${start.day} ${start.month} – ${endText}`
  return `${start.day}–${endText}`
}

// the C card remarks note: "TMS avg 18–28 Sep 2026 (7 class days)"; "" without class days
export function averageNote({ from, to, classDays }) {
  if (!classDays || !from || !to) return ''
  return `TMS avg ${dateRange(from, to)} (${classDays} class day${classDays === 1 ? '' : 's'})`
}

function batchRow(row) {
  return {
    id: count(row.id),
    courseId: count(row.course_info_id),
    number: text(row.batch_number),
    start: isoDay(row.start_date),
    end: isoDay(row.end_date),
    days: count(row.total_training_days),
    hours: count(row.total_training_hours),
  }
}

// the latest batch's length, e.g. "60 days / 300 h"
function durationOf(batches) {
  const dated = batches.filter((batch) => batch.start)
  const latest = dated.reduce((best, batch) => (best == null || batch.start > best.start ? batch : best), null) ?? batches[0]
  return latest ? `${latest.days} days / ${latest.hours} h` : ''
}

// dropout = enrolled who never sat the assessment, only for ended batches with assessment data
// (a running batch has assessed 0, which would read as 100% dropout). null when none qualifies.
function endedDropouts(batches, countsByBatch, visitDate) {
  let total = 0
  let female = 0
  let qualifying = 0
  for (const batch of batches) {
    const row = countsByBatch.get(batch.id)
    const ended = batch.end && batch.end < visitDate
    if (!row || !ended || count(row.assessed_trainee) <= 0) continue
    total += Math.max(0, count(row.enroll_trainee) - count(row.assessed_trainee))
    female += Math.max(0, count(row.enroll_female_trainee) - count(row.assessed_female_trainee))
    qualifying++
  }
  return qualifying === 0 ? null : { total, female }
}

// input = {targets, batches, summary, aliases} (TMS list replies); attendanceByBatch =
// Map(batch id -> Map(date -> present | null)) for the running batches (missing = no rows)
export function buildSnapshot(input, visitDate, attendanceByBatch) {
  const day = isoDay(visitDate)
  const summaryRows = input.summary ?? []
  const summaryByCourse = new Map(summaryRows.map((row) => [count(row.course_info?.id), row]))
  const countsByBatch = new Map()
  for (const row of summaryRows) {
    for (const batchCounts of row.batch_summary ?? []) countsByBatch.set(count(batchCounts.batch_info?.id), batchCounts)
  }
  const aliasById = new Map((input.aliases ?? []).map((row) => [count(row.id), row.name]))
  const fullNameOf = (courseRow) => fullCourseName(courseRow.course_name, aliasById.get(count(courseRow.x_course_name_id)))

  // target courses, plus any course TMS has enrolment for that is not a target
  const targetRows = input.targets ?? []
  const extraRows = summaryRows.map((row) => row.course_info ?? {})
    .filter((extra) => !targetRows.some((target) => count(target.id) === count(extra.id)))
  const courseRows = [...targetRows, ...extraRows]
  const allBatches = (input.batches ?? []).map(batchRow)

  const courses = courseRows.map((courseRow) => {
    const courseId = count(courseRow.id)
    const totals = summaryByCourse.get(courseId) ?? {}
    const ownBatches = allBatches.filter((batch) => batch.courseId === courseId)
    const dropouts = endedDropouts(ownBatches, countsByBatch, day)
    return {
      name: fullNameOf(courseRow),
      code: text(courseRow.code),
      targetBatches: count(courseRow.total_target_batches),
      batchSize: count(courseRow.trainee_per_batch),
      duration: durationOf(ownBatches),
      enrolledTotal: count(totals.enroll_trainee),
      enrolledFemale: count(totals.enroll_female_trainee),
      certifiedTotal: count(totals.certification_trainee),
      certifiedFemale: count(totals.certification_female_trainee),
      placedTotal: count(totals.employment_trainee),
      placedFemale: count(totals.employment_female_trainee),
      dropoutTotal: dropouts?.total ?? null,
      dropoutFemale: dropouts?.female ?? null,
      tmsName: text(courseRow.course_name),
    }
  })

  const courseRowById = new Map(courseRows.map((row) => [count(row.id), row]))
  const runningBatches = allBatches
    .filter((batch) => batch.start && batch.end && batch.start <= day && day <= batch.end)
    .sort((a, b) => a.courseId - b.courseId || (Number(a.number) || Infinity) - (Number(b.number) || Infinity))
    .map((batch) => {
      const counts = countsByBatch.get(batch.id) ?? {}
      const courseRow = courseRowById.get(batch.courseId)
      const attendance = attendanceOf(attendanceDates(day, batch.start), attendanceByBatch.get(batch.id) ?? new Map())
      return {
        id: batch.id,
        courseId: batch.courseId,
        course: courseRow ? fullNameOf(courseRow) : '',
        batchNumber: batch.number,
        startDate: batch.start,
        endDate: batch.end,
        enrolled: count(counts.enroll_trainee),
        female: count(counts.enroll_female_trainee),
        attendanceToday: attendance.today,
        attendance7day: attendance.mean,
        averageFrom: attendance.from,
        averageTo: attendance.to,
        averageClassDays: attendance.classDays,
        tmsCourse: text(courseRow?.course_name),
      }
    })
  return { courses, runningBatches }
}

// the batches running on the visit date (what needs attendance calls before buildSnapshot)
export function runningBatchRows(batches, visitDate) {
  const day = isoDay(visitDate)
  return (batches ?? []).map(batchRow).filter((batch) => batch.start && batch.end && batch.start <= day && day <= batch.end)
}
