<!-- admin-only: app_meta editor + officer directory. edit/delete ANY visit/tour/leave happens on
     those pages themselves (RLS is_admin() bypass + this app's canEdit checks already allow it). -->
<script>
  import { onMount } from 'svelte'
  import { getAppMeta, setAppMeta, updateOfficer } from '../lib/db.js'
  import { isAdmin } from '../lib/auth.js'
  import { officers } from '../lib/officers.js'
  import Dropdown from '../components/Dropdown.svelte'

  let meta = {}
  let loading = true
  let saved = ''
  let editingId = null // officer.id being edited
  let draft = null // {name, role, active} while editing
  let officerErr = ''

  onMount(async () => {
    meta = await getAppMeta()
    loading = false
  })

  async function save(key) {
    saved = ''
    await setAppMeta(key, meta[key])
    saved = key
  }

  function startEditOfficer(o) {
    editingId = o.id
    draft = { name: o.name, role: o.role, active: o.active }
    officerErr = ''
  }

  async function saveOfficer(id) {
    officerErr = ''
    try {
      const updated = await updateOfficer(id, draft)
      officers.update((list) => list.map((o) => (o.id === id ? updated : o)))
      editingId = null
    } catch (e) {
      officerErr = e.message
    }
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
    {#if officerErr}<p class="err">{officerErr}</p>{/if}
    <table>
      <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Active</th><th></th></tr></thead>
      <tbody>
        {#each $officers as o (o.id)}
          {#if editingId === o.id}
            <tr>
              <td><input type="text" bind:value={draft.name} /></td>
              <td class="muted">{o.email}</td>
              <td><Dropdown bind:value={draft.role} options={[['officer', 'officer'], ['admin', 'admin']]} /></td>
              <td><input type="checkbox" bind:checked={draft.active} /></td>
              <td>
                <button class="btn-link" on:click={() => saveOfficer(o.id)}>Save</button>
                <button class="btn-link" on:click={() => (editingId = null)}>Cancel</button>
              </td>
            </tr>
          {:else}
            <tr>
              <td>{o.name}</td><td>{o.email}</td><td>{o.role}</td><td>{o.active ? 'yes' : 'no'}</td>
              <td><button class="btn-link" on:click={() => startEditOfficer(o)}>Edit</button></td>
            </tr>
          {/if}
        {/each}
      </tbody>
    </table>
    <p class="muted">Full account deletion/password reset for others: local <code>tools/</code> scripts (service key never touches the browser).</p>
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
