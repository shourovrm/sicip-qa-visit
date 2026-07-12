// travel-leg form helpers shared by Bills.svelte (bill prep) and Home.svelte (Add travel on the
// active tour card) -- one place for the draft-object shape + the ticket-attached tick, which is
// stored inside the remarks string (see DECISIONS.md).
import { TRANSPORT, TICKET_REMARK } from './seeds.js'

export function composeRemarks(text, ticket) {
  const t = (text || '').trim()
  if (!ticket) return t
  return t ? `${t}; ${TICKET_REMARK}` : TICKET_REMARK
}

export function parseRemarks(remarks) {
  const r = remarks ?? ''
  if (!r.includes(TICKET_REMARK)) return { text: r, ticket: false }
  const text = r.replace(TICKET_REMARK, '').replace(/;\s*$/, '').trim()
  return { text, ticket: true }
}

// blank draft for "+ Add travel"
export function newLegDraft(tripId) {
  return { trip_id: tripId, dep_date: '', dep_time: '', dep_place: '', arr_date: '', arr_time: '', arr_place: '', mode: 'Bus', class: 'AC', fare: 0, remarks: '', ticket: false }
}

// existing row -> editable draft (splits the ticket tick back out of remarks)
export function legFromRow(l) {
  const { text, ticket } = parseRemarks(l.remarks)
  return { ...l, mode: l.mode in TRANSPORT ? l.mode : 'Other', otherMode: l.mode in TRANSPORT ? '' : l.mode, remarks: text, ticket }
}

// draft -> db row payload
export function legPayload(legForm) {
  const mode = legForm.mode === 'Other' ? (legForm.otherMode || 'Other') : legForm.mode
  const isNA = mode === 'N/A'
  return {
    trip_id: legForm.trip_id, dep_date: legForm.dep_date, dep_time: legForm.dep_time, dep_place: legForm.dep_place,
    arr_date: legForm.arr_date, arr_time: legForm.arr_time, arr_place: legForm.arr_place,
    mode, class: isNA ? null : legForm.class, fare: isNA ? 0 : (Number(legForm.fare) || 0),
    remarks: composeRemarks(legForm.remarks, legForm.ticket),
  }
}
