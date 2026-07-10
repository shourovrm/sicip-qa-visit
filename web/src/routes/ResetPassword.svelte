<!-- lands here from the supabase recovery email link (redirectTo=/reset). supabase-js auto-exchanges
     the code/token in the URL for a session on load, so once $session is set we show the
     new-password form; if someone opens /reset cold (no recovery link), show the request form. -->
<script>
  import { session, updatePassword, requestPasswordReset } from '../lib/auth.js'

  let password = ''
  let confirm = ''
  let email = ''
  let error = ''
  let done = false
  let busy = false

  // supabase puts failures in the url hash (e.g. otp_expired) — surface them
  const hash = new URLSearchParams(location.hash.slice(1))
  if (hash.get('error_code') === 'otp_expired')
    error = 'That reset link was already used or expired — request a new one below.'
  else if (hash.get('error_description'))
    error = hash.get('error_description').replace(/\+/g, ' ')

  async function setPassword() {
    error = ''
    if (password.length < 8) { error = 'Password must be at least 8 characters.'; return }
    if (password !== confirm) { error = 'Passwords do not match.'; return }
    busy = true
    const err = await updatePassword(password)
    busy = false
    if (err) error = err.message
    else { done = true; setTimeout(() => { location.href = '/' }, 1500) }
  }

  async function requestReset() {
    error = ''
    busy = true
    const err = await requestPasswordReset(email)
    busy = false
    if (err) error = err.message
    else done = true
  }
</script>

<div class="wrap">
  <div class="card">
    <h1>Reset password</h1>
    {#if done}
      <p>Done. Redirecting…</p>
    {:else if $session}
      <div class="field">
        <label for="np">New password</label>
        <input id="np" type="password" bind:value={password} autocomplete="new-password" />
      </div>
      <div class="field">
        <label for="cp">Confirm password</label>
        <input id="cp" type="password" bind:value={confirm} autocomplete="new-password" />
      </div>
      {#if error}<p class="err">{error}</p>{/if}
      <button class="btn btn-primary" disabled={busy} on:click={setPassword}>Set password</button>
    {:else}
      <p class="muted">Enter your account email, we'll send a reset link.</p>
      <div class="field">
        <label for="re">Email</label>
        <input id="re" type="email" bind:value={email} autocomplete="username" />
      </div>
      {#if error}<p class="err">{error}</p>{/if}
      <button class="btn btn-primary" disabled={busy} on:click={requestReset}>Send reset link</button>
    {/if}
  </div>
</div>

<style>
  .wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--canvas); }
  .card { width: 320px; }
  h1 { font-size: 20px; color: var(--primary); margin: 0 0 16px; }
  .btn { width: 100%; }
</style>
