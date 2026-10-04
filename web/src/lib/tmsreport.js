// TMS calls the surprise/QA report needs on the web: link pickers (tranche, partner, institute),
// the linked institute's course catalog and one batch's trainee hints. Results stay in memory;
// only the link itself is stored on the report (data.tms, same keys as android TmsLinkData.kt).
import { tmsList } from './tmsapi.js'
import { autoLinkCandidate, buildCourseCatalog, defaultTranche, matchPartner, traineeHints } from './tmscatalog.js'
import { attendanceDates, buildSnapshot, presentCountOf, runningBatchRows } from './tmssnapshot.js'
import { trainerHints } from './tmstrainers.js'

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

// what the report stores (address: same key as android TmsLinkData.kt). entity = the partner the
// officer picked: institute.entity_id is whoever registered the institute, wrong for a shared one
export function linkData(tranche, entityId, institute) {
  return {
    tranche_id: Number(tranche.id),
    entity_id: Number(entityId),
    institute_id: Number(institute.id),
    institute_no: String(institute.training_institute_no ?? ''),
    name: String(institute.institute_name ?? ''),
    address: String(institute.address ?? '').trim(),
  }
}

// links saved before addresses were kept: look the institute up again; '' when that fails
export async function instituteAddress(tms) {
  if (String(tms.address ?? '').trim()) return String(tms.address).trim()
  try {
    const institutes = await loadInstitutes(tms.entity_id, tms.tranche_id)
    const row = institutes.find((institute) => Number(institute.id) === Number(tms.institute_id))
    return String(row?.address ?? '').trim()
  } catch (e) {
    return ''
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

// the institute's active trainers (TMS "trainer list" page call), reduced to name + designation +
// courses + batch roles before it leaves this function
export async function loadTrainerHints(tms) {
  const rows = await tmsList(
    `entity/trainer/list?tranche_id=${tms.tranche_id}&entity_info_id=${tms.entity_id}&institute_info_id=${tms.institute_id}&active_status=1`,
  )
  return trainerHints(rows)
}

// one running batch's present counts on the days the 7 class-day mean may need; a day that fails
// counts as "no rows" so one bad reply never blocks the rest
async function batchAttendance(tms, batch, visitDate) {
  const dates = attendanceDates(visitDate, batch.start)
  const counts = await Promise.all(dates.map((date) =>
    tmsList(`trainee/date_wise_attendanceReport?entity=${tms.entity_id}&tranche=${tms.tranche_id}&institute=${tms.institute_id}&course=${batch.courseId}&batch=${batch.id}&date=${date}`)
      .then(presentCountOf) // trainee rows are dropped here: only the count leaves
      .catch(() => null),
  ))
  return new Map(dates.map((date, index) => [date, counts[index]]))
}

// course totals + running batches with attendance for the visit date (lib/tmssnapshot.js)
export async function loadTmsSnapshot(tms, visitDate) {
  const base = `entity_id=${tms.entity_id}`
  const [targets, batches, summary, aliases] = await Promise.all([
    tmsList(`institutetarget/all-list?tranche_id=${tms.tranche_id}&${base}&course_info_id=&training_institute_id=${tms.institute_id}&active_status=1`),
    tmsList(`batch/list?${base}&course_info_id=&institute_info_id=${tms.institute_id}`),
    tmsList(`enrollment/batch_summary?${base}&tranche_id=${tms.tranche_id}&institute_info_id=${tms.institute_id}`),
    tmsList('configurations/alias_name/list').catch(() => []),
  ])
  const attendance = new Map()
  for (const batch of runningBatchRows(batches, visitDate)) attendance.set(batch.id, await batchAttendance(tms, batch, visitDate))
  return buildSnapshot({ targets, batches, summary, aliases }, visitDate, attendance)
}
