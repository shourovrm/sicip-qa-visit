<!-- status pill: visit/office/success tone, derived state display only -->
<script>
  export let tone = 'office' // 'visit' | 'office' | 'success'
</script>

<span class="pill pill-{tone}"><slot /></span>

<style>
  .pill {
    display: inline-block;
    padding: 3px 12px;
    border-radius: var(--radius-pill);
    font-size: 12px;
    font-weight: 700;
  }
  .pill-visit { background: var(--status-visit-bg); color: var(--status-visit-fg); }
  .pill-office { background: var(--status-office-bg); color: var(--status-office-fg); }
  .pill-success { background: var(--status-success-bg); color: var(--status-success-fg); }
</style>
