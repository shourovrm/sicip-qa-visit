// TMS institutes of a visit's association (partner), for the visit form's institute suggestions.
// Kept in memory per association so reopening the form does not call TMS again; a failure is not
// cached, so the next open retries.
import { loadLinkChoices } from './tmsreport.js'

const institutesByAssociation = new Map()

export function clearInstituteCache() {
  institutesByAssociation.clear()
}

// resolves to the association's TMS institute rows; none when no partner has that short name
export async function loadAssociationInstitutes(association, loadChoices = loadLinkChoices) {
  const key = String(association ?? '').trim().toLowerCase()
  if (!key) return []
  if (!institutesByAssociation.has(key)) {
    const choices = await loadChoices(association, '')
    institutesByAssociation.set(key, choices.institutes ?? [])
  }
  return institutesByAssociation.get(key)
}
