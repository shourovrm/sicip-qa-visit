<!-- tours (trips): travel details CRUD + per-tour category (writes to primary visit) -->
<script>
  import { listTrips, listVisits, listLegsForTrips, listTravelPlaces, updateVisit, createLeg, updateLeg, softDeleteLeg } from '../lib/db.js'
  import { officer, isAdmin } from '../lib/auth.js'
  import { officers, officerName } from '../lib/officers.js'
  import { CATEGORY_LABELS, suggestedNights, suggestedFood } from '../lib/scoring.js'
  import { TRANSPORT } from '../lib/seeds.js'
  import Dropdown from '../components/Dropdown.svelte'

  let trips = []
  let visits = []
  let legs = []
  let places = []
  let loading = true
  let openTripId = null
  let legForm = null // {trip_id, id?, ...} being added/edited
  let err = ''

  async function load() {
    loading = true
    const [allTrips, allVisits] = await Promise.all([listTrips(), listVisits()])
    const mine = $officer?.id
    // admins see every officer's tours (matches Admin.svelte's "edit any record here" note);
    // everyone else sees only their own, same as android.
    trips = allTrips.filter((t) => $isAdmin || t.officer_id === mine).sort((a, b) => (b.started_at > a.started_at ? 1 : -1))
    visits = $isAdmin ? allVisits : allVisits.filter((v) => v.officer_id === mine)
    legs = await listLegsForTrips(trips.map((t) => t.id))
    places = await listTravelPlaces()
    loading = false
  }
  // load waits for the officer row (hard refresh: mount fires before auth resolves)
  let loadStarted = false
  $: if ($officer && !loadStarted) { loadStarted = true; load() }

  function primaryVisit(tripId) {
    return visits.find((v) => v.trip_id === tripId && !v.is_additional)
  }
  function tripLegs(tripId) {
    return legs.filter((l) => l.trip_id === tripId)
  }

  // no primary visit: fall back to the officer's name for a tour viewed by someone else
  // (admin), else the bare "Tour" placeholder. institute always wins when a primary exists.
  function tripTitle(trip, pv) {
    const isOthers = trip.officer_id !== $officer?.id
    const name = isOthers ? officerName(trip.officer_id, $officers) : null
    if (pv) return name ? `${pv.institute} — ${name}` : pv.institute
    return name ?? 'Tour'
  }

  async function setCategory(trip, category) {
    const pv = primaryVisit(trip.id)
    if (!pv) return
    const updated = await updateVisit(pv.id, { category, category_override: true })
    visits = visits.map((v) => (v.id === updated.id ? updated : v))
  }

  function newLeg(tripId) {
    legForm = { trip_id: tripId, dep_date: '', dep_time: '', dep_place: '', arr_date: '', arr_time: '', arr_place: '', mode: 'Bus', class: 'AC', fare: 0, remarks: '' }
    err = ''
  }
  function editLeg(l) {
    legForm = { ...l, mode: l.mode in TRANSPORT ? l.mode : 'Other', otherMode: l.mode in TRANSPORT ? '' : l.mode }
    err = ''
  }
  $: legClasses = legForm && legForm.mode !== 'Other' ? TRANSPORT[legForm.mode] ?? [] : []

  async function saveLeg() {
    err = ''
    try {
      const mode = legForm.mode === 'Other' ? (legForm.otherMode || 'Other') : legForm.mode
      const payload = {
        trip_id: legForm.trip_id, dep_date: legForm.dep_date, dep_time: legForm.dep_time, dep_place: legForm.dep_place,
        arr_date: legForm.arr_date, arr_time: legForm.arr_time, arr_place: legForm.arr_place,
        mode, class: legForm.class, fare: Number(legForm.fare) || 0, remarks: legForm.remarks,
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
      err = e.message
    }
  }

  async function delLeg(l) {
    if (!confirm('Delete this travel?')) return
    await softDeleteLeg(l.id)
    legs = legs.filter((x) => x.id !== l.id)
  }
</script>

<h1>Tours</h1>

{#if loading}
  <p class="muted">Loading…</p>
{:else if trips.length === 0}
  <p class="muted">No tours yet.</p>
{:else}
  {#each trips as trip (trip.id)}
    {@const pv = primaryVisit(trip.id)}
    <div class="card trip">
      <div class="spread" on:click={() => (openTripId = openTripId === trip.id ? null : trip.id)} role="button" tabindex="0" on:keydown={() => {}}>
        <div>
          <b>{tripTitle(trip, pv)}</b>
          <span class="muted"> — {trip.status} — started {new Date(trip.started_at).toLocaleDateString()}</span>
        </div>
        <span class="muted">{openTripId === trip.id ? '▲' : '▼'}</span>
      </div>

      {#if openTripId === trip.id}
        <div class="body">
          {#if pv}
            <div class="row-wrap cat-row">
              <div class="field" style="width:260px">
                <label for="cat-{trip.id}">Category</label>
                <Dropdown id="cat-{trip.id}" value={pv.category} options={Object.entries(CATEGORY_LABELS)} on:change={(e) => setCategory(trip, e.target.value)} />
              </div>
              <div class="muted derived">Nights: {suggestedNights(pv.category)} · Food days: {suggestedFood(pv.category)}</div>
            </div>
          {:else}
            <p class="muted">No primary visit attached.</p>
          {/if}

          <h3>Travel details</h3>
          <table>
            <thead><tr><th>Dep</th><th>Arr</th><th>Mode</th><th>Class</th><th>Fare</th><th>Remarks</th><th></th></tr></thead>
            <tbody>
              {#if tripLegs(trip.id).length === 0}<tr><td colspan="7" class="muted">No travel yet.</td></tr>{/if}
              {#each tripLegs(trip.id) as l (l.id)}
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
          <button class="btn" on:click={() => newLeg(trip.id)}>+ Add travel</button>
        </div>
      {/if}
    </div>
  {/each}
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
      <div class="field"><label for="fare">Fare (Tk)</label><input id="fare" type="number" step="0.01" bind:value={legForm.fare} /></div>
      <div class="field"><label for="rm">Remarks</label><input id="rm" type="text" bind:value={legForm.remarks} /></div>
      {#if err}<p class="err">{err}</p>{/if}
      <div class="row">
        <button type="submit" class="btn btn-primary">Save</button>
        <button type="button" class="btn" on:click={() => (legForm = null)}>Cancel</button>
      </div>
    </form>
  </div>
{/if}

<style>
  h1 { color: var(--primary); }
  .trip { margin-bottom: 12px; padding: 0; overflow: hidden; }
  .trip > .spread { padding: 14px 16px; cursor: pointer; }
  .body { padding: 0 16px 16px; border-top: 1px solid var(--outline); }
  .cat-row { align-items: flex-end; margin: 12px 0; }
  .derived { padding-bottom: 8px; }
  h3 { font-size: 14px; color: var(--muted); text-transform: uppercase; letter-spacing: 0.03em; }
  .modal-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 10; }
  .modal { width: 420px; max-height: 90vh; overflow: auto; }
</style>
