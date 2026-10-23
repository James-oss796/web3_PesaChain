import fs from 'node:fs/promises';
import path from 'node:path';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const PptxGenJS = require('C:/Users/Admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/pptxgenjs/dist/pptxgen.cjs.js');

const root = process.cwd();
const outDir = path.join(root, 'presentation_output');
await fs.mkdir(outDir, { recursive: true });
const outPath = path.join(outDir, 'PesaChain_Project_Presentation.pptx');
const pptx = new PptxGenJS();
pptx.layout = 'LAYOUT_WIDE';
pptx.author = 'PesaChain';
pptx.subject = 'Microfinance loan management and selected-event transparency';
pptx.title = 'PesaChain — A clearer loan journey';
pptx.company = 'PesaChain';
pptx.lang = 'en-KE';
pptx.theme = { headFontFace: 'Aptos Display', bodyFontFace: 'Aptos', lang: 'en-KE' };
const C = { green:'064E3B', dark:'052D24', gold:'D6AE70', cream:'F8E7C9', paper:'F7F5EF', white:'FFFFFF', ink:'17241F', muted:'5F6C64', line:'D8DED8', pale:'E8EFEA', paleGold:'F3E8D4', red:'A94136', blue:'638A7A' };
const S = pptx.ShapeType;
const logo = path.join(root, 'pesachain-logo-green-gold.png');
const diag = (name) => path.join(root, 'PesaChain_Mermaid_Diagrams_PNG', name);

function tx(slide, text, x,y,w,h, opts={}) {
  slide.addText(text, { x,y,w,h, margin:0, breakLine:false, fontFace:opts.fontFace||'Aptos', fontSize:opts.size||16, bold:opts.bold||false, color:opts.color||C.ink, valign:opts.valign||'mid', align:opts.align||'left', fit:'shrink', charSpacing:opts.charSpacing, paraSpaceAfterPt:opts.paraSpaceAfterPt, bullet:opts.bullet, isTextBox:true, transparency:opts.transparency, ...opts.extra });
}
function shape(slide, type, x,y,w,h, fill, line=fill, extra={}) {
  slide.addShape(type, { x,y,w,h, rectRadius:0.12, fill:{color:fill}, line:{color:line, width:0.8}, ...extra });
}
function line(slide,x1,y1,x2,y2,color=C.line,width=1.4,dash) {
  slide.addShape(S.line,{x:x1,y:y1,w:x2-x1,h:y2-y1,line:{color,width,beginArrowType:'none',endArrowType:'none',...(dash?{dashType:dash}:{})}});
}
function title(slide, eyebrow, heading, sub='') {
  tx(slide, eyebrow.toUpperCase(), 0.72,0.62,11.9,0.26,{size:10,bold:true,color:C.green,charSpacing:1.25});
  tx(slide, heading, 0.72,0.99,11.9,0.7,{size:31,bold:true,color:C.dark});
  if(sub) tx(slide,sub,0.74,1.76,11.3,0.55,{size:15,color:C.muted,valign:'top'});
}
function note(slide, text) { slide.addNotes(text); }
function addImageContain(slide, p,x,y,w,h) {
  slide.addImage({ path:p, x,y,w,h, sizingContain:true });
}
function newSlide() {
  const s=pptx.addSlide(); s.background={color:C.paper};
  line(s,0.55,0.48,12.78,0.48,C.line,0.7);
  tx(s,'PesaChain',0.58,0.17,2.1,0.22,{size:9,bold:true,color:C.green});
  tx(s,'LOAN MANAGEMENT  /  EVENT TRANSPARENCY',8.8,0.17,3.95,0.22,{size:8,color:C.muted,align:'right',charSpacing:1.1});
  tx(s,String(pptx._slides.length).padStart(2,'0'),12.3,7.12,0.45,0.2,{size:8,color:C.muted,align:'right'});
  return s;
}
function stage(slide, x,y,w,index,label,detail,tone=C.green) {
  shape(slide,S.roundRect,x,y,w,1.28,C.white,C.line);
  shape(slide,S.ellipse,x+0.17,y+0.19,0.36,0.36,tone,tone);
  tx(slide,String(index).padStart(2,'0'),x+0.18,y+0.2,0.34,0.3,{size:9,bold:true,color:C.white,align:'center'});
  tx(slide,label,x+0.66,y+0.16,w-0.82,0.35,{size:15,bold:true,color:C.dark});
  tx(slide,detail,x+0.66,y+0.58,w-0.84,0.53,{size:11.5,color:C.muted,valign:'top'});
}
function bullet(slide, text, x,y,w, opts={}) {
  shape(slide,S.ellipse,x,y+0.1,0.10,0.10,opts.dot||C.gold,opts.dot||C.gold);
  tx(slide,text,x+0.22,y,w-0.22,opts.h||0.5,{size:opts.size||15,color:opts.color||C.ink,valign:'top'});
}

