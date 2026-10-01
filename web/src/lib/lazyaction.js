// click handler that lazy-loads a heavy chunk (docx exporters) then runs one of its exports.
// A tab opened before a deploy asks for chunk hashes the new deploy deleted; the SPA fallback
// answers with index.html and import() rejects -- unhandled, the button silently did nothing.
// Now: stale chunk -> offer a reload; any other failure -> tell the user.

// wording per engine: chromium / firefox / safari
const STALE_CHUNK_MESSAGES = [
  'Failed to fetch dynamically imported module',
  'error loading dynamically imported module',
  'Importing a module script failed',
]

export function isStaleChunkError(error) {
  const message = String(error?.message ?? '')
  return STALE_CHUNK_MESSAGES.some((known) => message.includes(known))
}

const browserUi = {
  confirm: (text) => window.confirm(text),
  alert: (text) => window.alert(text),
  reload: () => window.location.reload(),
}

export function lazyAction(loader, exportName, ui = browserUi) {
  return async (...args) => {
    try {
      const module = await loader()
      return await module[exportName](...args)
    } catch (error) {
      if (isStaleChunkError(error)) {
        if (ui.confirm('The app was updated since this page was opened. Reload now to get the new version? Your report is saved.')) ui.reload()
        return undefined
      }
      ui.alert('Word export failed: ' + (error?.message ?? error))
      return undefined
    }
  }
}
