<!-- admin-only red banner for TMS API changes; click opens list + "Mark resolved". -->
<script>
  import { onMount } from 'svelte'
  import { isAdmin } from '../lib/auth.js'
  import { summarise, listUnresolvedTmsErrors, resolveTmsErrors } from '../lib/tmserrors.js'

  let rows = []
  let open = false
  let err = ''

  $: summary = $isAdmin ? summarise(rows) : null

  async function load() {
    if (!$isAdmin) return
    try { rows = await listUnresolvedTmsErrors() } catch { rows = [] } // silent: table may not exist yet
  }

  async function markResolved() {
    err = ''
    try {
      await resolveTmsErrors(rows.map((r) => r.id))
      rows = []
      open = false
    } catch (e) { err = e.message }
  }

  $: if ($isAdmin) load() // officer profile loads after session
  onMount(() => {
    window.addEventListener('focus', load)
    return () => window.removeEventListener('focus', load)
  })
</script>

{#if summary}
  <button class="tms-banner" on:click={() => (open = true)}>
    TMS API changed: {summary.n} error kinds since {summary.firstDay}, latest {summary.latest.endpoint} ({summary.latest.detail})
  </button>
{/if}

{#if open}
  <!-- svelte-ignore a11y-click-events-have-key-events -->
  <!-- svelte-ignore a11y-no-static-element-interactions -->
  <div class="modal-backdrop" on:click|self={() => (open = false)}>
    <div class="card modal">
      <h2>TMS API errors</h2>
      <table>
        <thead><tr><th>Day</th><th>Endpoint</th><th>Kind</th><th>Detail</th><th>App</th></tr></thead>
        <tbody>
          {#each rows as r (r.id)}
            <tr><td>{r.day}</td><td>{r.endpoint}</td><td>{r.kind}</td><td>{r.detail}</td><td>{r.app_version}</td></tr>
          {/each}
        </tbody>
      </table>
      <p class="muted">
        {#each summary?.groups ?? [] as g}{g.endpoint} {g.kind} x{g.count}; {/each}
      </p>
      {#if err}<p class="err">{err}</p>{/if}
      <div class="row">
        <button class="btn btn-primary" on:click={markResolved}>Mark resolved</button>
        <button class="btn" on:click={() => (open = false)}>Close</button>
      </div>
    </div>
  </div>
{/if}

<style>
  .tms-banner {
    display: block; width: 100%; border: 0; padding: 10px 20px; cursor: pointer;
    background: #c62828; color: #fff; font: inherit; text-align: left;
  }
</style>
