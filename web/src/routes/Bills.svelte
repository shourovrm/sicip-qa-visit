<!-- New bill: pick finished+unsubmitted tours, preview nights/food per tour, download .xlsx,
     Submit freezes a snapshot (bills row) + marks tours submitted. Previous bills: read-only,
     regenerate .xlsx from the frozen snapshot (never re-derives from live data). -->
<script>
  import { listTrips, listVisits, listLegsForTrips, listBills, createBill, updateTrip, updateVisit, listTravelPlaces, createLeg, updateLeg, softDeleteLeg } from '../lib/db.js'
  import { officer } from '../lib/auth.js'
  import { CATEGORY_LABELS, suggestedNights, suggestedFood } from '../lib/scoring.js'
  import { leg as mkLeg, makeTrip, billTotals } from '../lib/billmath.js'
  import { toBillTrips, snapshotBill, buildStoredTrips, tourSortKey, institutesForTrip, purposeLineForTrip } from '../lib/billsnapshot.js'
  import { fillBillTemplate, fillLocalBillTemplate, downloadBuffer } from '../lib/xlsx.js'
  import { buildBillHtml, buildLocalBillHtml, localBillTrips, printBillHtml } from '../lib/billhtml.js'
  import { newLegDraft, legFromRow, legPayload } from '../lib/legs.js'
  import Dropdown from '../components/Dropdown.svelte'
  import LegModal from '../components/LegModal.svelte'

  let trips = [], visits = [], legs = [], bills = [], places = []
  let loading = true
  let selected = new Set()
  let genErr = ''
  let busy = false
  let submittedMsg = ''
  let legForm = null // {trip_id, id?, ...} being added/edited -- travel CRUD lives here (bill prep), same as android
  let legErr = ''

  // load waits for the officer row (hard refresh: onMount fires before auth resolves)
  let loadStarted = false
  $: if ($officer && !loadStarted) { loadStarted = true; load() }

  async function load() {
    loading = true
    const mine = $officer?.id
    const [allTrips, allVisits] = await Promise.all([listTrips(), listVisits()])
    trips = allTrips.filter((t) => t.officer_id === mine && t.status === 'finished' && !t.submitted)
    visits = allVisits.filter((v) => v.officer_id === mine)
    legs = await listLegsForTrips(trips.map((t) => t.id))
    places = await listTravelPlaces()
    bills = await listBills(mine)
    loading = false
  }

  function primaryVisit(tripId) {
    return visits.find((v) => v.trip_id === tripId && !v.is_additional)
  }

  function newLeg(tripId) {
    legForm = newLegDraft(tripId)
    legErr = ''
  }
  function editLeg(l) {
    legForm = legFromRow(l)
    legErr = ''
  }

  async function saveLeg() {
    legErr = ''
    try {
      const payload = legPayload(legForm)
      if (legForm.id) {
        const updated = await updateLeg(legForm.id, payload)
        legs = legs.map((l) => (l.id === updated.id ? updated : l))
      } else {
        const created = await createLeg(payload)
        legs = [...legs, created]
      }
      legForm = null
    } catch (e) {
      legErr = e.message
    }
  }

  async function delLeg(l) {
    if (!confirm('Delete this travel?')) return
    await softDeleteLeg(l.id)
    legs = legs.filter((x) => x.id !== l.id)
  }

  function toggle(tripId) {
    if (selected.has(tripId)) {
      selected.delete(tripId)
    } else {
      selected.add(tripId)
    }
    selected = new Set(selected)
  }

  // category is the single source for nights/food (CATEGORY_SPANS, v1.5 policy) -- changing the
  // dropdown writes back to the trip's primary visit so Visits/Tours pick it up too, same as
  // Tours.svelte's per-tour dropdown. nights/food recompute live, read-only.
  async function setCategory(tripId, category) {
    const pv = primaryVisit(tripId)
    if (!pv) return
    const updated = await updateVisit(pv.id, { category, category_override: true })
    visits = visits.map((v) => (v.id === updated.id ? updated : v))
  }

  // chrono sort (android T5): earliest leg departure first, trip.started_at as fallback. args
  // (trips/legs) referenced directly in the $: expression -- see the gotcha note above.
  $: sortedTrips = [...trips].sort((a, b) =>
    tourSortKey(legs.filter((l) => l.trip_id === a.id), a.started_at)
      .localeCompare(tourSortKey(legs.filter((l) => l.trip_id === b.id), b.started_at)))

  // preview/snapshot/submit all iterate this instead of raw `selected` so they inherit the same
  // chrono order (android T5: "apply everywhere tours are listed/grouped for bills").
  $: orderedTripIds = sortedTrips.filter((t) => selected.has(t.id)).map((t) => t.id)

  $: selectedTrips = buildStoredTrips(orderedTripIds, visits, legs)
  $: billTripsPreview = toBillTrips({ billDate: new Date().toISOString().slice(0, 10), trips: selectedTrips })
  $: totals = billTotals(selectedTrips.map((t) => makeTrip(t.legs.map((l) => mkLeg(l.fare)), '', '', t.nights, t.foodDays)))

  function sameInstant(a, b) {
    if (!a || !b) return a === b
    return new Date(a).getTime() === new Date(b).getTime()
  }

  // imposed tour times (android T5): legs are ground truth once entered -- mirror
  // started_at/finished_at on the trip row so Team/Tours/chrono-sort-fallback stay accurate.
  // fires whenever a selected tour's legs change; args passed directly (not closure reads) so
  // svelte's $: sees `selected`/`trips`/`legs` change. arr_date is explicit in this schema so no
  // cross-midnight inference is needed (ponytail: unlike android, we never guess the arrival day).
  async function imposeTimes(sel, allTrips, allLegs) {
    for (const tripId of sel) {
      const trip = allTrips.find((t) => t.id === tripId)
      const tLegs = allLegs.filter((l) => l.trip_id === tripId)
      if (!trip || !tLegs.length) continue
      const start = tLegs.reduce((min, l) => { const k = `${l.dep_date}T${l.dep_time}`; return k < min ? k : min }, `${tLegs[0].dep_date}T${tLegs[0].dep_time}`)
      const end = tLegs.reduce((max, l) => { const k = `${l.arr_date}T${l.arr_time}`; return k > max ? k : max }, `${tLegs[0].arr_date}T${tLegs[0].arr_time}`)
      const startedAt = `${start}:00Z`
      const finishedAt = `${end}:00Z`
      if (sameInstant(trip.started_at, startedAt) && sameInstant(trip.finished_at, finishedAt)) continue
      const updated = await updateTrip(tripId, { started_at: startedAt, finished_at: finishedAt })
      trips = trips.map((t) => (t.id === updated.id ? updated : t))
    }
  }
  $: imposeTimes(selected, trips, legs)

  // shared template fetch -- same missing-file error message for both bill kinds
  async function fetchTemplate(path) {
    const resp = await fetch(path)
    if (!resp.ok) throw new Error(`Template not found at ${path} (run \`bun run build\` or copy it into public/ for dev).`)
    return resp.arrayBuffer()
  }

  async function downloadXlsx() {
    genErr = ''
    busy = true
    try {
      const buf = await fetchTemplate('/tada-template.xlsx')
      const out = await fillBillTemplate(buf, $officer.name, new Date().toISOString().slice(0, 10), billTripsPreview, totals)
      downloadBuffer(out, `TADA-${$officer.name.replace(/\s+/g, '-')}-${Date.now()}.xlsx`)
    } catch (e) {
      genErr = e.message
    }
    busy = false
  }

  function downloadPdf() {
    const billDate = new Date().toISOString().slice(0, 10)
    const html = buildBillHtml($officer.name, billDate, billTripsPreview, totals)
    printBillHtml(html)
  }

  async function downloadLocalXlsx() {
    genErr = ''
    busy = true
    try {
      const buf = await fetchTemplate('/local-tada-template.xlsx')
      const out = await fillLocalBillTemplate(buf, $officer.name, new Date().toISOString().slice(0, 10), localBillTrips(billTripsPreview))
      downloadBuffer(out, `Local-TADA-${$officer.name.replace(/\s+/g, '-')}-${Date.now()}.xlsx`)
    } catch (e) {
      genErr = e.message
    }
    busy = false
  }

  function downloadLocalPdf() {
    const billDate = new Date().toISOString().slice(0, 10)
    const html = buildLocalBillHtml($officer.name, billDate, billTripsPreview)
    printBillHtml(html)
  }

  async function submitBill() {
    if (!confirm(`Submit bill for ${selected.size} tour(s)? This freezes the amounts and marks them submitted.`)) return
    busy = true
    genErr = ''
    try {
      const billDate = new Date().toISOString().slice(0, 10)
      const data = snapshotBill(billDate, $officer.name, selectedTrips, totals)
      await createBill({ officer_id: $officer.id, bill_date: billDate, data, net: totals.net })
      for (const tripId of selected) await updateTrip(tripId, { submitted: true })
      submittedMsg = 'Bill submitted.'
      selected = new Set()
      await load()
    } catch (e) {
      genErr = e.message
    }
    busy = false
  }

  async function regenerate(bill) {
    const buf = await fetchTemplate('/tada-template.xlsx')
    const trips = toBillTrips(bill.data)
    const out = await fillBillTemplate(buf, bill.data.officerName, bill.bill_date, trips, bill.data.totals)
    downloadBuffer(out, `TADA-${bill.data.officerName.replace(/\s+/g, '-')}-${bill.bill_date}.xlsx`)
  }

  function regeneratePdf(bill) {
    const html = buildBillHtml(bill.data.officerName, bill.bill_date, toBillTrips(bill.data), bill.data.totals)
    printBillHtml(html)
  }

  async function regenerateLocalXlsx(bill) {
    const buf = await fetchTemplate('/local-tada-template.xlsx')
    const trips = toBillTrips(bill.data)
    const out = await fillLocalBillTemplate(buf, bill.data.officerName, bill.bill_date, localBillTrips(trips))
    downloadBuffer(out, `Local-TADA-${bill.data.officerName.replace(/\s+/g, '-')}-${bill.bill_date}.xlsx`)
  }

  function regenerateLocalPdf(bill) {
    const html = buildLocalBillHtml(bill.data.officerName, bill.bill_date, toBillTrips(bill.data))
    printBillHtml(html)
  }