// 1 — cover
{
  const s=pptx.addSlide(); s.background={color:C.dark};
  s.addShape(S.rect,{x:0,y:0,w:13.333,h:7.5,fill:{color:C.dark},line:{color:C.dark}});
  s.addShape(S.rect,{x:8.45,y:0,w:4.883,h:7.5,fill:{color:C.green},line:{color:C.green}});
  s.addShape(S.arc,{x:8.4,y:0.35,w:4.55,h:6.7,line:{color:C.gold,width:1.6,transparency:22},adjustPoint:0.3,rotate:12,fill:{color:C.green,transparency:100}});
  s.addImage({path:logo,x:0.78,y:0.6,w:0.78,h:0.78});
  tx(s,'PesaChain',1.7,0.77,3.8,0.45,{size:23,bold:true,color:C.white});
  tx(s,'A clearer loan journey,\nwith selected events\nindependently recorded.',0.8,2.0,7.6,2.0,{size:32,bold:true,color:C.white,valign:'mid'});
  tx(s,'Microfinance loan management and transparency',0.82,4.38,6.9,0.5,{size:17,color:'D7E4DC'});
  tx(s,'Academic system presentation  ·  Kenya  ·  2026',0.82,6.55,6.9,0.3,{size:11,color:'C3D2C9'});
  // Editorial lifecycle line
  const labels=['APPLY','REVIEW','DISBURSE','REPAY'];
  line(s,9.18,2.35,12.25,2.35,'D9C093',1.8);
  labels.forEach((l,i)=>{const x=9.05+i*1.05;shape(s,S.ellipse,x,2.18,0.34,0.34,i===3?C.cream:C.gold,i===3?C.cream:C.gold);tx(s,String(i+1),x,2.20,0.34,0.26,{size:9,bold:true,color:C.dark,align:'center'});tx(s,l,x-0.27,2.75,0.90,0.3,{size:8,bold:true,color:C.white,align:'center',charSpacing:0.6});});
  tx(s,'Operational records stay in PostgreSQL. Selected financial events receive separate chain evidence.',9.0,4.0,3.45,1.18,{size:17,bold:true,color:C.white,valign:'top'});
  tx(s,'PesaChain does not move money.',9.0,5.55,3.4,0.4,{size:12,bold:true,color:C.cream});
  note(s,'Opening: PesaChain is a loan management and transparency system built for an academic demonstration. It structures borrower applications, human decisions, full disbursement and simulated repayment handling. The blockchain layer records selected events as separate evidence; it is not a payment rail.');
}
// 2 — problem
{
  const s=newSlide(); title(s,'The need','A loan journey needs a clear record at every step.','PesaChain brings application, decision, servicing and evidence into one role-aware workflow.');
  tx(s,'WHEN RECORDS ARE FRAGMENTED',0.75,2.55,4.9,0.28,{size:10,bold:true,color:C.muted,charSpacing:1});
  const y=3.05; const fragments=[['01','Request','Application record'],['02','Decision','Officer notes'],['03','Service','Disbursement & repayment'],['04','Evidence','Event confirmation']];
  fragments.forEach((it,i)=>{const x=0.78+i*2.0;shape(s,S.roundRect,x,y,1.73,1.22,i===3?C.paleGold:C.white,C.line);tx(s,it[0],x+0.15,y+0.12,0.42,0.24,{size:9,bold:true,color:C.green});tx(s,it[1],x+0.15,y+0.44,1.4,0.25,{size:14,bold:true,color:C.dark});tx(s,it[2],x+0.15,y+0.78,1.4,0.34,{size:10,color:C.muted,valign:'top'});if(i<3)line(s,x+1.74,y+0.62,x+1.97,y+0.62,C.gold,1.4);});
  shape(s,S.rect,0.78,4.8,11.8,1.02,C.green,C.green);
  tx(s,'The product goal',1.05,5.03,2.1,0.32,{size:13,bold:true,color:C.cream});
  tx(s,'Give borrowers a clear view of their own records and give staff a consistent process to act on.',3.15,4.98,8.9,0.5,{size:19,bold:true,color:C.white});
  tx(s,'Problem framing is a product rationale, not a measured claim about all microfinance providers.',0.8,6.13,11.5,0.35,{size:10,color:C.muted});
  note(s,'Use this slide to state the design problem as a system problem: loan information can be hard to follow when each step is handled in a different record or process. Avoid claiming that all institutions have this problem. PesaChain demonstrates one structured workflow and a separate evidence trail.');
}
// 3 — roles
{
  const s=newSlide(); title(s,'Product model','Three roles, with distinct responsibilities.','The backend enforces access. The interface reflects those permissions but does not act as the security boundary.');
  const entries=[
    {x:0.8,n:'Borrower',cap:'Own records',color:C.green,items:['Register and submit an application','Follow decisions and approved terms','Initiate a simulated repayment','View own repayment and event evidence']},
    {x:4.68,n:'Loan Officer',cap:'Human decisions',color:'8E6A34',items:['Review the assigned application queue','Approve or reject with rationale','Record one full disbursement','Verify simulations and record repayments']},
    {x:8.56,n:'Administrator',cap:'System oversight',color:'4E7567',items:['Provision and manage staff accounts','Review audit activity','Monitor blockchain delivery','View privacy-minimized aggregate reports']},
  ];
  for(const e of entries){
    tx(s,e.cap.toUpperCase(),e.x,2.42,3.5,0.25,{size:9,bold:true,color:e.color,charSpacing:1});
    tx(s,e.n,e.x,2.8,3.55,0.52,{size:22,bold:true,color:C.dark});
    line(s,e.x,3.45,e.x+3.28,3.45,C.line,1.2);
    e.items.forEach((a,i)=>bullet(s,a,e.x,3.72+i*0.59,3.45,{size:12,h:0.46,dot:e.color}));
  }
  shape(s,S.roundRect,0.82,6.28,11.65,0.46,C.pale,C.pale);tx(s,'Administrators do not inherit Loan Officer lending permissions.',1.02,6.34,11.2,0.3,{size:12,bold:true,color:C.green,align:'center'});
  note(s,'Explain role separation explicitly. Borrowers are scoped to their records. Loan Officers make human lending decisions and perform official servicing actions. Administrators manage accounts and system oversight but do not receive Officer permissions by inheritance.');
}
// 4 — lifecycle
{
  const s=newSlide(); title(s,'End-to-end workflow','A loan moves through explicit, persisted states.','The Officer decides. The borrower sees the result. A successful full disbursement moves the loan into ACTIVE.');
  const labels=[['Apply','Borrower request\namount + term'],['Review','Officer decision\nrationale required'],['Approved','Authoritative terms\nflat-rate calculation'],['Disbursed','One full disbursement\nof approved principal'],['Active','Borrower may use\nsimulated repayment'],['Completed','Balance reaches\nKES 0.00']];
  labels.forEach((it,i)=>{const x=0.78+i*2.08; stage(s,x,2.95,1.84,i+1,it[0],it[1],i===4?C.green:C.blue);if(i<5){line(s,x+1.85,3.59,x+2.03,3.59,C.gold,1.6);}});
  tx(s,'Operational status',0.83,5.02,2.1,0.3,{size:11,bold:true,color:C.green});
  tx(s,'APPROVED → DISBURSED → ACTIVE → COMPLETED',2.9,4.97,8.7,0.38,{size:17,bold:true,color:C.dark});
  tx(s,'Blockchain delivery status is tracked separately and never replaces the loan state.',0.84,5.72,10.8,0.4,{size:15,color:C.muted});
  note(s,'Walk through the ordinary workflow. Approval creates the loan with approved principal, term and flat rate. Disbursement is one full amount equal to the approved principal. ACTIVE is distinct from APPROVED and is required before repayment simulation. Completion occurs when the remaining contractual balance is exactly zero under the stored monetary precision.');
}
// 5 — product journeys and assets
{
  const s=newSlide(); title(s,'Borrower experience','A borrower can follow the request without seeing another person’s data.','The borrower workspace presents the application decision and live loan lifecycle as separate information.');
  addImageContain(s,path.join(root,'PesaChain_Figma_Import_SVG','screens','27-borrower-application-history.svg'),0.83,2.3,5.6,3.85);
  tx(s,'What the borrower can understand',6.9,2.48,5.2,0.42,{size:19,bold:true,color:C.dark});
  const lines=['Requested amount and term remain visible','Approved terms are shown separately','Loan status advances after full disbursement','A simulation does not change the official balance','Repayment history reflects only recorded repayments'];
  lines.forEach((a,i)=>bullet(s,a,6.94,3.15+i*0.6,5.4,{size:13,h:0.48}));
  tx(s,'Illustrative wireframe from the supplied design set',0.88,6.34,5.45,0.28,{size:9,color:C.muted});
  note(s,'The screenshot is a supplied wireframe, not a claim that a particular borrower dataset is loaded. The live application uses API data. Explain that the original request and approved loan terms must remain distinct in the interface.');
}
// 6 — officer
{
  const s=newSlide(); title(s,'Officer experience','Decision support, without automated credit scoring.','The Officer reviews facts and records a reasoned human decision. PesaChain does not approve loans automatically.');
  addImageContain(s,path.join(root,'PesaChain_Figma_Import_SVG','screens','11-officer-application-review.svg'),0.84,2.22,5.85,3.95);
  tx(s,'Controls that make the decision auditable',7.03,2.44,5.2,0.48,{size:19,bold:true,color:C.dark});
  ['Requested principal stays unchanged','Officer may adjust approved principal and term','Flat rate and total repayable are calculated from approved terms','Decision rationale is mandatory','Rejection requires a borrower-readable reason'].forEach((v,i)=>bullet(s,v,7.05,3.2+i*0.57,5.1,{size:12.5,h:0.45}));
  tx(s,'No credit score or automated recommendation is produced.',7.05,6.17,5.2,0.34,{size:12,bold:true,color:C.green});
  note(s,'Clarify the exclusion of credit scoring. Officers may review recorded factual repayment and loan history if authorized, but the system does not compute a score or suggest that a borrower should or should not receive a loan. Approval remains a human decision.');
}
// 7 — finance
{
  const s=newSlide(); title(s,'Approved financial model','Flat interest is based on the original approved principal.','KES is used throughout. The agreed term is measured in months; calculations round to two decimal places using half-up rounding.');
  shape(s,S.rect,0.82,2.47,4.0,3.05,C.green,C.green);
  tx(s,'EXAMPLE',1.1,2.78,3.2,0.25,{size:10,bold:true,color:C.cream,charSpacing:1.4});
  tx(s,'KES 12,000',1.1,3.24,3.4,0.62,{size:32,bold:true,color:C.white});
  tx(s,'10% flat · 6 months',1.1,4.0,3.4,0.42,{size:17,color:'D7E4DC'});
  line(s,1.1,4.62,4.47,4.62,'6F9687',1.1);
  tx(s,'Interest  KES 1,200',1.1,4.88,3.4,0.35,{size:15,bold:true,color:C.white});
  tx(s,'Total due  KES 13,200',1.1,5.3,3.4,0.35,{size:15,bold:true,color:C.cream});
  tx(s,'Calculation',5.55,2.65,3.0,0.32,{size:11,bold:true,color:C.green,charSpacing:0.7});
  tx(s,'Principal × flat rate',5.55,3.08,5.6,0.48,{size:21,bold:true,color:C.dark});
  tx(s,'KES 12,000 × 10% = KES 1,200',5.55,3.62,6.0,0.4,{size:16,color:C.muted});
  tx(s,'Total repayable',5.55,4.35,4.0,0.32,{size:11,bold:true,color:C.green,charSpacing:0.7});
  tx(s,'Principal + interest',5.55,4.73,5.6,0.48,{size:21,bold:true,color:C.dark});
  tx(s,'KES 12,000 + KES 1,200 = KES 13,200',5.55,5.28,6.8,0.4,{size:16,color:C.muted});
  tx(s,'No fees, compounding, penalties, reducing-balance interest, or partial disbursement.',0.85,6.2,11.5,0.4,{size:12,bold:true,color:C.red});
  note(s,'This is an illustrative calculation from the approved policy, not a loan record. Principal and term approved by the Officer are authoritative. Flat interest uses original principal for the agreed whole term. A zero-percent rate is valid. No fees, compounding, reducing balance, late fees or partial disbursement are implemented.');
}
// 8 — repayment boundary
{
  const s=newSlide(); title(s,'Repayment flow','A simulation is not an official repayment.','The Officer verifies the signed simulation and records the official repayment; only then does PostgreSQL reduce the balance.');
  const x0=0.92,y=3.1,w=2.25;
  const blocks=[['01','Borrower initiates','Own ACTIVE loan'],['02','Backend signs','Unique event + HMAC'],['03','Officer verifies','Signature + event state'],['04','Officer records','Atomic balance update']];
  blocks.forEach((b,i)=>{const x=x0+i*3.02;shape(s,S.roundRect,x,y,w,1.52,i===3?C.paleGold:C.white,C.line);tx(s,b[0],x+0.18,y+0.17,0.44,0.25,{size:10,bold:true,color:C.green});tx(s,b[1],x+0.18,y+0.56,w-0.32,0.37,{size:14,bold:true,color:C.dark});tx(s,b[2],x+0.18,y+1.02,w-0.32,0.34,{size:11,color:C.muted});if(i<3){line(s,x+w+0.08,y+0.77,x+2.88,y+0.77,C.gold,1.7);}});
  shape(s,S.rect,0.95,5.15,11.35,0.78,C.dark,C.dark);
  tx(s,'Simulation event',1.18,5.36,2.4,0.3,{size:14,bold:true,color:C.cream});
  tx(s,'does not change the official balance',3.55,5.35,5.3,0.3,{size:16,bold:true,color:C.white});
  tx(s,'No real money moves in this academic payment simulation.',9.0,5.32,3.0,0.4,{size:11,color:'D7E4DC'});
  note(s,'This flow protects the meaning of a repayment. Borrower initiation creates a simulation event, but it does not post to the loan balance. The backend signature and event verification are part of the academic simulation. An authorized Loan Officer records the official repayment. Duplicate and overpayment protections apply at persistence level.');
}
// 9 — architecture
{
  const s=newSlide(); title(s,'System architecture','PostgreSQL governs loans; the chain records selected events.','The browser calls the Spring Boot API. Backend-managed signing keeps RPC credentials and transaction authority server-side.');
  addImageContain(s,diag('B-high-level-architecture.png'),0.9,2.2,11.6,4.25);
  note(s,'This supplied system architecture diagram describes the intended integration. React/Vite is the client; Spring Boot owns business rules and authorization; PostgreSQL is the operational source of truth; Hardhat local EVM and the event ledger support selected evidence. The browser does not submit contract transactions.');
}
// 10 — data privacy
{
  const s=newSlide(); title(s,'Data boundaries','Personal loan records stay off-chain.','The contract receives the minimum event evidence allowed by the approved design.');
  addImageContain(s,diag('AG-onchain-offchain.png'),0.88,2.15,7.2,4.3);
  tx(s,'Operational database',8.55,2.53,3.55,0.32,{size:17,bold:true,color:C.green});
  ['Borrower identity and contact details','Applications and decision rationale','Repayment details and balance','Loan lifecycle and audit history'].forEach((v,i)=>bullet(s,v,8.58,3.05+i*0.57,3.85,{size:12,h:0.45,dot:C.green}));
  tx(s,'Selected chain evidence',8.55,5.58,3.5,0.32,{size:15,bold:true,color:'936D32'});
  tx(s,'Opaque event identifiers, permitted amount and confirmation evidence. A blockchain record is not proof that cash moved.',8.58,5.98,3.75,0.7,{size:11,color:C.muted,valign:'top'});
  note(s,'Use the source diagram to explain the privacy boundary. Applications, identity and operational balances remain off-chain. Selected financial events can have chain evidence connected with opaque identifiers. A chain event proves a contract transaction was recorded; it does not prove an external payment occurred.');
}
// 11 — outbox
{
  const s=newSlide(); title(s,'Reliable event delivery','Outbox delivery decouples operations from chain outages.','An operational transaction and its outbox row commit together. A worker submits evidence afterward without holding a database transaction open.');
  const items=[['DB COMMIT','Loan / repayment\n+ outbox row'],['CLAIM','Worker leases\npending event'],['SUBMIT','Backend signer sends\ncontract transaction'],['CONFIRM','Configured confirmation\nthreshold reached'],['RECONCILE','Compare local status\nwith actual chain']];
  items.forEach((it,i)=>{const x=0.82+i*2.53;tx(s,it[0],x,2.55,2.15,0.25,{size:10,bold:true,color:i===4?C.gold:C.green,charSpacing:0.7});shape(s,S.roundRect,x,2.98,2.0,1.3,i===4?C.dark:C.white,i===4?C.dark:C.line);tx(s,it[1],x+0.17,3.2,1.68,0.82,{size:13,bold:true,color:i===4?C.white:C.dark,align:'center'});if(i<4)line(s,x+2.02,3.62,x+2.40,3.62,C.gold,1.8);});
  tx(s,'If RPC is unavailable, the business operation remains committed and event delivery remains pending or failed.',0.87,5.02,11.5,0.48,{size:15,bold:true,color:C.dark});
  tx(s,'Stable event IDs + idempotent contract handling + retries + reconciliation address ambiguous submissions.',0.87,5.74,11.5,0.4,{size:13,color:C.muted});
  note(s,'Describe outbox semantics carefully: a business operation and outbox record share one database transaction. The worker claims and processes events independently. RPC outage does not undo a committed operational transaction. Stable identifiers and idempotency mitigate retries, while reconciliation detects differences. Do not say every failure mode is impossible.');
}
// 12 — state semantics
{
  const s=newSlide(); title(s,'Two state machines','Loan state and chain delivery state are separate.','Operational state and blockchain delivery state answer different questions.');
  tx(s,'LOAN LIFECYCLE',0.9,2.48,4.7,0.28,{size:10,bold:true,color:C.green,charSpacing:1.2});
  const loan=['APPROVED','DISBURSED','ACTIVE','COMPLETED'];
  loan.forEach((v,i)=>{const x=0.9+i*2.25;shape(s,S.roundRect,x,3.0,1.82,0.65,i===2?C.green:C.white,i===2?C.green:C.line);tx(s,v,x+0.05,3.16,1.72,0.25,{size:10,bold:true,color:i===2?C.white:C.dark,align:'center'});if(i<3)line(s,x+1.84,3.33,x+2.15,3.33,C.gold,1.5);});
  tx(s,'BLOCKCHAIN DELIVERY',0.9,4.37,4.7,0.28,{size:10,bold:true,color:'8E6A34',charSpacing:1.2});
  const chain=['PENDING','PROCESSING','SUBMITTED','RECORDED / FAILED'];
  chain.forEach((v,i)=>{const x=0.9+i*2.25;shape(s,S.roundRect,x,4.9,1.82,0.65,i===3?C.paleGold:C.white,i===3?C.gold:C.line);tx(s,v,x+0.05,5.06,1.72,0.25,{size:9.5,bold:true,color:C.dark,align:'center'});if(i<3)line(s,x+1.84,5.23,x+2.15,5.23,C.gold,1.5);});
  tx(s,'RECORDED means the configured confirmation condition was met. FAILED means delivery needs attention; the database operation is not silently reversed.',0.92,6.08,11.2,0.58,{size:13,bold:true,color:C.dark});
  note(s,'Use the failure example to explain the split: PostgreSQL is authoritative for loan operations. A failed outbox event is a delivery failure requiring attention, not a rollback of an already committed repayment. RECORDED only follows actual confirmation at the configured threshold.');
}
// 13 — controls
{
  const s=newSlide(); title(s,'Controls and integrity','Security follows the data and the operation.','The system uses server-side authorization, validation, transactions and audit evidence around financial changes.');
  addImageContain(s,diag('AF-rbac.png'),0.8,2.1,5.6,4.25);
  const xs=7.0; const list=[['Role checks','Server-side endpoint authorization and borrower ownership checks'],['Session security','Session cookies with CSRF checks on state-changing requests'],['Financial invariants','Full disbursement, positive amounts, overpayment protection, duplicate controls'],['Audit trail','Record important actors, decisions and lifecycle changes'],['Blockchain signing','Backend-controlled signer; no keys in browser code']];
  list.forEach((it,i)=>{tx(s,it[0],xs,2.22+i*0.78,2.05,0.28,{size:13,bold:true,color:C.green});tx(s,it[1],xs+2.08,2.19+i*0.78,3.2,0.5,{size:11.5,color:C.muted,valign:'top'});if(i<4)line(s,xs,2.64+i*0.78,12.25,2.64+i*0.78,C.line,0.7);});
  note(s,'Explain both the controls and their limits. Sessions and CSRF protect browser requests. Role checks remain at the API. Database transactions and uniqueness/locking controls address concurrency-sensitive rules. The contract signer stays on the backend. This is an academic implementation, not an independent security certification.');
}
// 14 — admin reports
{
  const s=newSlide(); title(s,'Administrative oversight','Aggregate oversight without exposing borrower records.','Administrator reporting focuses on portfolio aggregates, operational states, audit outcomes and delivery status.');
  addImageContain(s,path.join(root,'PesaChain_Figma_Import_SVG','screens','20-admin-dashboard.svg'),0.82,2.2,5.75,3.85);
  tx(s,'What the report can help answer',7.05,2.5,5.1,0.4,{size:19,bold:true,color:C.dark});
  ['How many applications await review?','How many loans are approved, active or completed?','What principal, amount repaid and balance are recorded?','Are recent audit or blockchain-delivery outcomes failing?','How are persisted activity counts changing month to month?'].forEach((v,i)=>bullet(s,v,7.1,3.16+i*0.57,5.2,{size:12.5,h:0.47}));
  tx(s,'No due-date policy exists, so the system does not report on-time or overdue performance.',7.1,6.08,5.05,0.48,{size:11,bold:true,color:C.red});
  note(s,'The Administrator report endpoint returns aggregate data and avoids personal borrower details. It does not imply a schedule, credit score, or delinquency signal. The Administrator does not get Officer servicing rights.');
}
// 15 — comparison
{
  const s=newSlide(); title(s,'Why this approach','A digital ledger can complement finance workflows.','This is a conceptual comparison of workflow characteristics, not a measured market benchmark.');
  const rows=[['Application trail','May be split across tools or paper records','Structured status and role-scoped history'],['Decision accountability','Rationale can be hard to connect to a request','Officer, timestamp and rationale are retained'],['Payment evidence','Operational record and evidence can be conflated','Simulation, official repayment and chain delivery are distinct'],['Privacy boundary','Varies by process','Personal records stay in PostgreSQL; selected event evidence is separate'],['Integration scope','Depends on provider','Local/test academic prototype; no real payment rail']];
  tx(s,'PROCESS DIMENSION',0.84,2.35,2.25,0.26,{size:9,bold:true,color:C.green,charSpacing:1});tx(s,'TRADITIONAL / MANUAL POSSIBILITY',3.1,2.35,4.2,0.26,{size:9,bold:true,color:C.muted,charSpacing:.8});tx(s,'PESACHAIN DESIGN',8.0,2.35,3.6,0.26,{size:9,bold:true,color:C.green,charSpacing:1});
  rows.forEach((r,i)=>{const y=2.82+i*0.68;if(i%2===0)shape(s,S.rect,0.8,y-0.08,11.75,0.58,'EEF1EC','EEF1EC');tx(s,r[0],0.95,y,2.0,0.38,{size:12,bold:true,color:C.dark});tx(s,r[1],3.1,y,4.45,0.42,{size:11.5,color:C.muted});tx(s,r[2],8.0,y,4.2,0.42,{size:11.5,bold:true,color:C.green});});
  tx(s,'PesaChain is not a replacement for a regulated lender, payment provider, or core banking system.',0.88,6.45,11.5,0.3,{size:11,bold:true,color:C.dark});
  note(s,'Avoid claiming that manual financial systems are universally fragmented or inferior. The table compares possible workflow characteristics with what this prototype demonstrates. PesaChain is not a real payment provider or a production core banking system.');
}
// 16 — evidence
{
  const s=newSlide(); title(s,'Verification evidence','Test evidence should be specific about what it proves.','Automated tests support critical business rules; local services, authenticated browser journeys and deployment still need environment-specific checks.');
  const evidence=[['Backend integration','Latest documented run: 47 tests; 0 failures; 0 errors; 1 opt-in Hardhat integration test skipped.'],['Frontend','TypeScript check and production build passed in the latest reported UI slice.'],['Browser','Landing/FAQ browser interaction was previously exercised; no authenticated Officer/Admin session was available for full journey proof.'],['Database','Testcontainers use disposable PostgreSQL; persistent Compose data was preserved.'],['Blockchain','Local RPC and contract delivery require configured node, deployed address, signer and confirmation threshold.']];
  evidence.forEach((it,i)=>{const y=2.48+i*0.78;tx(s,it[0],0.95,y,2.1,0.32,{size:13,bold:true,color:C.green});tx(s,it[1],3.0,y,9.0,0.48,{size:12,color:C.ink,valign:'top'});if(i<4)line(s,0.95,y+0.58,12.3,y+0.58,C.line,0.7);});
  tx(s,'Test counts reflect the project status record and were not rerun during deck creation.',0.95,6.58,11.2,0.28,{size:10,color:C.muted});
  note(s,'State verification accurately. The recorded backend verify run was 47 tests with one opt-in Hardhat integration skipped. The current session reran frontend checks for the code fixes; backend code did not change. Authenticated UI journeys and an end-to-end deployment remain unverified without the required live account and environment.');
}
// 17 — deployment
{
  const s=newSlide(); title(s,'Deployment path','Free-tier deployment still needs deliberate choices.','The repository contains a separate guide for Cloudflare Pages and Vercel options, with provider limits and backend constraints.');
  const steps=[['Frontend','Cloudflare Pages\nor Vercel'],['API','Container host\nwith Java support'],['Database','Managed PostgreSQL\nwith backup plan'],['Blockchain','Test network or\ncontrolled local demo']];
  steps.forEach((it,i)=>{const x=0.9+i*3.05;tx(s,String(i+1).padStart(2,'0'),x,2.78,0.5,0.28,{size:10,bold:true,color:C.green});tx(s,it[0],x,3.2,2.5,0.38,{size:21,bold:true,color:C.dark});line(s,x,3.78,x+2.35,3.78,i===0?C.green:C.gold,2.2);tx(s,it[1],x,4.04,2.6,0.66,{size:14,color:C.muted,valign:'top'});});
  shape(s,S.roundRect,0.9,5.35,11.45,0.83,C.paleGold,C.paleGold);
  tx(s,'Important',1.16,5.6,1.25,0.25,{size:12,bold:true,color:'8E6A34'});
  tx(s,'Local Hardhat addresses are not public deployments. Use same-origin API routing for session cookies and CSRF.',2.43,5.52,9.5,0.46,{size:13,bold:true,color:C.dark});
  tx(s,'See docs/FREE_DEPLOYMENT_GUIDE.md for configuration and limits.',0.95,6.55,11.1,0.3,{size:11,bold:true,color:C.green});
  note(s,'Refer to docs/FREE_DEPLOYMENT_GUIDE.md. Do not promise permanent free hosting. A free static frontend does not eliminate the need for a Java API host, PostgreSQL persistence, secrets, same-origin session/CSRF routing, backups and an appropriate public/test chain setup. A local Hardhat node and address cannot be used from the public internet.');
}
// 18 — likely questions
{
  const s=newSlide(); title(s,'Questions you may be asked','Answer with the system boundary, not unsupported claims.','');
  const qa=[
    ['Does blockchain move money?','No. The prototype simulates payments. Blockchain records selected event evidence, not a money transfer.'],
    ['Why keep PostgreSQL if there is a chain?','PostgreSQL stores private operational records and remains authoritative for loan state.'],
    ['Who approves a loan?','An authorized Loan Officer. The contract does not approve or score applications.'],
    ['What if the chain is down?','A committed operational decision remains in the database; event delivery retries and can require reconciliation.'],
    ['Can the record be called tamper-proof?','Only narrowly: confirmed chain events are difficult to alter under the network’s consensus. The PDF receipt itself is not digitally signed.'],
    ['Can this replace a bank or MFI system today?','No. It is an academic local/test prototype that demonstrates a workflow and integration design.'],
  ];
  qa.forEach((it,i)=>{const col=i%2,row=Math.floor(i/2),x=0.85+col*6.1,y=2.15+row*1.38;tx(s,it[0],x,y,5.55,0.33,{size:13,bold:true,color:C.green});tx(s,it[1],x,y+0.42,5.55,0.64,{size:11.5,color:C.ink,valign:'top'});if(row<2)line(s,x,y+1.18,x+5.55,y+1.18,C.line,0.8);});
  note(s,'Use the answers to keep claims honest. For “tamper-proof,” explain that a block confirmation makes a recorded event harder to alter under the selected network assumptions. The project’s PDF is not digitally signed. The system is a prototype and cannot replace financial institutions or regulated payment providers.');
}
// 19 — future
{
  const s=newSlide(); title(s,'What can come next','Future work starts with policy and operational needs.','These are possible directions, not implemented or approved product behavior.');
  const choices=[
    ['Repayment schedule','Agree installment frequency, due dates, rounding and exception policy before implementing reminders.'],
    ['External payment integration','Select a regulated provider and define reconciliation, reversals, fees and idempotency before connecting money movement.'],
    ['Public test deployment','Threat-model signer operations, key custody, privacy, RPC reliability and contract upgrade policy.'],
    ['Operational evaluation','Run usability studies with officers and borrowers; measure task success before claiming business impact.'],
  ];
  choices.forEach((it,i)=>{const x=0.9+(i%2)*6.0,y=2.45+Math.floor(i/2)*1.78;tx(s,String(i+1).padStart(2,'0'),x,y,0.48,0.27,{size:10,bold:true,color:C.gold});tx(s,it[0],x+0.62,y-0.03,4.9,0.38,{size:17,bold:true,color:C.dark});tx(s,it[1],x+0.62,y+0.48,4.95,0.8,{size:12,color:C.muted,valign:'top'});});
  shape(s,S.rect,0.9,6.25,11.6,0.53,C.green,C.green);tx(s,'Keep each future change tied to a real user need and a verified control.',1.12,6.36,11.1,0.28,{size:13,bold:true,color:C.white,align:'center'});
  note(s,'These future directions require approval and policy. In particular, due dates and reminders change borrower obligations and need a schedule rule. External payment handling needs a provider plus reconciliation and reversal design. Public deployment requires stronger signer/key operations. Usability studies are needed before making measured impact claims.');
}
// 20 — close
{
  const s=pptx.addSlide(); s.background={color:C.dark};
  s.addShape(S.rect,{x:0,y:0,w:13.333,h:7.5,fill:{color:C.dark},line:{color:C.dark}});
  s.addShape(S.rect,{x:0,y:0,w:0.18,h:7.5,fill:{color:C.gold},line:{color:C.gold}});
  s.addImage({path:logo,x:0.95,y:1.0,w:0.8,h:0.8});
  tx(s,'PesaChain',1.95,1.18,5.5,0.45,{size:23,bold:true,color:C.white});
  tx(s,'A clear process.\nA record for each step.',0.95,2.35,8.0,1.45,{size:35,bold:true,color:C.white});
  tx(s,'Loan management and selected-event transparency',0.97,4.2,8.7,0.4,{size:18,color:'D7E4DC'});
  tx(s,'Thank you',0.97,6.25,3.0,0.42,{size:16,bold:true,color:C.cream});
  tx(s,'PesaChain',9.9,6.62,2.2,0.3,{size:12,bold:true,color:C.gold,align:'right'});
  note(s,'Close by restating the core contribution: a structured loan workflow and separate evidence for selected events. Invite questions about business rules, data boundaries, role security, the outbox, and the limits of the academic prototype.');
}

