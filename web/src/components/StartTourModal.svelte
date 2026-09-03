<!-- start-tour dialog: mirrors android StartTrip.kt -- pick start date+time, optionally attach
     scheduled visits (or create one inline via VisitModal -- reuses the same nested-render trick
     StartTrip.kt uses with VisitForm so `selected` survives the round trip), optionally inform a
     colleague. Owns persistence itself (createTrip + attach), same as Home.svelte's own
     saveVisit/saveLeg, so the parent only needs to reload on 'started'. -->
<script>
  import { createEventDispatcher } from 'svelte'
  import { createTrip, createVisit, updateVisit } from '../lib/db.js'
  import { DISTRICTS, ASSOCIATIONS, PURPOSES } from '../lib/seeds.js'
  import Dropdown from '../components/Dropdown.svelte'
  import VisitModal from '../components/VisitModal.svelte'

  export let officerId
  export let visits = [] // officer's visits, for candidate list + VisitModal autosuggest
  export let officers = [] // full officer directory -- self filtered out below
  export let preselectedVisitId = null

  const dispatch = createEventDispatcher()

  const pad = (n) => String(n).padStart(2, '0')
  const now = new Date()
  let startDate = now.toISOString().slice(0, 10)
  let startTime = `${pad(now.getHours())}:${pad(now.getMinutes())}:00`
  let selected = new Set(preselectedVisitId ? [preselectedVisitId] : [])
  let informedOfficerId = null
  let extraVisits = [] // visits created via "+ New visit" this session (candidates prop is read-only)
  let newVisitDraft = null
  let err = ''

  $: allVisits = [...visits, ...extraVisits]
  $: candidates = allVisits.filter((v) => v.officer_id === officerId && v.status === 'scheduled' && !v.trip_id)
  $: colleagues = officers.filter((o) => o.id !== officerId)

  function toggle(id) {
    if (selected.has(id)) selected.delete(id)
    else selected.add(id)
    selected = selected // reassign -- Set mutation needs a nudge for svelte reactivity
  }

  // "+ New visit" opens VisitModal inline (same nested-composition idea as StartTrip.kt) --
  // on save, the fresh visit auto-joins `selected`.
  function openNewVisit() {
    newVisitDraft = {
      id: null, institute: '', association: ASSOCIATIONS[0], district: DISTRICTS[0], dhaka_metro: null,
      purpose: PURPOSES[0], ref_no: '', ref_date: '', start_date: startDate, end_date: startDate,
      category: 'N/A', status: 'scheduled', remarks: '', trip_id: null, is_additional: false,
    }
    err = ''
  }
  async function saveNewVisit() {
    err = ''
    if (newVisitDraft.end_date < newVisitDraft.start_date) { err = 'End date must be on/after start date'; return }
    try {
      const d = newVisitDraft
      const patch = {
        institute: d.institute, association: d.association, district: d.district,
        dhaka_metro: d.district === 'Dhaka' ? d.dhaka_metro : null,
        purpose: d.purpose, ref_no: d.ref_no || null, ref_date: d.ref_date || null,
        start_date: d.start_date, end_date: d.end_date, remarks: d.remarks || null,
        trip_id: null, is_additional: false,
      }
      const created = await createVisit({ ...patch, officer_id: officerId, status: 'scheduled', category: 'N/A', category_override: false })
      extraVisits = [...extraVisits, created]
      selected.add(created.id)
      selected = selected
      newVisitDraft = null
    } catch (e) {
      err = e.message
    }
  }

  async function start() {
    err = ''
    try {
      const tripId = crypto.randomUUID()
      await createTrip({
        id: tripId,
        officer_id: officerId,
        status: 'active',
        started_at: `${startDate}T${startTime}Z`,
        informed_officer_id: informedOfficerId,
      })
      await Promise.all([...selected].map((id) => updateVisit(id, { trip_id: tripId })))
      dispatch('started', tripId)
    } catch (e) {
      err = e.message
    }
  }
</script>

{#if newVisitDraft}
  <VisitModal editing={newVisitDraft} visits={allVisits} saveErr={err} on:save={saveNewVisit} on:cancel={() => (newVisitDraft = null)} />
{:else}
  <!-- svelte-ignore a11y-click-events-have-key-events -->
  <!-- svelte-ignore a11y-no-static-element-interactions -->
  <div class="modal-backdrop" on:click|self={() => dispatch('cancel')}>
    <form class="card modal" on:submit|preventDefault={start}>
      <h2>Start tour</h2>
      <div class="row">
        <div class="field"><label for="st-d">Start date</label><input id="st-d" type="date" bind:value={startDate} required /></div>
        <div class="field"><label for="st-t">Start time</label><input id="st-t" type="time" bind:value={startTime} required /></div>
      </div>

      <div class="field">
        <span class="field-label">Attach scheduled visits (optional)</span>
        {#each candidates as v (v.id)}
          <label class="check-row">
            <input type="checkbox" checked={selected.has(v.id)} on:change={() => toggle(v.id)} />
            {v.institute} · {v.district} · {v.start_date}
          </label>
        {/each}
        <button type="button" class="btn" on:click={openNewVisit}>+ New visit</button>
      </div>

      <div class="field">
        <label for="st-inf">Inform a colleague (optional)</label>
        <Dropdown id="st-inf" bind:value={informedOfficerId} options={[[null, 'None (no one)'], ...colleagues.map((o) => [o.id, o.name])]} />
      </div>

      {#if err}<p class="err">{err}</p>{/if}
      <div class="row">
        <button type="submit" class="btn btn-primary">Start tour</button>
        <button type="button" class="btn" on:click={() => dispatch('cancel')}>Cancel</button>
      </div>
    </form>
  </div>
{/if}

<style>
  h2 { font-size: 15px; margin: 0 0 12px; }
  .modal-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 10; }
  .modal { width: 420px; max-height: 90vh; overflow: auto; }
  .check-row { display: block; font-weight: 400; font-size: 14px; margin: 6px 0; }
  .field-label { display: block; font-size: 13px; font-weight: 700; color: var(--muted); margin-bottom: 4px; }
</style>
