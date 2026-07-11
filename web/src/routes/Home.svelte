<!-- home dashboard: stat tiles, this-month line, active tour, upcoming visits. mirrors
     android ui/home/HomeScreen.kt (points/rank/visits + active trip + upcoming list). -->
<script>
  import { listVisits, listTrips } from '../lib/db.js'
  import { officer } from '../lib/auth.js'
  import { officers } from '../lib/officers.js'
  import { totalPoints, rank, monthSummary } from '../lib/scoring.js'

  let visits = [], trips = []
  let loading = true

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
  $: upcoming = mineVisits.filter((v) => v.status === 'scheduled').sort((a, b) => a.start_date.localeCompare(b.start_date))
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
    </div>
  {/if}

  <h2 class="section">Upcoming visits</h2>
  {#if upcoming.length === 0}
    <p class="muted">No upcoming visits.</p>
  {:else}
    {#each upcoming as v (v.id)}
      <div class="card upcoming-row">
        <b>{v.institute}</b>
        <span class="muted">{v.district} · {v.start_date}</span>
      </div>
    {/each}
  {/if}
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
  .active-trip p { margin: 0; opacity: 0.85; }
  .section { font-size: 14px; color: var(--muted); text-transform: uppercase; letter-spacing: 0.03em; margin: 16px 0 8px; }
  .upcoming-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
</style>
