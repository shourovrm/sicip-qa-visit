<!-- end-tour dialog: mirrors android FinishTrip.kt -- asks end date+time (prefilled now,
     editable), officer picks the primary visit's category manually (no auto-suggestion), then
     marks every attached visit done (only the primary gets category/category_override changed --
     visit dates are independent of tour dates, so end_date is never written here) and the trip
     finished. -->
<script>
  import { createEventDispatcher } from 'svelte'
  import { updateVisit, updateTrip } from '../lib/db.js'
  import { POINTS, CATEGORY_LABELS, points } from '../lib/scoring.js'
  import Dropdown from '../components/Dropdown.svelte'

  export let trip
  export let visits = [] // this trip's attached visits (parent's "ongoing" list)

  const dispatch = createEventDispatcher()
  const CATEGORIES = Object.keys(POINTS)

  // primary = first non-additional visit, else fall back to the first one at all (TripMath.kt).
  const primary = visits.find((v) => !v.is_additional) ?? visits[0] ?? null

  const pad = (n) => String(n).padStart(2, '0')
  const now = new Date()
  let endDate = now.toISOString().slice(0, 10)
  let endTime = `${pad(now.getHours())}:${pad(now.getMinutes())}:00`
  let category = '' // must be chosen -- no auto-category any more
  let err = ''

  $: finishedAt = `${endDate}T${endTime}Z`
  // ISO strings, same zero-padded shape -- lexicographic compare works.
  $: endBeforeStart = finishedAt < trip.started_at
  $: canSubmit = !endBeforeStart && (primary === null || category)

  async function finish() {
    err = ''
    try {
      await Promise.all(
        visits.map((v) =>
          updateVisit(v.id, v.id === primary?.id
            ? { category, category_override: true, status: 'done' }
            : { status: 'done' }),
        ),
      )
      await updateTrip(trip.id, { status: 'finished', finished_at: finishedAt })
      dispatch('finished')
    } catch (e) {
      err = e.message
    }
  }
</script>

<!-- svelte-ignore a11y-click-events-have-key-events -->
<!-- svelte-ignore a11y-no-static-element-interactions -->
<div class="modal-backdrop" on:click|self={() => dispatch('cancel')}>
  <form class="card modal" on:submit|preventDefault={finish}>
    <h2>End tour</h2>
    <div class="row">
      <div class="field"><label for="et-d">End date</label><input id="et-d" type="date" bind:value={endDate} required /></div>
      <div class="field"><label for="et-t">End time</label><input id="et-t" type="time" bind:value={endTime} required /></div>
    </div>
    {#if endBeforeStart}<p class="err">End must be after tour start</p>{/if}

    {#if primary === null}
      <p class="muted">No visits attached -- this tour will end with none.</p>
    {:else}
      <p>Primary visit: {primary.institute}</p>
      <div class="field">
        <label for="et-cat">Category</label>
        <Dropdown
          id="et-cat"
          bind:value={category}
          placeholder="Pick category"
          options={CATEGORIES.map((c) => [c, CATEGORY_LABELS[c] ?? c])}
        />
      </div>
      {#if category}<p class="muted">Points: {points(category)}</p>{/if}
    {/if}

    {#if err}<p class="err">{err}</p>{/if}
    <div class="row">
      <button type="submit" class="btn btn-primary" disabled={!canSubmit}>End tour</button>
      <button type="button" class="btn" on:click={() => dispatch('cancel')}>Cancel</button>
    </div>
  </form>
</div>

<style>
  h2 { font-size: 15px; margin: 0 0 12px; }
  .modal-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 10; }
  .modal { width: 420px; max-height: 90vh; overflow: auto; }
</style>
