<!-- generic dense table: columns = [{key,label}], rows = [{...}]. cell slot for custom render. -->
<script>
  export let columns = []
  export let rows = []
  export let emptyText = 'Nothing here yet.'
</script>

<table>
  <thead>
    <tr>
      {#each columns as col}<th>{col.label}</th>{/each}
    </tr>
  </thead>
  <tbody>
    {#if rows.length === 0}
      <tr><td colspan={columns.length} class="muted">{emptyText}</td></tr>
    {/if}
    {#each rows as row}
      <tr>
        {#each columns as col}
          <td><slot name="cell" {row} {col}>{row[col.key]}</slot></td>
        {/each}
      </tr>
    {/each}
  </tbody>
</table>
