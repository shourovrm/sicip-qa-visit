// pure shaping of TMS lists for the report: course names (full names), every batch of the linked
// institute, the batches running on the visit date, trainee name + mobile hints, and the link
// pickers' matching. Mirrors android data/tms/TmsSuggestCatalog.kt + TmsMatching.kt.

const text = (value) => String(value ?? '').trim()
const sameText = (a, b) => text(a).toLowerCase() === text(b).toLowerCase()

// TMS course_name vs its master-list alias: the longer one is the full name (alias 131 'Welding'
// is shorter than 'Advanced Welding', DECISIONS)
export function fullCourseName(courseName, alias) {
  const name = text(courseName)
  const other = text(alias)
  return other.length > name.length ? other : name
}

// {courseNames, batches:[{id, courseId, courseName, number, start, end}]}
export function buildCourseCatalog(targets, batches, aliases) {
  const aliasById = new Map((aliases ?? []).map((row) => [Number(row.id), row.name]))
  const nameByCourseId = new Map()
  const spellingsByCourseId = new Map() // every name a course goes by: full, TMS short, alias
  for (const row of targets ?? []) {
    const alias = aliasById.get(Number(row.x_course_name_id))
    nameByCourseId.set(Number(row.id), fullCourseName(row.course_name, alias))
    spellingsByCourseId.set(Number(row.id), [row.course_name, alias])
  }
  const batchRefs = (batches ?? []).map((row) => {
    const courseId = Number(row.course_info_id)
    const info = row.course_info ?? {}
    const fallback = fullCourseName(info.course_name, aliasById.get(Number(info.x_course_name_id)))
    const courseName = nameByCourseId.get(courseId) || fallback
    const spellings = [courseName, ...(spellingsByCourseId.get(courseId) ?? []), info.course_name, aliasById.get(Number(info.x_course_name_id))]
    return {
      id: Number(row.id),
      courseId,
      courseName,
      names: [...new Set(spellings.map(text).filter(Boolean))],
      number: text(row.batch_number),
      start: text(row.start_date).slice(0, 10),
      end: text(row.end_date).slice(0, 10),
    }
  })
  const courseNames = [...new Set([...nameByCourseId.values(), ...batchRefs.map((b) => b.courseName)].filter(Boolean))]
  return { courseNames, batches: batchRefs }
}

// batches with start <= visit date <= end, in catalog course order then batch number
export function runningBatches(catalog, visitDate) {
  const day = text(visitDate).slice(0, 10)
  if (!day) return []
  const courseOrder = (batch) => catalog.courseNames.indexOf(batch.courseName)
  return catalog.batches
    .filter((batch) => batch.start && batch.end && batch.start <= day && day <= batch.end)
    .sort((a, b) => courseOrder(a) - courseOrder(b) || Number(a.number) - Number(b.number))
}

// section A "courses" cards for the batches running on the visit date
export function runningCourseCards(catalog, visitDate, newId = () => crypto.randomUUID()) {
  return runningBatches(catalog, visitDate).map((batch) => ({ _id: newId(), course: batch.courseName, batch: batch.number }))
}

// a course typed in the report may be the full name, TMS's short name or the alias, with "and"
// for "&" and without the "(EIM)" code: compare on a folded key
export function courseKey(name) {
  return text(name).toLowerCase().replace(/\([^)]*\)/g, ' ').replace(/&/g, ' and ').replace(/[^a-z0-9]+/g, ' ').trim()
}
const isCourse = (batch, courseName) => {
  const wanted = courseKey(courseName)
  return wanted !== '' && batch.names.some((name) => courseKey(name) === wanted)
}

// every batch number of a course (finished ones too: graduates come from past batches), newest first
export function batchNumbersFor(catalog, courseName) {
  if (!text(courseName)) return []
  const numbers = catalog.batches
    .filter((batch) => isCourse(batch, courseName))
    .sort((a, b) => (b.start > a.start ? 1 : b.start < a.start ? -1 : 0))
    .map((batch) => batch.number)
  return [...new Set(numbers)]
}

// one batch by course name and batch number ("04" == "4"); null when unknown
export function findBatch(catalog, courseName, batchNumber) {
  const wanted = Number(text(batchNumber))
  return catalog.batches.find((batch) => isCourse(batch, courseName) && Number(batch.number) === wanted && text(batchNumber) !== '') ?? null
}

// a courseRef value "Plumbing and Pipe Fitting · 4" -> {course, batch}
export function courseBatchOfRef(value) {
  const raw = text(value)
  const at = raw.lastIndexOf(' · ')
  if (at < 0) return { course: raw, batch: '' }
  return { course: raw.slice(0, at).trim(), batch: raw.slice(at + 3).trim() }
}

// trainee/attendanceReport rows -> [{name, mobile}]; every other trainee field is dropped here
export function traineeHints(rows) {
  return (rows ?? [])
    .map((row) => ({ name: text(row?.trainee?.trainee_name), mobile: text(row?.trainee?.mobile) }))
    .filter((hint) => hint.name)
}

// partner whose short name equals the visit association
export function matchPartner(entities, association) {
  if (!text(association)) return null
  return (entities ?? []).find((entity) => sameText(entity.entity_short_name, association)) ?? null
}

// the one institute whose name or short name equals the report's institute text
export function autoLinkCandidate(institutes, instituteText) {
  if (!text(instituteText)) return null
  const equal = (institutes ?? []).filter((i) => sameText(i.institute_name, instituteText) || sameText(i.short_name, instituteText))
  return equal.length === 1 ? equal[0] : null
}

// newest active tranche, else newest at all
export function defaultTranche(tranches) {
  const newest = (list) => list.reduce((best, t) => (best == null || Number(t.id) > Number(best.id) ? t : best), null)
  const active = (tranches ?? []).filter((t) => Number(t.active_status) === 1)
  return newest(active.length ? active : tranches ?? [])
}
