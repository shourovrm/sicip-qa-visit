import { describe, it, expect, vi } from 'vitest'
import { lazyAction, isStaleChunkError } from './lazyaction.js'

const staleError = () => new TypeError('Failed to fetch dynamically imported module: https://x/assets/reportdocx-OLD.js')

function fakeUi(confirmAnswer = true) {
  return { confirm: vi.fn(() => confirmAnswer), alert: vi.fn(), reload: vi.fn() }
}

describe('isStaleChunkError', () => {
  it('knows the chromium, firefox and safari wording', () => {
    expect(isStaleChunkError(staleError())).toBe(true)
    expect(isStaleChunkError(new TypeError('error loading dynamically imported module'))).toBe(true)
    expect(isStaleChunkError(new TypeError('Importing a module script failed.'))).toBe(true)
    expect(isStaleChunkError(new Error('docx exploded'))).toBe(false)
    expect(isStaleChunkError(undefined)).toBe(false)
  })
})

describe('lazyAction', () => {
  it('calls the named export with the click args', async () => {
    const download = vi.fn(async (a, b) => `${a}-${b}`)
    const action = lazyAction(async () => ({ download }), 'download', fakeUi())
    expect(await action('x', 'y')).toBe('x-y')
    expect(download).toHaveBeenCalledWith('x', 'y')
  })

  it('offers a reload when the chunk is gone after a deploy (stale tab)', async () => {
    const ui = fakeUi(true)
    const action = lazyAction(() => Promise.reject(staleError()), 'download', ui)
    await expect(action()).resolves.toBeUndefined()
    expect(ui.confirm).toHaveBeenCalledOnce()
    expect(ui.reload).toHaveBeenCalledOnce()
    expect(ui.alert).not.toHaveBeenCalled()
  })

  it('does not reload when the user declines', async () => {
    const ui = fakeUi(false)
    await lazyAction(() => Promise.reject(staleError()), 'download', ui)()
    expect(ui.reload).not.toHaveBeenCalled()
  })

  it('shows any other failure instead of swallowing it', async () => {
    const ui = fakeUi()
    const failing = async () => { throw new Error('docx exploded') }
    await lazyAction(async () => ({ download: failing }), 'download', ui)()
    expect(ui.alert).toHaveBeenCalledWith(expect.stringContaining('docx exploded'))
    expect(ui.reload).not.toHaveBeenCalled()
  })
})
