<!-- Scheduled|Completed tabs, Personal|Team scope, filters, edit own (admin edits any), soft delete -->
<script>
  import { onMount } from 'svelte'
  import { listVisits, createVisit, updateVisit, softDeleteVisit } from '../lib/db.js'
  import { officer, isAdmin } from '../lib/auth.js'
  import { officers, officerName } from '../lib/officers.js'
  import { DISTRICTS, ASSOCIATIONS, PURPOSES } from '../lib/seeds.js'
  import { CATEGORY_LABELS, points } from '../lib/scoring.js'
  import Dropdown from '../components/Dropdown.svelte'
  import VisitModal from '../components/VisitModal.svelte'

  let visits = []
  let loading = true
  let tab = 'done' // scheduled | done -- Completed is the default
  let scope = 'mine' // mine | team
  let fDistrict = '', fCategory = '', fPurpose = '', fOfficer = '', fFrom = '', fTo = '', fSearch = ''

  // case-insensitive contains across the visible-ish text fields; officer name only in team
  // scope (officerList null in mine scope skips that field) -- null-safe on every field.
  function matchesSearch(v, term, officerList) {
    if (!term) return true
    const t = term.toLowerCase()
    const fields = [v.institute, v.association, v.purpose, v.district, v.ref_no]
    if (officerList) fields.push(officerName(v.officer_id, officerList))
    return fields.some((f) => (f ?? '').toLowerCase().includes(t))
  }
  let editing = null // visit being created/edited (copy); editing.id null == create mode
  let viewing = false // true when editing is open read-only (other officer's row)
  let saveErr = ''

  async function load() {
    loading = true
    visits = await listVisits()
    loading = false
  }
  onMount(load)

  $: mine = $officer?.id
  $: filtered = visits
    .filter((v) => v.status === tab)
    .filter((v) => (scope === 'mine' ? v.officer_id === mine : true))
    .filter((v) => !fDistrict || v.district === fDistrict)
    .filter((v) => !fCategory || v.category === fCategory)
    .filter((v) => !fPurpose || v.purpose === fPurpose)
    .filter((v) => !fOfficer || v.officer_id === fOfficer)
    .filter((v) => !fFrom || v.start_date >= fFrom)
    .filter((v) => !fTo || v.start_date <= fTo)
    .filter((v) => matchesSearch(v, fSearch, scope === 'team' ? $officers : null))

  // summary line, deps on filtered directly (no wrapper fn) so svelte tracks it right
  $: summaryPts = filtered.reduce((sum, v) => sum + points(v.category), 0)

  function canEdit(v) {
    return v.officer_id === mine || $isAdmin
  }

  function startEdit(v) {
    editing = { ...v }
    viewing = false
    saveErr = ''
  }

  function view(v) {
    editing = { ...v }
    viewing = true
    saveErr = ''
  }

  const todayIso = new Date().toISOString().slice(0, 10)
  function startCreate() {
    editing = {
      id: null, institute: '', association: ASSOCIATIONS[0], district: DISTRICTS[0], dhaka_metro: null,
      purpose: PURPOSES[0], ref_no: '', ref_date: '', start_date: todayIso, end_date: todayIso,
      category: 'N/A', status: 'scheduled', remarks: '',
    }
    viewing = false
    saveErr = ''
  }

  async function save() {
    saveErr = ''
    if (editing.end_date < editing.start_date) { saveErr = 'End date must be on/after start date'; return }
    try {
      const patch = {
        institute: editing.institute, association: editing.association, district: editing.district,
        dhaka_metro: editing.district === 'Dhaka' ? editing.dhaka_metro : null,
        purpose: editing.purpose, ref_no: editing.ref_no || null, ref_date: editing.ref_date || null,
        start_date: editing.start_date, end_date: editing.end_date, remarks: editing.remarks || null,
      }
      // category only editable once the visit is done, matching android
      if (editing.status === 'done') {
        patch.category = editing.category
        patch.category_override = true
      }
      if (editing.id) {
        const updated = await updateVisit(editing.id, patch)
        visits = visits.map((v) => (v.id === updated.id ? updated : v))
      } else {
        // new visits save with no category (picked later when the visit is done) -- status
        // stays scheduled.
        const created = await createVisit({ ...patch, officer_id: mine, status: 'scheduled', category: 'N/A', category_override: false })
        visits = [created, ...visits]
      }
      editing = null
    } catch (e) {
      saveErr = e.message
    }
  }

  async function del(v) {
    // visit on a tour: deleting it also drops the tour from bill prep, warn loudly
    const msg = v.trip_id
      ? `Delete visit "${v.institute}"? It's part of a tour -- deleting removes it from the tour, and the tour will disappear from bill preparation.`
      : `Delete visit "${v.institute}"?`
    if (!confirm(msg)) return
    await softDeleteVisit(v.id)
    visits = visits.filter((x) => x.id !== v.id)
  }
