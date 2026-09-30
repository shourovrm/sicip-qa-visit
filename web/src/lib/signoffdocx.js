// Word version of signoff.js's "Submitted by-" block: a borderless table, up to three officers
// per row, each cell = signature space, name, designation, organisation
import { BorderStyle, Paragraph, Table, TableCell, TableLayoutType, TableRow, TextRun, WidthType } from 'docx'
import { CONTENT_WIDTH_TWIPS } from './reportlayout.js'
import { SIGNOFF_ORGANISATION, visitingOfficers } from './signoff.js'

const OFFICERS_PER_ROW = 3
const NO_BORDER = { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' }
const NO_BORDERS = {
  top: NO_BORDER, bottom: NO_BORDER, left: NO_BORDER, right: NO_BORDER, insideHorizontal: NO_BORDER, insideVertical: NO_BORDER,
}

function officerCell(officer, widthTwips) {
  const line = (text, bold = false) => new Paragraph({ children: [new TextRun({ text, bold })] })
  return new TableCell({
    width: { size: widthTwips, type: WidthType.DXA },
    borders: NO_BORDERS,
    children: [line(''), line(''), line(''), line(officer.name, true), line(officer.designation), line(SIGNOFF_ORGANISATION)],
  })
}

export function signoffDocx(data) {
  const officers = visitingOfficers(data)
  const cellWidth = Math.floor(CONTENT_WIDTH_TWIPS / OFFICERS_PER_ROW)
  const rows = []
  for (let start = 0; start < officers.length; start += OFFICERS_PER_ROW) {
    const cells = officers.slice(start, start + OFFICERS_PER_ROW).map((officer) => officerCell(officer, cellWidth))
    // pad short rows so every row has the same column grid
    while (cells.length < OFFICERS_PER_ROW) {
      cells.push(new TableCell({ width: { size: cellWidth, type: WidthType.DXA }, borders: NO_BORDERS, children: [new Paragraph('')] }))
    }
    rows.push(new TableRow({ cantSplit: true, children: cells }))
  }
  return [
    new Paragraph({ spacing: { before: 360 }, keepNext: true, children: [new TextRun('Submitted by-')] }),
    new Table({
      width: { size: CONTENT_WIDTH_TWIPS, type: WidthType.DXA },
      columnWidths: Array(OFFICERS_PER_ROW).fill(cellWidth),
      layout: TableLayoutType.FIXED,
      borders: NO_BORDERS,
      rows,
    }),
  ]
}
