<!-- admin-only: app_meta editor + officer directory. edit/delete ANY visit/tour/leave happens on
     those pages themselves (RLS is_admin() bypass + this app's canEdit checks already allow it). -->
<script>
  import { onMount } from 'svelte'
  import { getAppMeta, setAppMeta } from '../lib/db.js'
  import { isAdmin } from '../lib/auth.js'
  import { officers } from '../lib/officers.js'

  let meta = {}
  let loading = true
  let saved = ''

  onMount(async () => {
    meta = await getAppMeta()
    loading = false
  })

  async function save(key) {
    saved = ''
    await setAppMeta(key, meta[key])
    saved = key
  }
</script>

{#if !$isAdmin}
  <p class="err">Admin access required.</p>
{:else}
  <h1>Admin</h1>

  <div class="card">
    <h2>App metadata</h2>
    {#if loading}
      <p class="muted">Loading…</p>
    {:else}
      <div class="field">
        <label for="ver">latest_version</label>
        <div class="row"><input id="ver" type="text" bind:value={meta.latest_version} />
          <button class="btn" on:click={() => save('latest_version')}>Save</button></div>
      </div>
      <div class="field">
        <label for="apk">apk_url</label>
        <div class="row"><input id="apk" type="text" bind:value={meta.apk_url} />
          <button class="btn" on:click={() => save('apk_url')}>Save</button></div>
      </div>
      {#if saved}<p class="muted">Saved {saved}.</p>{/if}
    {/if}
  </div>

  <div class="card officers-note">
    <h2>Officers</h2>
    <table>
      <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Active</th></tr></thead>
      <tbody>
        {#each $officers as o (o.id)}
          <tr><td>{o.name}</td><td>{o.email}</td><td>{o.role}</td><td>{o.active ? 'yes' : 'no'}</td></tr>
        {/each}
      </tbody>
    </table>
    <p class="muted">Account creation/activation runs via local <code>tools/</code> scripts (out of web scope) — service key never touches the browser.</p>
  </div>

  <div class="card">
    <h2>Edit/delete any record</h2>
    <p class="muted">Use the Visits, Tours, and Leaves pages directly — admins can edit or delete any officer's rows there (not just their own).</p>
  </div>
{/if}

<style>
  h1 { color: var(--primary); }
  h2 { font-size: 15px; margin: 0 0 12px; }
  .card { margin-bottom: 16px; }
  .officers-note table { margin-bottom: 12px; }
</style>
