// three-way report merge must match the shared fixture android's ReportMergeTest.kt also checks
import { describe, it, expect } from 'vitest'
import cases from '../../../shared/report-templates/fixtures/merge-1.json'
import { mergeReportData } from './reportmerge.js'

describe('mergeReportData', () => {
  for (const c of cases) {
    it(c.name, () => {
      expect(mergeReportData(c.base, c.local, c.server)).toEqual(c.expected)
    })
  }
})
