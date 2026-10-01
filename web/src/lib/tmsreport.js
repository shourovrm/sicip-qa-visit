// TMS calls the surprise/QA report needs on the web: link pickers (tranche, partner, institute),
// the linked institute's course catalog and one batch's trainee hints. Results stay in memory;
// only the link itself is stored on the report (data.tms, same keys as android TmsLinkData.kt).
import { tmsList } from './tmsapi.js'
import { autoLinkCandidate, buildCourseCatalog, defaultTranche, matchPartner, traineeHints } from './tmscatalog.js'

export async function loadPartners() {
  const [tranches, entities] = await Promise.all([tmsList('configurations/tranche/list'), tmsList('entity/list?entity_id=')])
  return { tranche: defaultTranche(tranches), entities }
}

export function loadInstitutes(entityId, trancheId) {
  return tmsList(`institute/filterList?entity=${entityId}&tranche=${trancheId}&courseType=&course=&district=`)
}

// pickers preset from the visit: partner by association, institute by exact name
export async function loadLinkChoices(association, instituteText) {
  const { tranche, entities } = await loadPartners()
  const partner = matchPartner(entities, association)
  const institutes = partner && tranche ? await loadInstitutes(partner.id, tranche.id) : []
  return { tranche, entities, partner, institutes, institute: autoLinkCandidate(institutes, instituteText) }
}

// what the report stores
export function linkData(tranche, entityId, institute) {
  return {
    tranche_id: Number(tranche.id),
    entity_id: Number(institute.entity_id || entityId),
    institute_id: Number(institute.id),
    institute_no: String(institute.training_institute_no ?? ''),
    name: String(institute.institute_name ?? ''),
  }
}

export function isLinked(tms) {
  return Boolean(tms?.institute_id && tms?.entity_id && tms?.tranche_id)
}

// aliases are optional: a failure only means short course names
export async function loadCourseCatalog(tms) {
  const base = `entity_id=${tms.entity_id}`
  const [targets, batches, aliases] = await Promise.all([
    tmsList(`institutetarget/all-list?tranche_id=${tms.tranche_id}&${base}&course_info_id=&training_institute_id=${tms.institute_id}&active_status=1`),
    tmsList(`batch/list?${base}&course_info_id=&institute_info_id=${tms.institute_id}`),
    tmsList('configurations/alias_name/list').catch(() => []),
  ])
  return buildCourseCatalog(targets, batches, aliases)
}

// whole-batch attendance report = the one TMS list naming a batch's trainees (ended batches too);
// reduced to name + mobile before it leaves this function
export async function loadTraineeHints(tms, batch) {
  const rows = await tmsList(
    `trainee/attendanceReport?entity=${tms.entity_id}&tranche=${tms.tranche_id}&institute=${tms.institute_id}&course=${batch.courseId}&batch=${batch.id}`,
  )
  return traineeHints(rows)
}
