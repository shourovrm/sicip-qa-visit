<!-- list/add/cancel own leaves + team view -->
<script>
  import { onMount } from 'svelte'
  import { listLeaves, createLeave, cancelLeave, updateLeaveStatus } from '../lib/db.js'
  import { officer, isAdmin } from '../lib/auth.js'
  import { officers } from '../lib/officers.js'
  import { LEAVE_TYPES } from '../lib/seeds.js'
  import Dropdown from '../components/Dropdown.svelte'
  import Pill from '../components/Pill.svelte'

  let leaves = []
  let loading = true
  let scope = 'mine'
  let showForm = false
  let form = blank()
  let err = ''

  function blank() {
    return { type: 'Casual', reason: '', informed_officer_id: '', start_date: '', end_date: '' }
  }

  async function load() {
    loading = true
    leaves = await listLeaves()
    loading = false
  }
  onMount(load)

  $: mine = $officer?.id
  $: filtered = leaves.filter((l) => (scope === 'mine' ? l.officer_id === mine : true))

  async function submit() {
    err = ''
    try {
      const row = await createLeave({
        officer_id: mine, type: form.type, reason: form.reason || null,
        informed_officer_id: form.informed_officer_id || null,
        start_date: form.start_date, end_date: form.end_date,
      })
      leaves = [row, ...leaves]
      form = blank()
      showForm = false
    } catch (e) {
      err = e.message
    }
  }

  async function cancel(l) {
    if (!confirm('Cancel this leave?')) return
    const updated = await cancelLeave(l.id)
    leaves = leaves.map((x) => (x.id === updated.id ? updated : x))
  }

  // scheduled -> started -> completed, own rows only (see migration 007).
  async function advance(l, status) {
    const updated = await updateLeaveStatus(l.id, status)
    leaves = leaves.map((x) => (x.id === updated.id ? updated : x))
  }

  function statusTone(status) {
    if (status === 'cancelled') return 'office'
    if (status === 'started') return 'leave'
    if (status === 'completed' || status === 'availed') return 'success' // availed = legacy, pre-007 rows
    return 'visit' // scheduled
  }

  function statusLabel(status) {
    return status === 'availed' ? 'completed' : status // availed = legacy, pre-007 rows
  }
</script>

<h1>Leaves</h1>

<div class="row-wrap tabs">
  <div class="seg">
    <button class:active={scope === 'mine'} on:click={() => (scope = 'mine')}>Personal</button>
    <button class:active={scope === 'team'} on:click={() => (scope = 'team')}>Team</button>
  </div>
  {#if scope === 'mine'}
    <button class="btn btn-primary" on:click={() => (showForm = !showForm)}>+ Schedule leave</button>
  {/if}
</div>

{#if showForm}
  <form class="card" on:submit|preventDefault={submit}>
    <div class="row">
      <div class="field"><label for="lt">Type</label><Dropdown id="lt" bind:value={form.type} options={LEAVE_TYPES} /></div>
      <div class="field"><label for="io">Inform colleague (optional)</label><Dropdown id="io" bind:value={form.informed_officer_id} options={$officers.filter((o) => o.id !== mine).map((o) => [o.id, o.name])} placeholder="None" /></div>
    </div>
    <div class="field"><label for="reason">Reason</label><input id="reason" type="text" bind:value={form.reason} /></div>
    <div class="row">
      <div class="field"><label for="sd">Start date</label><input id="sd" type="date" bind:value={form.start_date} required /></div>
      <div class="field"><label for="ed">End date</label><input id="ed" type="date" bind:value={form.end_date} required /></div>
    </div>
    {#if err}<p class="err">{err}</p>{/if}
    <button type="submit" class="btn btn-primary">Save</button>
  </form>
{/if}

{#if loading}
  <p class="muted">Loading…</p>
{:else}
  <table class="card">
    <thead><tr><th>Type</th><th>Reason</th><th>Start</th><th>End</th><th>Status</th>{#if scope === 'team'}<th>Officer</th>{/if}<th></th></tr></thead>
    <tbody>
      {#if filtered.length === 0}<tr><td colspan="7" class="muted">No leaves.</td></tr>{/if}
      {#each filtered as l (l.id)}
        <tr>
          <td>{l.type}</td>
          <td>{l.reason ?? ''}</td>
          <td>{l.start_date}</td>
          <td>{l.end_date}</td>
          <td><Pill tone={statusTone(l.status)}>{statusLabel(l.status)}</Pill></td>
          {#if scope === 'team'}<td>{$officers.find((o) => o.id === l.officer_id)?.name ?? ''}</td>{/if}
          <td>
            {#if l.officer_id === mine && l.status === 'scheduled'}
              <button class="btn-link" on:click={() => advance(l, 'started')}>Start leave</button>
            {/if}
            {#if l.officer_id === mine && l.status === 'started'}
              <button class="btn-link" on:click={() => advance(l, 'completed')}>End leave</button>
            {/if}
            {#if l.status !== 'cancelled' && (l.officer_id === mine || $isAdmin)}
              <button class="btn-link" on:click={() => cancel(l)}>Cancel</button>
            {/if}
          </td>
        </tr>
      {/each}
    </tbody>
  </table>
{/if}

<style>
  h1 { color: var(--primary); }
  .tabs { justify-content: space-between; margin-bottom: 16px; }
  .seg { display: flex; gap: 4px; background: var(--surface); border: 1px solid var(--outline); border-radius: var(--radius-pill); padding: 3px; }
  .seg button { border: none; background: none; padding: 6px 14px; border-radius: var(--radius-pill); cursor: pointer; font-weight: 700; color: var(--muted); }
  .seg button.active { background: var(--primary); color: var(--on-primary); }
  form.card { margin-bottom: 16px; }
</style>
