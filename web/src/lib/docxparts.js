// docx pieces every report Word file shares (surprise form, narrative, QA): the numbered-list
// definition, a "<title> · Page X of Y" footer and the A4 section properties. Fonts and sizes
// stay with each file because the surprise (Arial) and QA (Times) looks differ.
import {
  AlignmentType, Footer, LevelFormat, PageNumber, PageOrientation, Paragraph, Tab, TabStopType, TextRun,
} from 'docx'
import {
  CONTENT_WIDTH_TWIPS, MARGIN_BOTTOM_TWIPS, MARGIN_LEFT_TWIPS, MARGIN_RIGHT_TWIPS, MARGIN_TOP_TWIPS,
  PAGE_HEIGHT_TWIPS, PAGE_WIDTH_TWIPS,
} from './reportlayout.js'

const NUMBERED_REFERENCE = 'report-numbered'
const LIST_INDENT_TWIPS = 360

// one abstract "1." list; each list on the page gets its own instance so it restarts at 1
export const NUMBERING = {
  config: [{
    reference: NUMBERED_REFERENCE,
    levels: [{
      level: 0,
      format: LevelFormat.DECIMAL,
      text: '%1.',
      alignment: AlignmentType.START,
      style: { paragraph: { indent: { left: LIST_INDENT_TWIPS, hanging: LIST_INDENT_TWIPS } } },
    }],
  }],
}

let nextListInstance = 1

// lines -> real Word numbered paragraphs (1., 2., ...); makeRuns(text) builds each line's runs
export function numberedParagraphs(lines, makeRuns) {
  const instance = nextListInstance++
  return lines.map((text) => new Paragraph({
    numbering: { reference: NUMBERED_REFERENCE, level: 0, instance },
    children: makeRuns(text),
  }))
}

// title bottom-left, "Page X of Y" bottom-right
export function pageFooter(title, runOptions) {
  const textRun = (text) => new TextRun({ text, ...runOptions })
  return new Footer({
    children: [
      new Paragraph({
        tabStops: [{ type: TabStopType.RIGHT, position: CONTENT_WIDTH_TWIPS }],
        children: [
          textRun(title),
          new TextRun({ children: [new Tab()], ...runOptions }),
          textRun('Page '),
          new TextRun({ children: [PageNumber.CURRENT], ...runOptions }),
          textRun(' of '),
          new TextRun({ children: [PageNumber.TOTAL_PAGES], ...runOptions }),
        ],
      }),
    ],
  })
}

export const A4_PAGE = {
  size: { orientation: PageOrientation.PORTRAIT, width: PAGE_WIDTH_TWIPS, height: PAGE_HEIGHT_TWIPS },
  margin: { top: MARGIN_TOP_TWIPS, right: MARGIN_RIGHT_TWIPS, bottom: MARGIN_BOTTOM_TWIPS, left: MARGIN_LEFT_TWIPS },
}
