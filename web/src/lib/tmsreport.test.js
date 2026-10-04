import { describe, it, expect } from 'vitest'
import { linkData } from './tmsreport.js'

describe('linkData', () => {
  // TMS institute rows carry the entity that registered the institute; an institute shared by two
  // partners (Compact: BACI + BEIOA) must link to the partner the officer picked
  it('stores the picked partner, not the entity on the institute row', () => {
    const institute = { id: 1503, entity_id: 5, training_institute_no: '1820988', institute_name: 'Compact', address: ' Chuadanga ' }
    expect(linkData({ id: 1 }, 8, institute)).toEqual({
      tranche_id: 1,
      entity_id: 8,
      institute_id: 1503,
      institute_no: '1820988',
      name: 'Compact',
      address: 'Chuadanga',
    })
  })
})
