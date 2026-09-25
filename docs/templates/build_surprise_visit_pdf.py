"""Build the compact A4 paper version of the SICIP surprise visit report.

The checklist questions and critical flags are read from the web checklist
(surprise-visit-checklist.html), so editing the lists there and re-running
this script keeps the paper form and the web form identical.

Usage:  python3 build_surprise_visit_pdf.py
Output: surprise-visit-report-template.html and surprise-visit-report-template.pdf
"""

import html
import re
import subprocess
from pathlib import Path

PROJECT_FOLDER = Path(__file__).resolve().parent
WEB_CHECKLIST = PROJECT_FOLDER / "surprise-visit-checklist.html"
PRINT_HTML = PROJECT_FOLDER / "surprise-visit-report-template.html"
OUTPUT_PDF = PROJECT_FOLDER / "surprise-visit-report-template.pdf"
BROWSER = "helium-browser"


def read_js_block(source, start_marker):
    """Return the text between a JS declaration marker and its closing bracket."""
    start = source.index(start_marker) + len(start_marker)
    closing = "};" if start_marker.endswith("{") else "];"
    end = source.index(closing, start)
    return source[start:end]


def read_checklists(source):
    block = read_js_block(source, "const CHECKLISTS = {")
    checklists = {}
    for match in re.finditer(r"(\w+):\s*\[(.*?)\]", block, re.DOTALL):
        list_name, body = match.groups()
        checklists[list_name] = re.findall(r'"((?:[^"\\]|\\.)*)"', body)
    return checklists


def read_flags(source):
    block = read_js_block(source, "const FLAGS = [")
    return re.findall(r'"((?:[^"\\]|\\.)*)"', block)


BOX = '<span class="box"></span>'


def checklist_table(questions, first_number=1):
    rows = []
    for offset, question in enumerate(questions):
        rows.append(
            f"<tr><td class='num'>{first_number + offset}</td>"
            f"<td class='question'>{html.escape(question)}</td>"
            f"<td class='tick'>{BOX}</td><td class='tick'>{BOX}</td>"
            f"<td class='tick'>{BOX}</td><td class='tick'>{BOX}</td>"
            f"<td class='remarks'></td></tr>"
        )
    return (
        "<table class='checklist'>"
        "<colgroup><col class='c-num'><col class='c-question'><col class='c-tick'>"
        "<col class='c-tick'><col class='c-tick'><col class='c-tick'><col class='c-remarks'></colgroup>"
        "<thead><tr><th>#</th><th>Item</th><th>Yes</th><th>No</th><th>Part</th><th>N/A</th>"
        "<th>Remarks</th></tr></thead>"
        f"<tbody>{''.join(rows)}</tbody></table>"
    )


def blank_rows(cell_count, row_count, number_first_cell=True, tick_columns=()):
    """Empty rows for handwriting; columns listed in tick_columns get a tick box."""
    rows = []
    for row_number in range(1, row_count + 1):
        cells = []
        for column in range(cell_count):
            if column == 0 and number_first_cell:
                cells.append(f"<td class='num'>{row_number}</td>")
            elif column in tick_columns:
                cells.append(f"<td class='tick'>{BOX}</td>")
            else:
                cells.append("<td></td>")
        rows.append(f"<tr>{''.join(cells)}</tr>")
    return "".join(rows)


def section_heading(letter, title, note=""):
    note_html = f"<span class='note'>{html.escape(note)}</span>" if note else ""
    return f"<h2><span class='letter'>{letter}</span>{html.escape(title)}{note_html}</h2>"


