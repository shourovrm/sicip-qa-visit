import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearInstituteCache, loadAssociationInstitutes } from './tmsinstitutes.js'

beforeEach(clearInstituteCache)

describe('loadAssociationInstitutes', () => {
  it('asks TMS once per association and returns its institutes', async () => {
    const loadChoices = vi.fn().mockResolvedValue({ institutes: [{ id: 1, institute_name: 'Alpha Institute' }] })
    expect(await loadAssociationInstitutes('BACI', loadChoices)).toHaveLength(1)
    await loadAssociationInstitutes(' baci ', loadChoices)
    expect(loadChoices).toHaveBeenCalledTimes(1)
  })
  it('returns nothing for a blank association without calling TMS', async () => {
    const loadChoices = vi.fn()
    expect(await loadAssociationInstitutes('', loadChoices)).toEqual([])
    expect(loadChoices).not.toHaveBeenCalled()
  })
  it('does not cache a failure', async () => {
    const loadChoices = vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValue({ institutes: [] })
    await expect(loadAssociationInstitutes('BACI', loadChoices)).rejects.toThrow('offline')
    await loadAssociationInstitutes('BACI', loadChoices)
    expect(loadChoices).toHaveBeenCalledTimes(2)
  })
})
