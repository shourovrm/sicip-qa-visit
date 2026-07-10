<!-- email/password login + "forgot password" request-reset toggle. terse copy. -->
<script>
  import { signIn, requestPasswordReset } from '../lib/auth.js'

  let email = ''
  let password = ''
  let error = ''
  let busy = false
  let mode = 'login' // 'login' | 'forgot'
  let sentTo = ''

  async function submit() {
    error = ''
    busy = true
    if (mode === 'login') {
      const err = await signIn(email, password)
      if (err) error = err.message
    } else {
      const err = await requestPasswordReset(email)
      if (err) error = err.message
      else sentTo = email
    }
    busy = false
  }
</script>

<div class="wrap">
  <form class="card" on:submit|preventDefault={submit}>
    <h1>SICIP QA Visit</h1>
    {#if mode === 'login'}
      <div class="field">
        <label for="email">Email</label>
        <input id="email" type="email" bind:value={email} required autocomplete="username" />
      </div>
      <div class="field">
        <label for="pw">Password</label>
        <input id="pw" type="password" bind:value={password} required autocomplete="current-password" />
      </div>
      {#if error}<p class="err">{error}</p>{/if}
      <button class="btn btn-primary" type="submit" disabled={busy}>Log in</button>
      <button type="button" class="btn-link forgot" on:click={() => { mode = 'forgot'; error = '' }}>Forgot password?</button>
    {:else if sentTo}
      <p>Reset link sent to <b>{sentTo}</b>. Check your inbox.</p>
      <button type="button" class="btn-link" on:click={() => { mode = 'login'; sentTo = '' }}>Back to login</button>
    {:else}
      <div class="field">
        <label for="remail">Account email</label>
        <input id="remail" type="email" bind:value={email} required autocomplete="username" />
      </div>
      {#if error}<p class="err">{error}</p>{/if}
      <button class="btn btn-primary" type="submit" disabled={busy}>Send reset link</button>
      <button type="button" class="btn-link forgot" on:click={() => { mode = 'login'; error = '' }}>Back to login</button>
    {/if}
  </form>
</div>

<style>
  .wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--canvas); }
  form { width: 320px; }
  h1 { font-size: 20px; color: var(--primary); margin: 0 0 16px; }
  .btn { width: 100%; }
  .forgot { display: block; margin-top: 10px; text-align: center; width: 100%; }
</style>
