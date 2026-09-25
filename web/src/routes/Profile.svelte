<!-- my stats, theme toggle, writing helper model, change password, logout, view-only sheet link -->
<script>
  import { onMount } from 'svelte'
  import { officer, signOut, updatePassword } from '../lib/auth.js'
  import { theme, toggleTheme } from '../lib/theme.js'
  import { getModel, setModel, loadModels } from '../lib/rewritemodel.js'
  import { listVisits } from '../lib/db.js'
  import { totalPoints, rank } from '../lib/scoring.js'

  const SHEET_URL = 'https://docs.google.com/spreadsheets/d/1MIZ7tMjWHKnM-NuLcfimH__N9YNac-MIQKXe_oUTBuM'
  const REPO_URL = 'https://github.com/shourovrm/sicip-qa-visit'
  const APK_URL = 'https://github.com/shourovrm/sicip-qa-visit/releases/latest'
  const VERSION = '1.7.2' // bump on every release

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
    myPoints = totalPoints(mineVisits.map((v) => ({ officerId: v.officer_id, category: v.category, deleted: v.deleted, done: v.status === 'done' })))
    const ranked = rank(visits.map((v) => ({ officerId: v.officer_id, category: v.category, deleted: v.deleted, done: v.status === 'done' })))
    myRankPos = ranked.findIndex(([id]) => id === mine) + 1
    loading = false
  }

  // "Writing helper" model picker for Improve wording (per-device, localStorage-backed --
  // see rewritemodel.js). savedModel is the officer's own choice, or null = server default;
  // modelInfo is the /api/models response, or null while loading / when it fails to load.
  let savedModel = getModel()
  let modelInfo = null
  let modelsLoading = true

  onMount(async () => {
    modelInfo = await loadModels()
    modelsLoading = false
  })

  // both identifiers are read directly here (not through a helper fn) so Svelte's dependency
  // tracking actually sees them -- see DECISIONS.md's $:/{@const} gotcha.
  // a saved key the server no longer lists falls back to the default there, so show that.
  let selectedModel = null
  $: {
    const savedIsListed = modelInfo?.models.some((m) => m.key === savedModel)
    selectedModel = savedIsListed ? savedModel : (modelInfo?.default ?? savedModel)
  }

  function chooseModel(key) {
    savedModel = key
    setModel(key)
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
  <h2>Writing helper</h2>
  {#if modelsLoading}
    <p class="muted">Loading models…</p>
  {:else if !modelInfo}
    <p class="muted">Model list unavailable offline</p>
  {:else}
    <div class="model-list" role="radiogroup" aria-label="Writing helper model">
      {#each modelInfo.models as m (m.key)}
        <button
          type="button"
          class="model-opt"
          class:chosen={selectedModel === m.key}
          role="radio"
          aria-checked={selectedModel === m.key}
          on:click={() => chooseModel(m.key)}
        >
          <span class="model-radio" aria-hidden="true"></span>
          <span class="model-text">
            <span class="model-label">{m.label}</span>
            {#if m.note}<span class="model-note">{m.note}</span>{/if}
          </span>
        </button>
      {/each}
    </div>
  {/if}
  <p class="muted model-help">Used by Improve wording in reports. All options are free; they share one daily limit.</p>
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

<div class="card">
  <h2>About</h2>
  <p><b>SICIP QA Visit</b> — v{VERSION}</p>
  <p class="muted">
    Visit management for SICIP QA field officers: schedule visits and tours, log travel,
    auto-score visits, and manage TA/DA bills.
  </p>
  <p class="muted">Created by Riad Mashrub Shourov, Program Officer (QA), SICIP</p>
  <p class="links">
    <a href={REPO_URL} target="_blank" rel="noopener" class="gh-link">
      <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
        <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38
          0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13
          -.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07
          -1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82
          a7.6 7.6 0 0 1 4 0c1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15
          0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38
          A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8Z" />
      </svg>
      GitHub
    </a>
    · <a href={APK_URL} target="_blank" rel="noopener">Android APK (latest release)</a>
  </p>
</div>

<button class="btn btn-danger" on:click={signOut}>Log out</button>

<style>
  h1 { color: var(--primary); }
  h2 { font-size: 15px; margin: 0 0 8px; }
  .card { margin-bottom: 16px; }
  .links { display: flex; align-items: center; gap: 6px; }
  .gh-link { display: inline-flex; align-items: center; gap: 5px; }

  .model-list { display: flex; flex-direction: column; gap: 6px; margin-bottom: 8px; }
  .model-opt {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    width: 100%;
    text-align: left;
    padding: 8px 10px;
    border: 1px solid var(--outline);
    border-radius: 10px;
    background: var(--surface);
    cursor: pointer;
  }
  .model-opt.chosen { border-color: var(--primary); background: var(--primary-container); }
  .model-opt.chosen .model-label, .model-opt.chosen .model-note { color: var(--on-primary-container); }
  .model-radio {
    flex: none;
    width: 16px;
    height: 16px;
    margin-top: 2px;
    border-radius: 50%;
    border: 2px solid var(--outline);
  }
  .model-opt.chosen .model-radio { border-color: var(--on-primary-container); background: var(--on-primary-container); }
  .model-text { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
  .model-label { font-weight: 600; font-size: 14px; }
  .model-note { font-size: 12px; color: var(--muted); }
  .model-help { margin-top: 4px; font-size: 12px; }
</style>
