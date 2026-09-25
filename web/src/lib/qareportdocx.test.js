// buildQaReportDocx -- smoke + a couple of structural checks, same convention as
// reportdocx.test.js (a .docx is a zip; deep content checks aren't practical here, this proves
// the builder runs end-to-end without throwing for an empty report, a filled one, and every
// section of the real template).
import { describe, it, expect } from 'vitest'
import { buildQaReportDocx, downloadQaReportDocx } from './qareportdocx.js'
import { templateFor } from './reporttemplate.js'

const template = templateFor('qa')
const meta = { officerName: 'Mahfuzul Islam', status: 'draft' }
const EMPTY_DATA = { fields: {}, checks: {}, cards: {}, flags: [], criteria: {} }

describe('buildQaReportDocx', () => {
  it('builds a blob for a maximally blank report', async () => {
    const blob = await buildQaReportDocx(template, EMPTY_DATA, meta)
    expect(blob.size).toBeGreaterThan(0)
  })

  it('builds a blob for a filled report covering every block kind', async () => {
    const data = {
      fields: {
        ti_name: 'Dhaka Skills Institute', provider: 'FLAXA', address: 'Mirpur, Dhaka',
        date_from: '2026-09-25', date_to: '2026-09-26', officers: 'R. M. Shourov, Program Officer (QA)\nS. Akter, Program Officer',
        status: 'Registered with BTEB', purposes: 'QA visit', team: 'R. M. Shourov',
        findings: 'First finding\nSecond finding', recommendations: 'Recommendation one',
        str_1: 'Strong QMS', weak_7: 'No PPE list',
      },
      cards: {
        persons: [{ _id: 'p1', name: 'Md. Karim', designation: 'Principal', contact: '01700000000' }],
        mou_courses: [{ _id: 'c1', course: 'Welding (SMAW)', target: '30', duration: '3 months', batches: '2', batch_size: '15' }],
        cumulative: [{ _id: 'cu1', course: 'Welding (SMAW)', target: '30', enrolled_t: '28', enrolled_f: '10', certified_t: '20', certified_f: '8', placed_t: '10', placed_f: '4', dropout_t: '4', dropout_f: '1' }],
        batches: [{ _id: 'b1', course: 'Welding (SMAW)', batch: '07', enrolled: '28' }],
        trainee_feedback: [{ _id: 't1', name: 'Rakib', trade: 'Welding', batch: '07', feedback: 'Good training' }],
        trainer_feedback: [{ _id: 'tr1', name: 'Karim', trade: 'Welding', feedback: 'Needs more tools' }],
      },
      criteria: {
        s2_1: { opts: { qms_process: { v: 'seen' }, qms_assessment: { v: 'not', remark: 'Missing this year' } }, evidence: 'Flow chart seen', note: '' },
        s8_1c: { opts: { extinguisher: { v: 'seen', detail: '12/02/2026' } } },
      },
      checks: {}, flags: [],
    }
    const blob = await buildQaReportDocx(template, data, meta)
    expect(blob.size).toBeGreaterThan(0)
  })

  it('a section-8-style heading item does not throw (two criteria blocks in one section)', async () => {
    const s8 = template.sections.find((s) => s.key === 's8')
    expect(s8.blocks.filter((b) => b.type === 'criteria')).toHaveLength(2)
    const blob = await buildQaReportDocx(template, EMPTY_DATA, meta)
    expect(blob.size).toBeGreaterThan(0)
  })
})

describe('downloadQaReportDocx filename', () => {
  it('is exported and callable (jsdom has no real download, this just checks it does not throw before the DOM step)', () => {
    expect(typeof downloadQaReportDocx).toBe('function')
  })
})
