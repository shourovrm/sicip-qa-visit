// lab-standard equipment (shared/lab-standards/lab-equipment.json, built by tools/lab_equipment.py
// from the SICIP-standards repo) for a report's association + course. Pure; mirrors android
// domain/report/LabEquipment.kt, fixture shared/report-templates/fixtures/lab-equipment-1.json.
import { courseBatchOfRef, courseKey } from './tmscatalog.js'

// visit association -> standards organisation where the two lists spell it differently
const ORG_ALIASES = { flaxa: 'lfmeab' }
// words that say nothing about which course it is
const FILLER_WORDS = new Set(['and', 'of', 'in', 'on', 'the', 'for', 'course', 'certificate'])

export function orgKey(association) {
  const key = String(association ?? '').toLowerCase().replace(/[^a-z0-9]+/g, '')
  return ORG_ALIASES[key] ?? key
}

// "Tiles & Marble Works (TMW)" -> [tile, marble, work]
export function courseWords(name) {
  return courseKey(name).split(' ')
    .filter((word) => word && !FILLER_WORDS.has(word))
    .map((word) => (word.length > 3 && word.endsWith('s') ? word.slice(0, -1) : word))
}

const containsAll = (words, wanted) => wanted.every((word) => words.includes(word))

// the association's standard courses a typed course name means: the same words; else one name's
// words inside the other ("Welding" ~ "Welding (1G, 2G & 3G)"); else its initials ("RAC")
export function matchingCourses(courses, association, courseText) {
  const org = orgKey(association)
  const ofOrg = (courses ?? []).filter((course) => orgKey(course.org) === org)
  const typed = courseWords(courseText)
  if (!typed.length) return []
  const same = ofOrg.filter((course) => courseWords(course.course).join(' ') === typed.join(' '))
  if (same.length) return same
  const within = ofOrg.filter((course) => {
    const words = courseWords(course.course)
    return containsAll(words, typed) || containsAll(typed, words)
  })
  if (within.length || typed.length > 1) return within
  return ofOrg.filter((course) => courseWords(course.course).map((word) => word[0]).join('') === typed[0])
}

function uniqueNames(courses) {
  const seen = new Set()
  const names = []
  for (const name of courses.flatMap((course) => course.equipment)) {
    if (seen.has(name.toLowerCase())) continue
    seen.add(name.toLowerCase())
    names.push(name)
  }
  return names
}

// equipment names to offer: the matched course's list; every course of the association when the
// card names no course or none matches; nothing for an association without standards
export function standardEquipment(courses, association, courseText) {
  const matched = matchingCourses(courses, association, courseText)
  if (matched.length) return uniqueNames(matched)
  const org = orgKey(association)
  return uniqueNames((courses ?? []).filter((course) => orgKey(course.org) === org))
}

// the course a card is about: the card field the template names in `suggestCourse`, which holds
// either a plain course or a courseRef "Course · N"
export function cardCourse(field, card) {
  if (!field.suggestCourse) return ''
  return courseBatchOfRef(card?.[field.suggestCourse]).course
}