// Ask PowerPoint to use restrained fade transitions between slides.
pptx.writeFile({ fileName: outPath, compression: true }).then(async () => {
  // Add a short fade transition to each slide while preserving all editable slide content.
  const { execFileSync } = await import('node:child_process');
  const py = String.raw`
import os,sys,zipfile,re
p=sys.argv[1]; tmp=p+'.tmp'
with zipfile.ZipFile(p,'r') as zin, zipfile.ZipFile(tmp,'w',zipfile.ZIP_DEFLATED) as zout:
  for item in zin.infolist():
    data=zin.read(item.filename)
    if item.filename=='[Content_Types].xml':
      text=data.decode('utf-8')
      text=re.sub(r'<Override PartName="/ppt/slideMasters/slideMaster(?!1\.xml)[^"]*"[^>]*/>','',text)
      data=text.encode('utf-8')
    if item.filename.startswith('ppt/slides/slide') and item.filename.endswith('.xml'):
      text=data.decode('utf-8')
      if '<p:transition' not in text:
        text=text.replace('</p:cSld>','</p:cSld><p:transition spd="med"><p:fade/></p:transition>',1)
      data=text.encode('utf-8')
    zout.writestr(item,data)
os.replace(tmp,p)
`;
  execFileSync('python', ['-c', py, outPath]);
  console.log(JSON.stringify({ output: outPath, slides: pptx._slides.length }));
}).catch((e) => { console.error(e); process.exitCode = 1; });