def build_page(checklists, flags):
    attendance_table = (
        "<table class='grid attendance'>"
        "<thead>"
        "<tr><th rowspan='2'>Course</th><th rowspan='2' class='batch'>Batch no(s).</th><th colspan='2'>Enrolled</th><th colspan='2'>Headcount</th>"
        "<th rowspan='2'>Present in register</th><th rowspan='2'>Present in TMS</th>"
        "<th rowspan='2'>Trainer present Y / N</th><th rowspan='2' class='wide'>Remarks</th></tr>"
        "<tr><th>T</th><th>F</th><th>T</th><th>F</th></tr>"
        "</thead>"
        f"<tbody>{blank_rows(10, 4, number_first_cell=False)}</tbody></table>"
    )

    identity_table = (
        "<table class='grid'>"
        "<thead><tr><th class='num'>#</th><th>Name (from TMS)</th><th>Course / batch</th>"
        "<th class='tick-head'>Present &amp; verified</th><th class='tick-head'>Absent</th>"
        "<th class='tick-head'>Doesn't match</th><th class='wide'>Remarks</th></tr></thead>"
        f"<tbody>{blank_rows(7, 5, tick_columns=(3, 4, 5))}</tbody></table>"
    )

    graduate_table = (
        "<table class='grid'>"
        "<thead><tr><th class='num'>#</th><th>Name</th><th>Course / batch</th><th>Phone</th>"
        "<th>Employer as per TMS</th><th>Reached Y / N</th>"
        "<th>Employed: Same / Other / None</th><th>Position, salary (BDT)</th><th class='wide'>Remarks</th></tr></thead>"
        f"<tbody>{blank_rows(9, 5)}</tbody></table>"
    )

    followup_table = (
        "<table class='grid'>"
        "<thead><tr><th class='num'>#</th><th class='wide'>Issue raised in last visit</th>"
        "<th class='tick-head'>Resolved</th><th class='tick-head'>Partly</th>"
        "<th class='tick-head'>Not resolved</th><th class='wide'>Remarks</th></tr></thead>"
        f"<tbody>{blank_rows(6, 3, tick_columns=(2, 3, 4))}</tbody></table>"
    )

    flag_items = "".join(f"<li>{BOX}<span>{html.escape(flag)}</span></li>" for flag in flags)

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>SICIP Surprise Visit Report</title>
<style>
  @page {{
    size: A4 portrait;
    margin: 10mm 11mm 12mm;
    @bottom-left {{ content: "SICIP Surprise Visit Report"; font: 7pt "Noto Sans", Arial, sans-serif; color: #555; }}
    @bottom-right {{ content: "Page " counter(page) " of " counter(pages); font: 7pt "Noto Sans", Arial, sans-serif; color: #555; }}
  }}
  * {{ box-sizing: border-box; }}
  body {{ margin: 0; font-family: "Noto Sans", "Segoe UI", Arial, sans-serif; font-size: 8.4pt; line-height: 1.25; color: #111; }}

  header {{ display: flex; justify-content: space-between; align-items: flex-end; border-bottom: 1.6pt solid #111; padding-bottom: 4pt; margin-bottom: 6pt; }}
  header .program {{ font-size: 8pt; }}
  header h1 {{ margin: 1pt 0 0; font-size: 13pt; font-weight: 700; letter-spacing: 0.01em; }}
  header .form-code {{ font-size: 7.5pt; text-align: right; color: #333; }}

  h2 {{ display: flex; align-items: baseline; gap: 5pt; margin: 8pt 0 3pt; font-size: 9.2pt; font-weight: 700; break-after: avoid; }}
  h2 .letter {{ display: inline-block; min-width: 13pt; padding: 0.5pt 0; text-align: center; background: #111; color: #fff; font-size: 8pt; }}
  h2 .note {{ margin-left: auto; font-weight: 400; font-size: 7.4pt; font-style: italic; color: #333; }}
  h3 {{ margin: 4pt 0 2pt; font-size: 8.2pt; font-weight: 700; }}

  .details {{ display: grid; grid-template-columns: 1fr 1fr; column-gap: 12pt; row-gap: 3pt; }}
  .details .line {{ display: flex; align-items: flex-end; gap: 4pt; min-height: 15pt; }}
  .details .line.full {{ grid-column: 1 / -1; }}
  .details .label {{ white-space: nowrap; }}
  .details .rule {{ flex: 1; border-bottom: 0.6pt solid #555; height: 11pt; }}
  .details .triple {{ grid-column: 1 / -1; display: grid; grid-template-columns: 1.2fr 1fr 1fr; column-gap: 12pt; }}

  table {{ width: 100%; border-collapse: collapse; table-layout: fixed; }}
  th, td {{ border: 0.6pt solid #666; padding: 2pt 3pt; vertical-align: middle; }}
  th {{ background: #e6e6e6; font-weight: 700; font-size: 7.4pt; text-align: center; line-height: 1.15; }}
  tr {{ break-inside: avoid; }}
  td.num, th.num {{ width: 16pt; text-align: center; }}

  .checklist col.c-num {{ width: 16pt; }}
  .checklist col.c-question {{ width: 44%; }}
  .checklist col.c-tick {{ width: 24pt; }}
  .checklist td {{ height: 16pt; }}
  .checklist td.tick {{ text-align: center; }}
  .checklist td.num {{ color: #333; }}

  .grid td {{ height: 17pt; }}
  .grid th.wide {{ width: 24%; }}
  .grid th.tick-head {{ width: 44pt; }}
  .grid td.tick {{ text-align: center; }}
  .attendance th:first-child {{ width: 18%; }}
  .attendance th.batch {{ width: 10%; }}

  .box {{ display: inline-block; width: 8pt; height: 8pt; border: 0.7pt solid #111; vertical-align: middle; }}

  .inline-fields {{ display: flex; gap: 12pt; margin: 2pt 0 3pt; }}
  .inline-fields .line {{ display: flex; align-items: flex-end; gap: 4pt; flex: 1; }}
  .inline-fields .rule {{ flex: 1; border-bottom: 0.6pt solid #555; height: 11pt; }}

  .flags {{ columns: 2; column-gap: 14pt; margin: 0; padding: 0; list-style: none; }}
  .flags li {{ display: flex; gap: 5pt; align-items: flex-start; padding: 1.8pt 0; break-inside: avoid; }}
  .flags .box {{ flex: none; margin-top: 1pt; }}

  .choice-row {{ display: flex; flex-wrap: wrap; gap: 4pt 14pt; align-items: center; margin: 2pt 0 4pt; }}
  .choice-row .label {{ font-weight: 700; margin-right: 2pt; }}
  .choice-row span.choice {{ display: inline-flex; align-items: center; gap: 4pt; }}
  .write-box {{ border: 0.6pt solid #666; margin: 1pt 0 5pt; }}
  .write-box.findings {{ height: 78pt; }}
  .write-box.instructions {{ height: 46pt; }}
  .write-box.feedback {{ height: 30pt; }}

  .keep {{ break-inside: avoid; }}
  footer.legend {{ margin-top: 6pt; font-size: 7pt; color: #333; }}
</style>
</head>
<body>

<header>
  <div>
    <div class="program">Skills for Industry Competitiveness and Innovation Program (SICIP)</div>
    <h1>Surprise Visit Report: Quality Assurance</h1>
  </div>
  <div class="form-code">Unannounced visit to partner TI/TC</div>
</header>

<section class="keep">
  {section_heading("A", "Visit details")}
  <div class="details">
    <div class="line full"><span class="label">Name of TI/TC visited</span><span class="rule"></span></div>
    <div class="line"><span class="label">Association / provider</span><span class="rule"></span></div>
    <div class="line"><span class="label">Received by (name, designation)</span><span class="rule"></span></div>
    <div class="line full"><span class="label">Address and contact</span><span class="rule"></span></div>
    <div class="triple">
      <div class="line"><span class="label">Date of visit</span><span class="rule"></span></div>
      <div class="line"><span class="label">Arrival time</span><span class="rule"></span></div>
      <div class="line"><span class="label">Departure time</span><span class="rule"></span></div>
    </div>
    <div class="line full"><span class="label">Visiting officer(s) with designation</span><span class="rule"></span></div>
    <div class="line full"><span class="label">Courses / batches scheduled today (routine or TDP)</span><span class="rule"></span></div>
  </div>
</section>

<section class="keep">
  {section_heading("B", "Status on arrival")}
  {checklist_table(checklists["arrival"])}
</section>

<section class="keep">
  {section_heading("C", "Attendance in each course", "Count heads first, then check the register and TMS. One row per course.")}
  {attendance_table}
  <h3>Attendance register</h3>
  {checklist_table(checklists["attendance_register"])}
</section>

<section class="keep">
  {section_heading("D", "Trainee identity spot-check", "5 names picked at random from the TMS enrolment list")}
  {identity_table}
  <h3>Enrolment</h3>
  {checklist_table(checklists["identity"])}
</section>

<section class="keep">
  {section_heading("E", "Materials and safety gear provided")}
  {checklist_table(checklists["materials"])}
</section>

<section class="keep">
  {section_heading("F", "Training delivery observation", "Observe one class or workshop for about 15 minutes")}
  {checklist_table(checklists["delivery"])}
</section>

<section class="keep">
  {section_heading("G", "Formative assessment", "Latest assessment paper and result sheet of one running batch")}
  {checklist_table(checklists["assessment"])}
</section>

<section class="keep">
  {section_heading("H", "Daily registers")}
  {checklist_table(checklists["registers"])}
</section>

<section class="keep">
  {section_heading("I", "Trainee interviews (no staff present)", "General response of a group of trainees")}
  <div class="inline-fields">
    <div class="line"><span>Trainees interviewed</span><span class="rule"></span></div>
    <div class="line"><span>Course(s)</span><span class="rule"></span></div>
  </div>
  {checklist_table(checklists["interview"])}
  <h3>Complaints or other feedback</h3>
  <div class="write-box feedback"></div>
</section>

<section class="keep">
  {section_heading("J", "Job placement verification calls", "5 graduates from the TMS job-placed list, called by the officer")}
  {graduate_table}
</section>

<section class="keep">
  {section_heading("K", "Previous visit follow-up")}
  <div class="inline-fields">
    <div class="line"><span>Date of last visit</span><span class="rule"></span></div>
    <div class="line"><span>Type: {BOX} Full QA &nbsp; {BOX} Monitoring &nbsp; {BOX} Surprise</span></div>
  </div>
  {followup_table}
</section>

<section class="keep">
  {section_heading("L", "Critical non-compliance flags", "Tick each flag observed and explain it under key findings")}
  <ul class="flags">{flag_items}</ul>
</section>

<section class="keep">
  {section_heading("M", "Overall rating and action")}
  <div class="choice-row">
    <span class="label">Overall rating:</span>
    <span class="choice">{BOX} Satisfactory</span>
    <span class="choice">{BOX} Needs improvement</span>
    <span class="choice">{BOX} Unsatisfactory</span>
  </div>
  <h3>Key findings</h3>
  <div class="write-box findings"></div>
  <h3>Instructions given to the TI on the spot</h3>
  <div class="write-box instructions"></div>
  <div class="choice-row">
    <span class="label">Recommended follow-up:</span>
    <span class="choice">{BOX} No further action</span>
    <span class="choice">{BOX} Full QA visit</span>
    <span class="choice">{BOX} Written explanation from TI</span>
    <span class="choice">{BOX} Escalate to PIU</span>
  </div>
</section>

<footer class="legend">T = total, F = female, TMS = Training Management System, TDP = training delivery plan, CS = competency standard, CBLM = competency-based learning material, PPE = personal protective equipment, OHS = occupational health and safety.</footer>

</body>
</html>
"""


def main():
    source = WEB_CHECKLIST.read_text(encoding="utf-8")
    page = build_page(read_checklists(source), read_flags(source))
    PRINT_HTML.write_text(page, encoding="utf-8")
    subprocess.run(
        [
            BROWSER,
            "--headless",
            "--disable-gpu",
            "--no-pdf-header-footer",
            f"--print-to-pdf={OUTPUT_PDF}",
            PRINT_HTML.as_uri(),
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    print(f"Wrote {OUTPUT_PDF}")


if __name__ == "__main__":
    main()