</script>

<h1>Visits</h1>

<div class="row-wrap tabs">
  <div class="seg">
    <button class:active={tab === 'scheduled'} on:click={() => (tab = 'scheduled')}>Scheduled</button>
    <button class:active={tab === 'done'} on:click={() => (tab = 'done')}>Completed</button>
  </div>
  <div class="seg">
    <button class:active={scope === 'mine'} on:click={() => (scope = 'mine')}>Personal</button>
    <button class:active={scope === 'team'} on:click={() => (scope = 'team')}>Team</button>
  </div>
  {#if tab === 'scheduled'}
    <button class="btn btn-primary" on:click={startCreate}>＋ Schedule visit</button>
  {/if}
</div>

<div class="card row-wrap filters">
  <input type="text" bind:value={fSearch} placeholder="Search" />
  <Dropdown bind:value={fDistrict} options={DISTRICTS} placeholder="All districts" />
  <Dropdown bind:value={fCategory} options={Object.entries(CATEGORY_LABELS)} placeholder="All categories" />
  <Dropdown bind:value={fPurpose} options={PURPOSES} placeholder="All purposes" />
  {#if scope === 'team'}
    <Dropdown bind:value={fOfficer} options={$officers.map((o) => [o.id, o.name])} placeholder="All officers" />
  {/if}
  <input type="date" bind:value={fFrom} title="From" />
  <input type="date" bind:value={fTo} title="To" />
</div>

{#if loading}
  <p class="muted">Loading…</p>
{:else}
  <p class="muted summary">{filtered.length} visits · {summaryPts} pts</p>
  <table class="card">
    <thead>
      <tr>
        <th>Institute</th><th>Association</th><th>District</th><th>Purpose</th>
        <th>Start</th><th>End</th><th>Category</th>{#if scope === 'team'}<th>Officer</th>{/if}<th></th>
      </tr>
    </thead>
    <tbody>
      {#if filtered.length === 0}<tr><td colspan="9" class="muted">No visits.</td></tr>{/if}
      {#each filtered as v (v.id)}
        <tr>
          <td>{v.institute}</td>
          <td>{v.association}</td>
          <td>{v.district}{v.district === 'Dhaka' ? (v.dhaka_metro ? ' (metro)' : ' (outside metro)') : ''}</td>
          <td>{v.purpose}</td>
          <td>{v.start_date}</td>
          <td>{v.end_date}</td>
          <td>{v.category}</td>
          {#if scope === 'team'}<td>{$officers.find((o) => o.id === v.officer_id)?.name ?? ''}</td>{/if}
          <td>
            {#if canEdit(v)}
              <button class="btn-link" on:click={() => startEdit(v)}>Edit</button>
              <button class="btn-link" on:click={() => del(v)}>Delete</button>
            {:else}
              <button class="btn-link" on:click={() => view(v)}>View</button>
            {/if}
          </td>
        </tr>
      {/each}
    </tbody>
  </table>
{/if}

{#if editing}
  <VisitModal editing={editing} visits={visits} saveErr={saveErr} readonly={viewing}
    on:save={save} on:cancel={() => { editing = null; viewing = false }} />
{/if}

<style>
  h1 { color: var(--primary); }
  .tabs { justify-content: space-between; margin-bottom: 12px; }
  .seg { display: flex; gap: 4px; background: var(--surface); border: 1px solid var(--outline); border-radius: var(--radius-pill); padding: 3px; }
  .seg button { border: none; background: none; padding: 6px 14px; border-radius: var(--radius-pill); cursor: pointer; font-weight: 700; color: var(--muted); }
  .seg button.active { background: var(--primary); color: var(--on-primary); }
  .filters { margin-bottom: 16px; }
  .summary { margin: -4px 0 8px; }
  .filters :global(select), .filters input { width: auto; }
</style>
