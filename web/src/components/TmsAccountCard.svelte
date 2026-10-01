<!-- Profile card: log in to / out of the officer's own TMS account (lib/tmslogin.js), web twin
     of android ui/settings/TmsAccountCard.kt. Only token + name + expiry are kept (tmssession.js);
     once the token expires (or TMS answers 401) the login form comes back -- the browser never keeps the password. -->
<script>
  import { onDestroy } from 'svelte'
  import { loginToTms } from '../lib/tmslogin.js'
  import { refreshTmsSession, signInTms, signOutTms, tmsSession } from '../lib/tmsstore.js'

  let username = ''
  let password = ''
  let loading = false
  let error = ''

  // drop back to the login form the moment the token runs out
  const expiryCheck = setInterval(() => {
    if ($tmsSession && !refreshTmsSession()) error = 'TMS session expired. Sign in again.'
  }, 60_000)
  onDestroy(() => clearInterval(expiryCheck))

  async function logIn() {
    loading = true
    error = ''
    try {
      signInTms(await loginToTms(username.trim(), password))
      password = ''
    } catch (e) {
      error = e.message
    } finally {
      loading = false
    }
  }

  function signOut() {
    signOutTms()
  }

  function untilText(expiresAt) {
    return new Date(expiresAt * 1000).toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
  }
</script>

<div class="card">
  <h2>TMS account</h2>
  {#if $tmsSession}
    <p class="name">{$tmsSession.displayName}</p>
    <p class="muted until">Signed in until {untilText($tmsSession.expiresAt)}</p>
    <button type="button" class="btn" on:click={signOut}>Sign out</button>
  {:else}
    <form on:submit|preventDefault={logIn}>
      <div class="field"><label for="tms-user">TMS username</label><input id="tms-user" bind:value={username} autocomplete="username" disabled={loading} /></div>
      <div class="field"><label for="tms-pw">TMS password</label><input id="tms-pw" type="password" bind:value={password} autocomplete="current-password" disabled={loading} /></div>
      {#if error}<p class="err">{error}</p>{/if}
      <button type="submit" class="btn btn-primary" disabled={loading || !username.trim() || !password}>{loading ? 'Logging in…' : 'Log in'}</button>
    </form>
    <p class="muted hint">Your password goes straight to TMS and is not saved in this browser.</p>
  {/if}
</div>

<style>
  h2 { font-size: 15px; margin: 0 0 8px; }
  .card { margin-bottom: 16px; }
  .hint { margin-top: 8px; font-size: 12px; }
  .name { margin: 0; font-weight: 700; }
  .until { margin: 2px 0 10px; font-size: 14px; }
</style>
