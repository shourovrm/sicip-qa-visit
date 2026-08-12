<!-- read/edit list of travel legs on the active tour -- opened from Home's "Travels" button.
     dumb list view: parent (Home.svelte) owns the actual create/update/delete calls + legs
     state, this just renders + dispatches add/edit/delete/close. same modal-backdrop/.card.modal
     family as LegModal so the two stack visually as one thing (Home hides this while LegModal
     is open, see showTravels/resumeTravels in Home.svelte). -->
<script>
  import { createEventDispatcher } from 'svelte'
  import { parseRemarks } from '../lib/legs.js'

  export let legs = []

  const dispatch = createEventDispatcher()
  let confirmId = null // leg id showing the inline "delete this?" row

  const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  const fmtDate = (d) => { const [, m, day] = d.split('-'); return `${day} ${MONTHS[Number(m) - 1]}` }
  // pg `time` comes back as HH:MM:SS -- drop the seconds, android shows HH:MM
  const fmtTime = (t) => String(t).slice(0, 5)

  $: sorted = [...legs].sort((a, b) => (a.dep_date + a.dep_time).localeCompare(b.dep_date + b.dep_time))
  $: total = legs.reduce((s, l) => s + Number(l.fare), 0)
</script>

<!-- svelte-ignore a11y-click-events-have-key-events -->
<!-- svelte-ignore a11y-no-static-element-interactions -->
<div class="modal-backdrop" on:click|self={() => dispatch('close')}>
  <div class="card modal">
    <div class="row spread">
      <h2>Travels</h2>
      <button type="button" class="btn-icon" aria-label="Close" on:click={() => dispatch('close')}>&times;</button>
    </div>
    {#if legs.length}
      <p class="muted sub">{legs.length} travel{legs.length === 1 ? '' : 's'} · ৳{total.toLocaleString()} · click a row to edit</p>
    {/if}

    {#if legs.length === 0}
      <p class="muted">No travel logged yet. Add each bus, train or CNG leg as you take it — they become your TA/DA bill rows.</p>
    {:else}
      {#each sorted as l (l.id)}
        {#if confirmId === l.id}
          <div class="leg-row confirm-row">
            <span>Delete {l.dep_place} → {l.arr_place}?</span>
            <div class="row">
              <button type="button" class="btn" on:click={() => (confirmId = null)}>Cancel</button>
              <button type="button" class="btn" style="background: var(--danger); color: var(--on-danger)" on:click={() => { dispatch('delete', l); confirmId = null }}>Delete</button>
            </div>
          </div>
        {:else}
          <div class="leg-row">
            <!-- svelte-ignore a11y-click-events-have-key-events -->
            <!-- svelte-ignore a11y-no-static-element-interactions -->
            <div class="leg-main" on:click={() => dispatch('edit', l)}>
              <div class="row spread">
                <b>{l.dep_place} → {l.arr_place}</b>
                <b>৳{Number(l.fare).toLocaleString()}</b>
              </div>
              <div class="leg-sub">
                <span class="leg-sub-text">{fmtDate(l.dep_date)} {fmtTime(l.dep_time)} – {fmtTime(l.arr_time)} · {l.mode}{l.class ? ' · ' + l.class : ''}</span>
                {#if parseRemarks(l.remarks).ticket}<span class="chip">Ticket</span>{/if}
              </div>
            </div>
            <button type="button" class="btn-icon" aria-label="Delete travel" on:click={() => (confirmId = l.id)}>
              <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
                <path d="M6 1a1 1 0 0 0-1 1v1H2.5a.5.5 0 0 0 0 1H3v9a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2V4h.5a.5.5 0 0 0 0-1H11V2a1 1 0 0 0-1-1H6Zm0 1h4v1H6V2ZM4.5 4h7v9a1 1 0 0 1-1 1H5.5a1 1 0 0 1-1-1V4Zm2 2v6h1V6h-1Zm2.5 0v6h1V6h-1Z" />
              </svg>
            </button>
          </div>
        {/if}
      {/each}
    {/if}

    <button type="button" class="btn add-btn" on:click={() => dispatch('add')}>+ Add travel</button>
  </div>
</div>

<style>
  h2 { font-size: 15px; margin: 0; }
  .modal-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 10; }
  .modal { width: 420px; max-height: 90vh; overflow: auto; }
  .sub { margin: 4px 0 12px; font-size: 13px; }
  .btn-icon { flex: none; width: 44px; height: 44px; display: flex; align-items: center; justify-content: center; background: none; border: none; padding: 0; cursor: pointer; color: var(--muted); font-size: 20px; }
  .leg-row { display: flex; align-items: center; gap: 4px; border-bottom: 1px solid var(--outline); padding: 8px 0; }
  .leg-main { flex: 1; min-width: 0; cursor: pointer; }
  .leg-sub { display: flex; align-items: center; gap: 6px; margin-top: 2px; }
  .leg-sub-text { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; color: var(--muted); }
  .chip { flex: none; background: var(--status-success-bg); color: var(--status-success-fg); border-radius: var(--radius-pill); padding: 1px 8px; font-size: 11px; font-weight: 700; }
  .confirm-row { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
  .add-btn { width: 100%; margin-top: 12px; }
</style>
