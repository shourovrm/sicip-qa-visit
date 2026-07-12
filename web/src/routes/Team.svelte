<!-- status list (derived: on tour / on leave / in office) + rank leaderboard (overall / last month) -->
<script>
  import { onMount } from 'svelte'
  import { listTrips, listVisits, listLeaves } from '../lib/db.js'
  import { officers } from '../lib/officers.js'
  import { officer } from '../lib/auth.js'
  import { rank, lastDayOfPreviousMonth } from '../lib/scoring.js'
  import Pill from '../components/Pill.svelte'

  let trips = [], visits = [], leaves = []
  let loading = true
  let tab = 'status' // status | rank
  let period = 'overall' // overall | lastmonth

  const today = new Date().toISOString().slice(0, 10)
  const lastMonthCutoff = lastDayOfPreviousMonth(today)

  onMount(async () => {
    ;[trips, visits, leaves] = await Promise.all([listTrips(), listVisits(), listLeaves()])
    loading = false
  })

  function statusFor(officerId) {
    const activeTrip = trips.find((t) => t.officer_id === officerId && t.status === 'active')
    if (activeTrip) {
      const pv = visits.find((v) => v.trip_id === activeTrip.id && !v.is_additional)
      return { tone: 'visit', label: 'On tour', detail: pv ? `${pv.institute}, ${pv.district}` : '', since: activeTrip.started_at }
    }
    // explicit lifecycle: only a started leave counts (matches android TeamStatus.kt)
    const onLeave = leaves.find((l) => l.officer_id === officerId && l.status === 'started')
    if (onLeave) return { tone: 'leave', label: 'On leave', detail: onLeave.type, since: onLeave.end_date }
    return { tone: 'office', label: 'In office', detail: '', since: '' }
  }

  // "Last month" = cumulative standings as of the last day of the previous month, not points
  // earned during that month alone -- mirrors android TeamScreen/Rank.kt exactly.
  $: rankVisits = visits
    .filter((v) => period === 'overall' || v.start_date <= lastMonthCutoff)
    .map((v) => ({ officerId: v.officer_id, category: v.category, deleted: v.deleted }))
  $: ranked = rank(rankVisits)
  $: rankedWithZeros = [
    ...ranked,
    ...$officers.filter((o) => !ranked.some(([id]) => id === o.id)).map((o) => [o.id, 0]),
  ]
</script>

<h1>Team</h1>

<div class="seg">
  <button class:active={tab === 'status'} on:click={() => (tab = 'status')}>Status</button>
  <button class:active={tab === 'rank'} on:click={() => (tab = 'rank')}>Rank</button>
</div>

{#if loading}
  <p class="muted">Loading…</p>
{:else if tab === 'status'}
  <table class="card">
    <thead><tr><th>Officer</th><th>Status</th><th>Detail</th></tr></thead>
    <tbody>
      {#each $officers as o (o.id)}
        {@const s = statusFor(o.id)}
        <tr class:me={o.id === $officer?.id}>
          <td>{o.name}</td>
          <td><Pill tone={s.tone}>{s.label}</Pill></td>
          <td class="muted">{s.detail}</td>
        </tr>
      {/each}
    </tbody>
  </table>
{:else}
  <div class="seg" style="margin-bottom:12px">
    <button class:active={period === 'overall'} on:click={() => (period = 'overall')}>Overall</button>
    <button class:active={period === 'lastmonth'} on:click={() => (period = 'lastmonth')}>Last month</button>
  </div>
  <table class="card">
    <thead><tr><th>#</th><th>Officer</th><th>Points</th></tr></thead>
    <tbody>
      {#each rankedWithZeros.sort((a, b) => b[1] - a[1]) as [id, pts], i}
        <tr class:me={id === $officer?.id}><td>{i + 1}</td><td>{$officers.find((o) => o.id === id)?.name ?? '—'}</td><td>{pts}</td></tr>
      {/each}
    </tbody>
  </table>
{/if}

<style>
  h1 { color: var(--primary); }
  .seg { display: inline-flex; gap: 4px; background: var(--surface); border: 1px solid var(--outline); border-radius: var(--radius-pill); padding: 3px; margin-bottom: 16px; }
  .seg button { border: none; background: none; padding: 6px 14px; border-radius: var(--radius-pill); cursor: pointer; font-weight: 700; color: var(--muted); }
  .seg button.active { background: var(--primary); color: var(--on-primary); }
  tr.me td { background: var(--status-visit-bg); font-weight: 700; }
</style>
