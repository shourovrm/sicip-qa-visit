import { describe, it, expect } from 'vitest'
import { summarise } from './tmserrors.js'

const row = (endpoint, kind, day, created_at, detail = 'd') =>
  ({ id: endpoint + day, endpoint, kind, day, created_at, detail, app_version: '1' })

describe('summarise', () => {
  it('returns null for no rows', () => {
    expect(summarise([])).toBeNull()
  })

  it('groups by endpoint+kind, finds first day and latest row', () => {
    const rows = [
      row('/a', 'shape', '2026-09-30', '2026-09-30T10:00:00Z', 'newest'),
      row('/a', 'shape', '2026-09-28', '2026-09-28T10:00:00Z'),
      row('/b', 'http_404', '2026-09-29', '2026-09-29T10:00:00Z'),
    ]
    const summary = summarise(rows)
    expect(summary.n).toBe(2)
    expect(summary.firstDay).toBe('2026-09-28')
    expect(summary.latest.detail).toBe('newest')
    expect(summary.groups.find((g) => g.endpoint === '/a').count).toBe(2)
  })
})
