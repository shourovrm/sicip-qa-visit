<!-- tours (trips): read-only tour log. category + travel CRUD moved to Bills (bill prep) -->
<script>
  import { listTrips, listVisits, listLegsForTrips } from '../lib/db.js'
  import { officer, isAdmin } from '../lib/auth.js'
  import { officers } from '../lib/officers.js'
  import { CATEGORY_LABELS, suggestedNights, suggestedFood } from '../lib/scoring.js'
  import Dropdown from '../components/Dropdown.svelte'

  let trips = []
  let visits = []
  let legs = []
  let loading = true
  let openTripId = null
  let officerFilter = '' // admin only; '' = own tours

  async function load() {
    loading = true
    const [allTrips, allVisits] = await Promise.all([listTrips(), listVisits()])
    const mine = $officer?.id
    const target = $isAdmin && officerFilter ? officerFilter : mine
    trips = allTrips.filter((t) => t.officer_id === target).sort((a, b) => (b.started_at > a.started_at ? 1 : -1))
    visits = allVisits.filter((v) => v.officer_id === target)
    legs = await listLegsForTrips(trips.map((t) => t.id))
    loading = false
  }
  // load waits for the officer row (hard refresh: mount fires before auth resolves)
  let loadStarted = false
  $: if ($officer && !loadStarted) { loadStarted = true; load() }

  function onOfficerFilterChange(e) {
    officerFilter = e.target.value
    load()
  }
</script>

<h1>Tours</h1>

{#if $isAdmin}
  <div class="field" style="width:260px">
    <label for="officer-filter">Officer</label>
    <Dropdown id="officer-filter" value={officerFilter} options={[['', 'My tours'], ...$officers.map((o) => [o.id, o.name])]} on:change={onOfficerFilterChange} />
  </div>
{/if}

{#if loading}
  <p class="muted">Loading…</p>
{:else if trips.length === 0}
  <p class="muted">No tours yet.</p>
{:else}
  {#each trips as trip (trip.id)}
    <!-- pv/tLegs inline `visits`/`legs` lookups (not helper fns) so svelte's compiler sees this
         each-block depends on those variables -- a helper *call* hides the dependency from
         svelte's static analysis (repo gotcha, see DECISIONS.md). -->
    {@const pv = visits.find((v) => v.trip_id === trip.id && !v.is_additional)}
    {@const tLegs = legs.filter((l) => l.trip_id === trip.id)}
    <div class="card trip">
      <div class="spread" on:click={() => (openTripId = openTripId === trip.id ? null : trip.id)} role="button" tabindex="0" on:keydown={() => {}}>
        <div>
          <b>{pv?.institute ?? 'Tour'}</b>
          <span class="muted"> — {trip.status} — started {new Date(trip.started_at).toLocaleDateString()}</span>
        </div>
        <span class="muted">{openTripId === trip.id ? '▲' : '▼'}</span>
      </div>

      {#if openTripId === trip.id}
        <div class="body">
          {#if pv}
            <div class="row-wrap cat-row">
              <div class="muted">Category: {CATEGORY_LABELS[pv.category] ?? pv.category}</div>
              <div class="muted derived">Nights: {suggestedNights(pv.category)} · Food days: {suggestedFood(pv.category)}</div>
            </div>
          {:else}
            <p class="muted">No primary visit attached.</p>
          {/if}

          <h3>Travel details</h3>
          <table>
            <thead><tr><th>Dep</th><th>Arr</th><th>Mode</th><th>Class</th><th>Fare</th><th>Remarks</th></tr></thead>
            <tbody>
              {#if tLegs.length === 0}<tr><td colspan="6" class="muted">No travel yet.</td></tr>{/if}
              {#each tLegs as l (l.id)}
                <tr>
                  <td>{l.dep_date} {l.dep_time}<br /><span class="muted">{l.dep_place}</span></td>
                  <td>{l.arr_date} {l.arr_time}<br /><span class="muted">{l.arr_place}</span></td>
                  <td>{l.mode}</td>
                  <td>{l.class ?? ''}</td>
                  <td>{l.fare}</td>
                  <td>{l.remarks ?? ''}</td>
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
      {/if}
    </div>
  {/each}
{/if}

<style>
  h1 { color: var(--primary); }
  .trip { margin-bottom: 12px; padding: 0; overflow: hidden; }
  .trip > .spread { padding: 14px 16px; cursor: pointer; }
  .body { padding: 0 16px 16px; border-top: 1px solid var(--outline); }
  .cat-row { align-items: flex-end; margin: 12px 0; }
  .derived { padding-bottom: 8px; }
  h3 { font-size: 14px; color: var(--muted); text-transform: uppercase; letter-spacing: 0.03em; }
</style>
