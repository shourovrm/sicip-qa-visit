import { describe, it, expect } from 'vitest'
import courses from '../../../shared/lab-standards/lab-equipment.json'
import fixture from '../../../shared/report-templates/fixtures/lab-equipment-1.json'
import { cardCourse, matchingCourses, orgKey, standardEquipment } from './labequipment.js'

describe('lab-standard equipment (fixture lab-equipment-1.json)', () => {
  for (const testCase of fixture.cases) {
    it(`${testCase.association} / "${testCase.course}"`, () => {
      const matched = matchingCourses(courses, testCase.association, testCase.course).map((course) => course.course)
      expect(matched).toEqual(testCase.expect)
      const equipment = standardEquipment(courses, testCase.association, testCase.course)
      for (const name of testCase.equipmentHas ?? []) expect(equipment).toContain(name)
      for (const name of testCase.equipmentLacks ?? []) expect(equipment).not.toContain(name)
      if (testCase.equipmentCount !== undefined) expect(equipment).toHaveLength(testCase.equipmentCount)
    })
  }

  it('maps visit associations to standards organisations', () => {
    expect(orgKey('ISC-TH')).toBe(orgKey('ISC-T&H'))
    expect(orgKey('FLAXA')).toBe(orgKey('LFMEAB'))
  })

  it('reads the card course from a plain value or a "Course · N" reference', () => {
    expect(cardCourse({ suggestCourse: 'batch' }, { batch: 'Welding · 06' })).toBe('Welding')
    expect(cardCourse({ suggestCourse: 'course' }, { course: 'Welding' })).toBe('Welding')
    expect(cardCourse({}, { course: 'Welding' })).toBe('')
  })
})
