<!-- navy top bar: brand + nav tabs (admin tab only for admins) + officer name -->
<script>
  import { route, nav } from '../lib/router.js'
  import { officer, isAdmin } from '../lib/auth.js'

  const tabs = [
    ['/', 'Home'],
    ['/visits', 'Visits'],
    ['/tours', 'Tours'],
    ['/bills', 'Bills'],
    ['/team', 'Team'],
    ['/leaves', 'Leaves'],
    ['/profile', 'Profile'],
  ]

  $: current = '/' + $route.split('/')[1]
</script>

<header class="bar">
  <div class="brand">SICIP QA Visit</div>
  <nav>
    {#each tabs as [path, label]}
      <button class:active={current === path} on:click={() => nav(path)}>{label}</button>
    {/each}
    {#if $isAdmin}
      <button class:active={current === '/admin'} on:click={() => nav('/admin')}>Admin</button>
    {/if}
  </nav>
  <div class="who">{$officer?.name ?? ''}</div>
</header>

<style>
  .bar {
    background: var(--primary);
    color: var(--on-primary);
    display: flex;
    align-items: center;
    gap: 24px;
    padding: 0 20px;
    height: 56px;
  }
  .brand { font-weight: 700; letter-spacing: 0.02em; }
  nav { display: flex; gap: 4px; flex: 1; overflow-x: auto; }
  nav button {
    background: none;
    border: none;
    color: var(--on-primary);
    opacity: 0.75;
    padding: 8px 12px;
    border-radius: var(--radius-pill);
    cursor: pointer;
    font-weight: 700;
    white-space: nowrap;
  }
  nav button.active { opacity: 1; background: var(--accent); color: var(--on-accent); }
  .who { font-size: 13px; opacity: 0.85; }
</style>
