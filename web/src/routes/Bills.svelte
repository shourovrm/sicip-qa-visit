<!-- New bill: pick finished+unsubmitted tours, preview nights/food per tour, download .xlsx,
     Submit freezes a snapshot (bills row) + marks tours submitted. Previous bills: read-only,
     regenerate .xlsx from the frozen snapshot (never re-derives from live data). -->
<script>
  import { listTrips, listVisits, listLegsForTrips, listBills, createBill, updateTrip, updateVisit } from '../lib/db.js'
  import { officer } from '../lib/auth.js'
  import { CATEGORY_LABELS, suggestedNights, suggestedFood } from '../lib/scoring.js'
  import { leg as mkLeg, makeTrip, billTotals } from '../lib/billmath.js'
  import { toBillTrips, snapshotBill } from '../lib/billsnapshot.js'
  import { fillBillTemplate, downloadBuffer, fmtDate } from '../lib/xlsx.js'
  import { buildBillHtml, printBillHtml } from '../lib/billhtml.js'
  import Dropdown from '../components/Dropdown.svelte'

  let trips = [], visits = [], legs = [], bills = []
  let loading = true
  let selected = new Set()
  let genErr = ''
  let busy = false
  let submittedMsg = ''

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
    bills = await listBills(mine)
    loading = false
  }

  function primaryVisit(tripId) {
    return visits.find((v) => v.trip_id === tripId && !v.is_additional)
  }
  function tripLegs(tripId) {
    return legs.filter((l) => l.trip_id === tripId).sort((a, b) => (a.dep_date + a.dep_time).localeCompare(b.dep_date + b.dep_time))
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

  function purposeLineFor(v) {
    return `${v.purpose} - ${v.association} (Ref: ${v.ref_no || '—'}, ${fmtDate(v.ref_date || v.start_date)})`
  }

  // build snapshot-shaped trips (same shape bills.data stores) from currently selected live trips.
  // sel passed as arg so svelte's $: dependency tracking sees it (body-only refs aren't tracked).
  // nights/foodDays derived from the primary visit's category, not user-edited.
  function buildSnapshotTrips(sel) {
    return [...sel].map((tripId) => {
      const pv = primaryVisit(tripId)
      const tLegs = tripLegs(tripId)
      return {
        tripId,
        purposeLine: purposeLineFor(pv),
        nights: suggestedNights(pv.category),
        foodDays: suggestedFood(pv.category),
        legs: tLegs.map((l) => ({
          dep_date: l.dep_date, dep_time: l.dep_time, dep_place: l.dep_place,
          arr_date: l.arr_date, arr_time: l.arr_time, arr_place: l.arr_place,
          mode: l.mode, class: l.class, fare: Number(l.fare), remarks: l.remarks,
        })),
      }
    })
  }

  $: selectedTrips = buildSnapshotTrips(selected)
  $: billTripsPreview = toBillTrips({ billDate: new Date().toISOString().slice(0, 10), trips: selectedTrips })
  $: totals = billTotals(selectedTrips.map((t) => makeTrip(t.legs.map((l) => mkLeg(l.fare)), '', '', t.nights, t.foodDays)))

  async function downloadXlsx() {
    genErr = ''
    busy = true
    try {
      const resp = await fetch('/tada-template.xlsx')
      if (!resp.ok) throw new Error('Template not found at /tada-template.xlsx (run `bun run build` or copy it into public/ for dev).')
      const buf = await resp.arrayBuffer()
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
    const resp = await fetch('/tada-template.xlsx')
    const buf = await resp.arrayBuffer()
    const trips = toBillTrips(bill.data)
    const out = await fillBillTemplate(buf, bill.data.officerName, bill.bill_date, trips, bill.data.totals)
    downloadBuffer(out, `TADA-${bill.data.officerName.replace(/\s+/g, '-')}-${bill.bill_date}.xlsx`)
  }

  function regeneratePdf(bill) {
    const html = buildBillHtml(bill.data.officerName, bill.bill_date, toBillTrips(bill.data), bill.data.totals)
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
        <thead><tr><th></th><th>Institute</th><th>Dates</th><th>Category</th></tr></thead>
        <tbody>
          {#each trips as t (t.id)}
            {@const pv = primaryVisit(t.id)}
            <tr>
              <td><input type="checkbox" checked={selected.has(t.id)} on:change={() => toggle(t.id)} /></td>
              <td>{pv?.institute ?? '—'}</td>
              <td>{pv ? `${pv.start_date} – ${pv.end_date}` : ''}</td>
              <td>{pv?.category ?? ''}</td>
            </tr>
          {/each}
        </tbody>
      </table>

      {#if selected.size > 0}
        <h3>Preview</h3>
        {#each [...selected] as tripId (tripId)}
          {@const pv = primaryVisit(tripId)}
          <div class="preview-trip">
            <p><b>{purposeLineFor(pv)}</b></p>
            <div class="row-wrap cat-row">
              <div class="field" style="width:280px">
                <label for="cat-{tripId}">Category</label>
                <Dropdown id="cat-{tripId}" value={pv.category} options={Object.entries(CATEGORY_LABELS)} on:change={(e) => setCategory(tripId, e.target.value)} />
              </div>
              <div class="muted derived">Nights: {suggestedNights(pv.category)} · Food days: {suggestedFood(pv.category)}</div>
            </div>
            <table>
              <thead><tr><th>Dep</th><th>Arr</th><th>Mode</th><th>Fare</th></tr></thead>
              <tbody>
                {#each tripLegs(tripId) as l}
                  <tr><td>{l.dep_date} {l.dep_time} {l.dep_place}</td><td>{l.arr_date} {l.arr_time} {l.arr_place}</td><td>{l.mode}</td><td>{l.fare}</td></tr>
                {/each}
              </tbody>
            </table>
          </div>
        {/each}

        <p class="totals">TA: {totals.ta} · Accommodation: {totals.accommodation} · Food: {totals.food} · <b>Net: {totals.net}</b></p>
        {#if genErr}<p class="err">{genErr}</p>{/if}
        {#if submittedMsg}<p class="muted">{submittedMsg}</p>{/if}
        <div class="row">
          <button class="btn" disabled={busy} on:click={downloadXlsx}>Download .xlsx</button>
          <button class="btn" disabled={busy} on:click={downloadPdf}>Download PDF</button>
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
                <button class="btn-link" on:click={() => regenerate(b)}>Download .xlsx</button>
                <button class="btn-link" on:click={() => regeneratePdf(b)}>Download PDF</button>
              </td>
            </tr>
          {/each}
        </tbody>
      </table>
    {/if}
  </div>
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
