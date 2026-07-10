<script>
  import { session } from './lib/auth.js'
  import { route } from './lib/router.js'
  import TopBar from './components/TopBar.svelte'
  import Login from './routes/Login.svelte'
  import ResetPassword from './routes/ResetPassword.svelte'
  import Visits from './routes/Visits.svelte'
  import Tours from './routes/Tours.svelte'
  import Bills from './routes/Bills.svelte'
  import Team from './routes/Team.svelte'
  import Leaves from './routes/Leaves.svelte'
  import Admin from './routes/Admin.svelte'
  import Profile from './routes/Profile.svelte'

  // supabase password-recovery redirect lands on a clean /reset path (not hash-routed) so the
  // code/token query params supabase-js needs stay intact -- see lib/auth.js requestPasswordReset.
  const isResetPath = location.pathname === '/reset'

  const pages = {
    '/visits': Visits, '/tours': Tours, '/bills': Bills, '/team': Team,
    '/leaves': Leaves, '/admin': Admin, '/profile': Profile,
  }
  $: base = '/' + $route.split('/')[1]
  $: Page = pages[base] ?? Visits
</script>

{#if isResetPath}
  <ResetPassword />
{:else if $session === undefined}
  <p class="loading">Loading…</p>
{:else if $session === null}
  <Login />
{:else}
  <TopBar />
  <main>
    <svelte:component this={Page} />
  </main>
{/if}

<style>
  .loading { padding: 40px; text-align: center; color: var(--muted); }
  main { max-width: 1100px; margin: 0 auto; padding: 20px; }
</style>
