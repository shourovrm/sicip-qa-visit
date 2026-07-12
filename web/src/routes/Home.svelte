<!-- home dashboard: stat tiles, this-month line, active tour, upcoming visits. mirrors
     android ui/home/HomeScreen.kt (points/rank/visits + active trip + upcoming list). -->
<script>
  import { listVisits, listTrips, createVisit, createLeg, listTravelPlaces } from '../lib/db.js'
  import { officer } from '../lib/auth.js'
  import { officers } from '../lib/officers.js'
  import { totalPoints, rank, monthSummary, autoCategoryFromDates } from '../lib/scoring.js'
  import { DISTRICTS, ASSOCIATIONS, PURPOSES } from '../lib/seeds.js'
  import { newLegDraft, legPayload } from '../lib/legs.js'
  import LegModal from '../components/LegModal.svelte'
  import VisitModal from '../components/VisitModal.svelte'
  import Pill from '../components/Pill.svelte'

  let visits = [], trips = []
  let loading = true

  // active-tour "Add travel" (LegModal) / "Add visit" + upcoming-card "+ Visit" (VisitModal)
  let legForm = null, legErr = '', places = []
  let editing = null, saveErr = ''

  async function load() {
    ;[visits, trips] = await Promise.all([listVisits(), listTrips()])
    loading = false
  }
  // stats wait for the officer row (hard refresh: mount fires before auth resolves) --
  // same gating pattern as Tours.svelte/Profile.svelte, needed because onMount races auth.
  let loadStarted = false
  $: if ($officer && !loadStarted) { loadStarted = true; load() }

  $: mine = $officer?.id
  $: mineVisits = visits.filter((v) => v.officer_id === mine)
  $: myPoints = totalPoints(mineVisits.map((v) => ({ officerId: v.officer_id, category: v.category, deleted: v.deleted })))

  // same "zero-point officers get a last-place slot" trick as Team.svelte's rank tab.
  $: ranked = rank(visits.map((v) => ({ officerId: v.officer_id, category: v.category, deleted: v.deleted })))
  $: rankedWithZeros = [
    ...ranked,
    ...$officers.filter((o) => !ranked.some(([id]) => id === o.id)).map((o) => [o.id, 0]),
  ].sort((a, b) => b[1] - a[1])
  $: myRankPos = rankedWithZeros.findIndex(([id]) => id === mine) + 1

  $: visitCount = mineVisits.filter((v) => v.status === 'done').length

  const yearMonth = new Date().toISOString().slice(0, 7)
  $: [monthVisits, monthPoints] = monthSummary(
    mineVisits.map((v) => ({ startDate: v.start_date, category: v.category, deleted: v.deleted })),
    yearMonth,
  )

  $: activeTrip = trips.find((t) => t.officer_id === mine && t.status === 'active')
  $: activeTripVisit = activeTrip ? visits.find((v) => v.trip_id === activeTrip.id && !v.is_additional) : null
  // ONGOING: every visit attached to the active tour (primary + any added mid-tour). UPCOMING
  // excludes those -- same split as android T4. activeTrip referenced directly (not through a
  // helper) so svelte's $: sees it.
  $: ongoing = activeTrip ? mineVisits.filter((v) => v.trip_id === activeTrip.id) : []
  $: upcoming = mineVisits
    .filter((v) => v.status === 'scheduled' && v.trip_id !== activeTrip?.id)
    .sort((a, b) => a.start_date.localeCompare(b.start_date))

  // -- Add travel (active tour) --------------------------------------------------------------
  async function newLeg(tripId) {
    if (!places.length) places = await listTravelPlaces()
    legForm = newLegDraft(tripId)
    legErr = ''
  }
  async function saveLeg() {
    legErr = ''
    try {
      await createLeg(legPayload(legForm))
      legForm = null
    } catch (e) {
      legErr = e.message
    }
  }

  // -- Add visit (attach to active tour) / + Visit (plain scheduled visit prefilled from a card)
  const todayIso = new Date().toISOString().slice(0, 10)
  function addVisitToTrip() {
    editing = {
      id: null, institute: '', association: ASSOCIATIONS[0], district: DISTRICTS[0], dhaka_metro: null,
      purpose: PURPOSES[0], ref_no: '', ref_date: '', start_date: todayIso, end_date: todayIso,
      category: 'N/A', status: 'scheduled', remarks: '', trip_id: activeTrip.id, is_additional: true,
    }
    saveErr = ''
  }
  function scheduleFromCard(v) {
    editing = {
      id: null, institute: '', association: ASSOCIATIONS[0], district: v.district, dhaka_metro: null,
      purpose: PURPOSES[0], ref_no: '', ref_date: '', start_date: v.start_date, end_date: v.start_date,
      category: 'N/A', status: 'scheduled', remarks: '', trip_id: null, is_additional: false,
    }
    saveErr = ''
  }
  // same create-branch shape as Visits.svelte's startCreate/save (auto category, status scheduled)
  async function saveVisit() {
    saveErr = ''
    try {
      const patch = {
        institute: editing.institute, association: editing.association, district: editing.district,
        dhaka_metro: editing.district === 'Dhaka' ? editing.dhaka_metro : null,
        purpose: editing.purpose, ref_no: editing.ref_no || null, ref_date: editing.ref_date || null,
        start_date: editing.start_date, end_date: editing.end_date, remarks: editing.remarks || null,
        trip_id: editing.trip_id ?? null, is_additional: editing.is_additional ?? false,
      }
      const auto = autoCategoryFromDates(editing.start_date, editing.end_date, editing.district, editing.dhaka_metro)
      const created = await createVisit({ ...patch, officer_id: mine, status: 'scheduled', category: auto, category_override: false })
      visits = [created, ...visits]
      editing = null
    } catch (e) {
      saveErr = e.message
    }
  }
