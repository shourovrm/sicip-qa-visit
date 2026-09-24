<!-- critical non-compliance flags: plain tick boxes, red highlight once ticked. contributes 0 to
     section progress totals (spec) but flags the section when anything is ticked. -->
<script>
  import { createEventDispatcher } from 'svelte'

  export let block // {type:'flags', items:[{id,text}]}
  export let ticked = [] // data.flags
  export let disabled = false

  const dispatch = createEventDispatcher()

  function toggle(id) {
    if (disabled) return
    dispatch('change', ticked.includes(id) ? ticked.filter((x) => x !== id) : [...ticked, id])
  }
</script>

<div class="flags">
  {#each block.items as item (item.id)}
    {@const on = ticked.includes(item.id)}
    <label class="flag" class:on>
      <input type="checkbox" checked={on} {disabled} on:change={() => toggle(item.id)} />
      <span>{item.text}</span>
    </label>
  {/each}
</div>

<style>
  .flag { display: flex; gap: 10px; align-items: flex-start; padding: 10px 8px; border-radius: 8px; cursor: pointer; font-size: 14px; }
  .flag input { width: 20px; height: 20px; margin-top: 1px; accent-color: var(--tone-no-fg); flex: none; }
  .flag.on { background: var(--tone-no-bg); color: var(--tone-no-fg); font-weight: 700; }
</style>