</script>

<h1>Bills</h1>

{#if loading}
  <p class="muted">Loading…</p>
{:else}
  <div class="card">
    <h2>New bill</h2>
    {#if trips.length === 0}
      <p class="muted">No finished, unsubmitted tours to bill.</p>
    {:else}
      <table>
        <thead><tr><th></th><th>Institutes</th><th>Dates</th><th>Visits</th><th>Category</th><th>Travel</th></tr></thead>
        <tbody>
          {#each sortedTrips as t (t.id)}
            {@const tVisits = visits.filter((v) => v.trip_id === t.id)}
            {@const pv = tVisits.find((v) => !v.is_additional)}
            {@const tLegs = legs.filter((l) => l.trip_id === t.id)}
            <tr>
              <td><input type="checkbox" checked={selected.has(t.id)} on:change={() => toggle(t.id)} /></td>
              <td>{institutesForTrip(t.id, visits).join(', ') || '—'}</td>
              <td>{pv ? `${pv.start_date} – ${pv.end_date}` : ''}</td>
              <td>{tVisits.length} visit{tVisits.length === 1 ? '' : 's'}</td>
              <td>{pv?.category ?? ''}</td>
              <td>{tLegs.length} travel{tLegs.length === 1 ? '' : 's'} · ৳{tLegs.reduce((s, l) => s + Number(l.fare), 0)}</td>
            </tr>
          {/each}
        </tbody>
      </table>

      {#if selected.size > 0}
        <h3>Preview</h3>
        {#each orderedTripIds as tripId (tripId)}
          {@const pv = visits.find((v) => v.trip_id === tripId && !v.is_additional)}
          {@const tLegs = legs.filter((l) => l.trip_id === tripId).sort((a, b) => (a.dep_date + a.dep_time).localeCompare(b.dep_date + b.dep_time))}
          <div class="preview-trip">
            <p><b>{purposeLineForTrip(tripId, visits)}</b></p>
            <div class="row-wrap cat-row">
              <div class="field" style="width:280px">
                <label for="cat-{tripId}">Category</label>
                <Dropdown id="cat-{tripId}" value={pv.category} options={Object.entries(CATEGORY_LABELS)} on:change={(e) => setCategory(tripId, e.target.value)} />
              </div>
              <div class="muted derived">Nights: {suggestedNights(pv.category)} · Food days: {suggestedFood(pv.category)}</div>
            </div>
            <h3>Travel details</h3>
            <table>
              <thead><tr><th>Dep</th><th>Arr</th><th>Mode</th><th>Class</th><th>Fare</th><th>Remarks</th><th></th></tr></thead>
              <tbody>
                {#if tLegs.length === 0}<tr><td colspan="7" class="muted">No travel yet.</td></tr>{/if}
                {#each tLegs as l (l.id)}
                  <tr>
                    <td>{l.dep_date} {l.dep_time}<br /><span class="muted">{l.dep_place}</span></td>
                    <td>{l.arr_date} {l.arr_time}<br /><span class="muted">{l.arr_place}</span></td>
                    <td>{l.mode}</td>
                    <td>{l.class ?? ''}</td>
                    <td>{l.fare}</td>
                    <td>{l.remarks ?? ''}</td>
                    <td><button class="btn-link" on:click={() => editLeg(l)}>Edit</button> <button class="btn-link" on:click={() => delLeg(l)}>Delete</button></td>
                  </tr>
                {/each}
              </tbody>
            </table>
            <button class="btn" on:click={() => newLeg(tripId)}>+ Add travel</button>
          </div>
        {/each}

        <p class="totals">TA: {totals.ta} · Accommodation: {totals.accommodation} · Food: {totals.food} · <b>Net: {totals.net}</b></p>
        {#if genErr}<p class="err">{genErr}</p>{/if}
        {#if submittedMsg}<p class="muted">{submittedMsg}</p>{/if}
        <div class="row">
          <button class="btn" disabled={busy} on:click={downloadXlsx}>Download TADA Bill .xlsx</button>
          <button class="btn" disabled={busy} on:click={downloadPdf}>Download TADA Bill PDF</button>
          <button class="btn" disabled={busy} on:click={downloadLocalXlsx}>Download Local Bill .xlsx</button>
          <button class="btn" disabled={busy} on:click={downloadLocalPdf}>Download Local Bill PDF</button>
          <button class="btn btn-primary" disabled={busy} on:click={submitBill}>Submit bill</button>
        </div>
      {/if}
    {/if}
  </div>

  <div class="card">
    <h2>Previous bills</h2>
    {#if bills.length === 0}
      <p class="muted">No bills submitted yet.</p>
    {:else}
      <table>
        <thead><tr><th>Date</th><th>Net</th><th>Tours</th><th></th></tr></thead>
        <tbody>
          {#each bills as b (b.id)}
            <tr>
              <td>{b.bill_date}</td>
              <td>{b.net}</td>
              <td>{b.data.trips.length}</td>
              <td>
                <button class="btn-link" on:click={() => regenerate(b)}>Download TADA Bill .xlsx</button>
                <button class="btn-link" on:click={() => regeneratePdf(b)}>Download TADA Bill PDF</button>
                <button class="btn-link" on:click={() => regenerateLocalXlsx(b)}>Download Local Bill .xlsx</button>
                <button class="btn-link" on:click={() => regenerateLocalPdf(b)}>Download Local Bill PDF</button>
              </td>
            </tr>
          {/each}
        </tbody>
      </table>
    {/if}
  </div>
{/if}

{#if legForm}
  <LegModal legForm={legForm} places={places} legErr={legErr} on:save={saveLeg} on:cancel={() => (legForm = null)} />
{/if}

<style>
  h1 { color: var(--primary); }
  h2 { font-size: 15px; margin: 0 0 12px; }
  h3 { font-size: 13px; color: var(--muted); text-transform: uppercase; margin: 16px 0 8px; }
  .card { margin-bottom: 16px; }
  .preview-trip { border: 1px solid var(--outline); border-radius: 10px; padding: 10px; margin-bottom: 10px; }
  .cat-row { align-items: flex-end; margin: 8px 0; }
  .derived { padding-bottom: 8px; }
  .totals { font-size: 14px; margin: 12px 0; }
</style>
