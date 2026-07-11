<!-- New bill: pick finished+unsubmitted tours, preview nights/food per tour, download .xlsx,
     Submit freezes a snapshot (bills row) + marks tours submitted. Previous bills: read-only,
     regenerate .xlsx from the frozen snapshot (never re-derives from live data). -->
<script>
  import { listTrips, listVisits, listLegsForTrips, listBills, createBill, updateTrip, updateVisit, listTravelPlaces, createLeg, updateLeg, softDeleteLeg } from '../lib/db.js'
  import { officer } from '../lib/auth.js'
  import { CATEGORY_LABELS, suggestedNights, suggestedFood } from '../lib/scoring.js'
  import { leg as mkLeg, makeTrip, billTotals } from '../lib/billmath.js'
  import { toBillTrips, snapshotBill } from '../lib/billsnapshot.js'
  import { fillBillTemplate, fillLocalBillTemplate, downloadBuffer, fmtDate } from '../lib/xlsx.js'
  import { buildBillHtml, buildLocalBillHtml, localBillTrips, printBillHtml } from '../lib/billhtml.js'
  import { TRANSPORT, TICKET_REMARK } from '../lib/seeds.js'
  import Dropdown from '../components/Dropdown.svelte'

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

  // android stores the tick inside remarks string -- match exactly on compose/parse (copied from Tours.svelte)
  function composeRemarks(text, ticket) {
    const t = (text || '').trim()
    if (!ticket) return t
    return t ? `${t}; ${TICKET_REMARK}` : TICKET_REMARK
  }
  function parseRemarks(remarks) {
    const r = remarks ?? ''
    if (!r.includes(TICKET_REMARK)) return { text: r, ticket: false }
    const text = r.replace(TICKET_REMARK, '').replace(/;\s*$/, '').trim()
    return { text, ticket: true }
  }

  function newLeg(tripId) {
    legForm = { trip_id: tripId, dep_date: '', dep_time: '', dep_place: '', arr_date: '', arr_time: '', arr_place: '', mode: 'Bus', class: 'AC', fare: 0, remarks: '', ticket: false }
    legErr = ''
  }
  function editLeg(l) {
    const { text, ticket } = parseRemarks(l.remarks)
    legForm = { ...l, mode: l.mode in TRANSPORT ? l.mode : 'Other', otherMode: l.mode in TRANSPORT ? '' : l.mode, remarks: text, ticket }
    legErr = ''
  }
  $: legClasses = legForm && legForm.mode !== 'Other' ? TRANSPORT[legForm.mode] ?? [] : []

  async function saveLeg() {
    legErr = ''
    try {
      const mode = legForm.mode === 'Other' ? (legForm.otherMode || 'Other') : legForm.mode
      const isNA = mode === 'N/A'
      const payload = {
        trip_id: legForm.trip_id, dep_date: legForm.dep_date, dep_time: legForm.dep_time, dep_place: legForm.dep_place,
        arr_date: legForm.arr_date, arr_time: legForm.arr_time, arr_place: legForm.arr_place,
        mode, class: isNA ? null : legForm.class, fare: isNA ? 0 : (Number(legForm.fare) || 0),
        remarks: composeRemarks(legForm.remarks, legForm.ticket),
      }
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

  function purposeLineFor(v) {
    return `${v.purpose} - ${v.association} (Ref: ${v.ref_no || '—'}, ${fmtDate(v.ref_date || v.start_date)})`
  }

  // build snapshot-shaped trips (same shape bills.data stores) from currently selected live trips.
  // sel/allVisits/allLegs passed as args (not read off the closure) so svelte's $: dependency
  // tracking sees them -- a helper *call* like primaryVisit(id) hides the read from svelte's
  // static analysis, so a category edit (setCategory) or leg CRUD (saveLeg/delLeg) would leave
  // totals stale otherwise. nights/foodDays derived from the primary visit's category, not user-edited.
  function buildSnapshotTrips(sel, allVisits, allLegs) {
    return [...sel].map((tripId) => {
      const pv = allVisits.find((v) => v.trip_id === tripId && !v.is_additional)
      const tLegs = allLegs.filter((l) => l.trip_id === tripId).sort((a, b) => (a.dep_date + a.dep_time).localeCompare(b.dep_date + b.dep_time))
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

  $: selectedTrips = buildSnapshotTrips(selected, visits, legs)
  $: billTripsPreview = toBillTrips({ billDate: new Date().toISOString().slice(0, 10), trips: selectedTrips })
  $: totals = billTotals(selectedTrips.map((t) => makeTrip(t.legs.map((l) => mkLeg(l.fare)), '', '', t.nights, t.foodDays)))

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
        <thead><tr><th></th><th>Institute</th><th>Dates</th><th>Category</th><th>Travel</th></tr></thead>
        <tbody>
          {#each trips as t (t.id)}
            {@const pv = visits.find((v) => v.trip_id === t.id && !v.is_additional)}
            {@const tLegs = legs.filter((l) => l.trip_id === t.id)}
            <tr>
              <td><input type="checkbox" checked={selected.has(t.id)} on:change={() => toggle(t.id)} /></td>
              <td>{pv?.institute ?? '—'}</td>
              <td>{pv ? `${pv.start_date} – ${pv.end_date}` : ''}</td>
              <td>{pv?.category ?? ''}</td>
              <td>{tLegs.length} travels · {tLegs.reduce((s, l) => s + Number(l.fare), 0)}</td>
            </tr>
          {/each}
        </tbody>
      </table>

      {#if selected.size > 0}
        <h3>Preview</h3>
        {#each [...selected] as tripId (tripId)}
          {@const pv = visits.find((v) => v.trip_id === tripId && !v.is_additional)}
          {@const tLegs = legs.filter((l) => l.trip_id === tripId).sort((a, b) => (a.dep_date + a.dep_time).localeCompare(b.dep_date + b.dep_time))}
          <div class="preview-trip">
            <p><b>{purposeLineFor(pv)}</b></p>
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
  <!-- svelte-ignore a11y-click-events-have-key-events -->
  <!-- svelte-ignore a11y-no-static-element-interactions -->
  <div class="modal-backdrop" on:click|self={() => (legForm = null)}>
    <form class="card modal" on:submit|preventDefault={saveLeg}>
      <h2>{legForm.id ? 'Edit' : 'Add'} travel</h2>
      <div class="row">
        <div class="field"><label for="dd">Departure date</label><input id="dd" type="date" bind:value={legForm.dep_date} required /></div>
        <div class="field"><label for="dt">Time</label><input id="dt" type="time" bind:value={legForm.dep_time} required /></div>
      </div>
      <div class="field"><label for="dp">Departure place</label><input id="dp" type="text" list="place-list" bind:value={legForm.dep_place} required /></div>
      <div class="row">
        <div class="field"><label for="ad">Arrival date</label><input id="ad" type="date" bind:value={legForm.arr_date} required /></div>
        <div class="field"><label for="at">Time</label><input id="at" type="time" bind:value={legForm.arr_time} required /></div>
      </div>
      <div class="field"><label for="ap">Arrival place</label><input id="ap" type="text" list="place-list" bind:value={legForm.arr_place} required /></div>
      <datalist id="place-list">{#each places as p}<option value={p} />{/each}</datalist>
      <div class="field">
        <label for="md">Mode</label>
        <Dropdown id="md" bind:value={legForm.mode} options={[...Object.keys(TRANSPORT)]} />
      </div>
      {#if legForm.mode === 'Other'}
        <div class="field"><label for="om">Mode (free text)</label><input id="om" type="text" bind:value={legForm.otherMode} /></div>
        <div class="field"><label for="cl">Class</label><input id="cl" type="text" bind:value={legForm.class} /></div>
      {:else if legClasses.length}
        <div class="field"><label for="cl2">Class</label><Dropdown id="cl2" bind:value={legForm.class} options={legClasses} /></div>
      {/if}
      {#if legForm.mode !== 'N/A'}
        <div class="field"><label for="fare">Fare (Tk)</label><input id="fare" type="number" step="0.01" bind:value={legForm.fare} /></div>
      {/if}
      <div class="field"><label for="rm">Remarks</label><input id="rm" type="text" bind:value={legForm.remarks} /></div>
      <div class="field checkbox-row">
        <label><input type="checkbox" bind:checked={legForm.ticket} /> {TICKET_REMARK}</label>
      </div>
      {#if legErr}<p class="err">{legErr}</p>{/if}
      <div class="row">
        <button type="submit" class="btn btn-primary">Save</button>
        <button type="button" class="btn" on:click={() => (legForm = null)}>Cancel</button>
      </div>
    </form>
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
  .modal-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 10; }
  .modal { width: 420px; max-height: 90vh; overflow: auto; }
</style>
