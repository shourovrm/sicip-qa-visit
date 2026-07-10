<!-- my stats, theme toggle, change password, logout, view-only sheet link -->
<script>
  import { officer, signOut, updatePassword } from '../lib/auth.js'
  import { theme, toggleTheme } from '../lib/theme.js'
  import { listVisits } from '../lib/db.js'
  import { totalPoints, rank } from '../lib/scoring.js'

  const SHEET_URL = 'https://docs.google.com/spreadsheets/d/1MIZ7tMjWHKnM-NuLcfimH__N9YNac-MIQKXe_oUTBuM'

  let myPoints = 0
  let myRankPos = 0
  let visitCount = 0
  let loading = true

  // stats wait for the officer row (hard refresh: mount fires before auth resolves)
  let loadStarted = false
  $: if ($officer && !loadStarted) { loadStarted = true; loadStats() }

  async function loadStats() {
    const visits = await listVisits()
    const mine = $officer?.id
    const mineVisits = visits.filter((v) => v.officer_id === mine)
    visitCount = mineVisits.length
    myPoints = totalPoints(mineVisits.map((v) => ({ officerId: v.officer_id, category: v.category, deleted: v.deleted })))
    const ranked = rank(visits.map((v) => ({ officerId: v.officer_id, category: v.category, deleted: v.deleted })))
    myRankPos = ranked.findIndex(([id]) => id === mine) + 1
    loading = false
  }

  let newPw = '', confirmPw = '', pwErr = '', pwDone = false

  async function changePassword() {
    pwErr = ''; pwDone = false
    if (newPw.length < 8) { pwErr = 'Password must be at least 8 characters.'; return }
    if (newPw !== confirmPw) { pwErr = 'Passwords do not match.'; return }
    const err = await updatePassword(newPw)
    if (err) pwErr = err.message
    else { pwDone = true; newPw = ''; confirmPw = '' }
  }
</script>

<h1>Profile</h1>

<div class="card">
  <h2>{$officer?.name}</h2>
  <p class="muted">{$officer?.email} · {$officer?.role}</p>
  {#if loading}
    <p class="muted">Loading stats…</p>
  {:else}
    <p>My points: <b>{myPoints}</b> · Rank: <b>#{myRankPos || '—'}</b> · Visits logged: <b>{visitCount}</b></p>
  {/if}
</div>

<div class="card">
  <h2>Theme</h2>
  <button class="btn" on:click={toggleTheme}>Switch to {$theme === 'dark' ? 'light' : 'dark'}</button>
</div>

<div class="card">
  <h2>Change password</h2>
  <div class="field"><label for="npw">New password</label><input id="npw" type="password" bind:value={newPw} autocomplete="new-password" /></div>
  <div class="field"><label for="cpw">Confirm</label><input id="cpw" type="password" bind:value={confirmPw} autocomplete="new-password" /></div>
  {#if pwErr}<p class="err">{pwErr}</p>{/if}
  {#if pwDone}<p class="muted">Password updated.</p>{/if}
  <button class="btn btn-primary" on:click={changePassword}>Update password</button>
</div>

<div class="card">
  <h2>Visit Scores sheet (view-only)</h2>
  <a href={SHEET_URL} target="_blank" rel="noopener">Open Google Sheet</a>
</div>

<button class="btn btn-danger" on:click={signOut}>Log out</button>

<style>
  h1 { color: var(--primary); }
  h2 { font-size: 15px; margin: 0 0 8px; }
  .card { margin-bottom: 16px; }
</style>