</script>

<h1>Home</h1>

{#if loading}
  <p class="muted">Loading…</p>
{:else}
  <div class="row-wrap stats">
    <div class="card stat"><b>{myPoints}</b><span class="muted">My points</span></div>
    <div class="card stat"><b>#{myRankPos || '—'} / {$officers.length}</b><span class="muted">Rank</span></div>
    <div class="card stat"><b>{visitCount}</b><span class="muted">Visits</span></div>
  </div>
  <p class="muted month">This month: {monthVisits} visits · {monthPoints} pts</p>

  {#if activeTrip}
    <div class="card active-trip">
      <div class="label">Active tour</div>
      <h2>{activeTripVisit?.institute ?? 'Ad-hoc tour'}</h2>
      <p>Started {new Date(activeTrip.started_at).toLocaleString()}</p>
      <div class="row active-actions">
        <button class="btn btn-secondary" on:click={() => newLeg(activeTrip.id)}>Add travel</button>
        <button class="btn btn-secondary" on:click={addVisitToTrip}>Add visit</button>
      </div>
    </div>

    <h2 class="section">Ongoing</h2>
    {#each ongoing as v (v.id)}
      <div class="card upcoming-row">
        <div>
          <b>{v.institute}</b>
          <span class="muted">{v.district} · {v.start_date}</span>
        </div>
        <Pill tone="visit">ONGOING</Pill>
      </div>
    {/each}
  {/if}

  <h2 class="section">Upcoming visits</h2>
  {#if upcoming.length === 0}
    <p class="muted">No upcoming visits.</p>
  {:else}
    {#each upcoming as v (v.id)}
      <div class="card upcoming-row">
        <div>
          <b>{v.institute}</b>
          <span class="muted">{v.district} · {v.start_date}</span>
        </div>
        <button class="btn-link" on:click={() => scheduleFromCard(v)}>+ Visit</button>
      </div>
    {/each}
  {/if}
{/if}

{#if legForm}
  <LegModal legForm={legForm} places={places} legErr={legErr} on:save={saveLeg} on:cancel={() => (legForm = null)} />
{/if}
{#if editing}
  <VisitModal editing={editing} visits={visits} saveErr={saveErr} on:save={saveVisit} on:cancel={() => (editing = null)} />
{/if}

<style>
  h1 { color: var(--primary); }
  .stats { margin-bottom: 12px; }
  .stat { flex: 1; min-width: 100px; display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 14px; }
  .stat b { font-size: 22px; }
  .stat span { font-size: 12px; }
  .month { margin: 0 0 16px; }
  .active-trip { background: var(--primary); color: var(--on-primary); margin-bottom: 16px; }
  .active-trip .label { font-size: 11px; letter-spacing: 0.03em; text-transform: uppercase; opacity: 0.85; }
  .active-trip h2 { margin: 4px 0; font-size: 18px; }
  .active-trip p { margin: 0 0 12px; opacity: 0.85; }
  .active-actions { gap: 8px; }
  .section { font-size: 14px; color: var(--muted); text-transform: uppercase; letter-spacing: 0.03em; margin: 16px 0 8px; }
  .upcoming-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
</style>
