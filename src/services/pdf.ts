type PdfGroup = { title: string; rows: Array<{ label: string; value: string }> };
type PdfOptions = {
  title: string;
  category: string;
  reference: string;
  issuedAt: string;
  groups: PdfGroup[];
  note?: string;
  filename: string;
};

const contact = ["jamesbriandungu@gmail.com", "0794523302", "Njoro, Nakuru County"];

const clean = (value: string) => value.normalize("NFKD").replace(/[^\x20-\x7E]/g, " ").replace(/[\\()]/g, "\\$&").slice(0, 100);
const text = (x: number, y: number, value: string, size = 10, color = "0.13 0.22 0.17") => `BT ${color} rg /F1 ${size} Tf ${x} ${y} Td (${clean(value)}) Tj ET`;

function pageContent(options: PdfOptions, rows: PdfGroup[], page: number, pages: number) {
  const commands = [
    "q 0.025 0.306 0.231 rg 0 730 595 112 re f Q",
    "q 0.72 0.55 0.24 rg 0 724 595 6 re f Q",
    // Compact linked-ledger mark drawn as vector artwork in the approved brand colors.
    "q 5 w 0.97 0.91 0.78 RG 30 786 m 37 793 43 793 49 787 c 53 783 53 778 49 774 c S 0.97 0.91 0.78 RG 43 781 m 37 775 31 775 25 781 c 21 785 21 790 25 794 c S 1.7 w 0.72 0.55 0.24 RG 29 784 m 45 784 l S Q",
    text(65, 790, "PesaChain", 19, "1 1 1"),
    text(65, 769, "LOAN MANAGEMENT  /  VERIFIABLE EVENT RECORDS", 7, "0.97 0.91 0.78"),
    text(46, 687, options.category.toUpperCase(), 8, "0.45 0.32 0.12"),
    text(46, 656, options.title, 20, "0.08 0.20 0.14"),
    text(46, 630, `Reference: ${options.reference}`, 9, "0.30 0.38 0.33"),
    text(46, 613, `Issued: ${options.issuedAt}`, 9, "0.30 0.38 0.33"),
    "q 0.82 0.68 0.42 RG 46 596 m 549 596 l S Q",
  ];
  let y = 575;
  let stripe = false;
  for (const group of rows) {
    commands.push(text(48, y, group.title.toUpperCase(), 8, "0.03 0.31 0.23"));
    commands.push(`q 0.97 0.91 0.79 rg 48 ${y - 5} 3 13 re f Q`);
    y -= 23;
    for (const row of group.rows) {
      commands.push(`q ${stripe ? "0.96 0.97 0.94" : "1 1 1"} rg 46 ${y - 7} 503 23 re f Q`);
      commands.push(text(56, y, row.label, 9, "0.34 0.40 0.36"));
      const value = clean(row.value);
      const estimatedWidth = Math.min(465, value.length * 5.1);
      commands.push(text(Math.max(58, 537 - estimatedWidth), y, row.value, 9, "0.08 0.20 0.14"));
      y -= 23;
      stripe = !stripe;
    }
    y -= 7;
  }
  if (options.note) {
    commands.push(`q 0.97 0.91 0.79 rg 46 123 503 47 re f Q`);
    commands.push(text(56, 151, "IMPORTANT", 7, "0.45 0.32 0.12"));
    commands.push(text(56, 133, options.note, 8, "0.18 0.27 0.22"));
  }
  commands.push("q 0.72 0.55 0.24 RG 46 91 m 549 91 l S Q");
  commands.push(text(46, 72, "PesaChain  |  Loan management and event transparency", 8, "0.03 0.31 0.23"));
  commands.push(text(46, 56, `Contact: ${contact[0]}  |  ${contact[1]}  |  ${contact[2]}`, 7, "0.34 0.40 0.36"));
  commands.push(text(500, 34, `Page ${page} of ${pages}`, 7, "0.40 0.46 0.42"));
  commands.push(text(46, 34, "Record copy generated from PesaChain operational data. Not digitally signed.", 7, "0.40 0.46 0.42"));
  return commands.join("\n");
}

/** Download a branded, printable PesaChain PDF record. This is not cryptographically signed. */
export function downloadPesaChainPdf(options: PdfOptions) {
  const groupsPerPage: PdfGroup[][] = [];
  let current: PdfGroup[] = [];
  let rowCount = 0;
  for (const group of options.groups) {
    const nextCount = group.rows.length + 1;
    if (current.length && rowCount + nextCount > 18) {
      groupsPerPage.push(current);
      current = [];
      rowCount = 0;
    }
    current.push(group);
    rowCount += nextCount;
  }
  if (current.length || !groupsPerPage.length) groupsPerPage.push(current);

  const pageStreams = groupsPerPage.map((groups, index) => pageContent(options, groups, index + 1, groupsPerPage.length));
  const pageObjectsStart = 3;
  const fontRegularId = pageObjectsStart + pageStreams.length * 2;
  const fontBoldId = fontRegularId + 1;
  const pageIds = pageStreams.map((_, index) => pageObjectsStart + index * 2);
  const objects: string[] = [
    `<< /Type /Catalog /Pages 2 0 R >>`,
    `<< /Type /Pages /Kids [${pageIds.map((id) => `${id} 0 R`).join(" ")}] /Count ${pageIds.length} >>`,
  ];
  pageStreams.forEach((stream, index) => {
    const pageId = pageIds[index];
    const contentId = pageId + 1;
    objects.push(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 ${fontRegularId} 0 R /F2 ${fontBoldId} 0 R >> >> /Contents ${contentId} 0 R >>`);
    objects.push(`<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`);
  });
  objects.push("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
  objects.push("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>");

  let pdf = "%PDF-1.4\n";
  const offsets = [0];
  objects.forEach((object, index) => {
    offsets.push(pdf.length);
    pdf += `${index + 1} 0 obj\n${object}\nendobj\n`;
  });
  const xrefOffset = pdf.length;
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  for (const offset of offsets.slice(1)) pdf += `${String(offset).padStart(10, "0")} 00000 n \n`;
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF`;
  const blob = new Blob([pdf], { type: "application/pdf" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = options.filename;
  anchor.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
