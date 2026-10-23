import path from 'node:path';
import { pathToFileURL } from 'node:url';
const root = process.cwd();
const skill = 'C:/Users/Admin/.codex/plugins/cache/openai-primary-runtime/presentations/26.1020.11127/skills/presentations';
const { finalizePresentation } = await import(pathToFileURL(path.join(skill, 'container_tools/artifact_tool_utils.mjs')).href);
const result = await finalizePresentation({
  workspaceDir: root,
  candidatePath: path.join(root, 'presentation_output/PesaChain_Project_Presentation.pptx'),
  finalPath: path.join(root, 'presentation_output/PesaChain_Project_Presentation_Final.pptx'),
  pythonExecutable: 'C:/Users/Admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe',
  integrityValidatorPath: path.join(skill, 'container_tools/inspect_presentation_package_integrity.py'),
  layoutValidatorPath: path.join(skill, 'container_tools/inspect_presentation_layout_geometry.py'),
  layoutArgs: ['--expected-slide-size-emu', '12192000,6858000', '--validate-heading-fit'],
  explicitTotalSlideCount: 20,
  requiredNativeTableOwnerSlides: [],
  requiredNativeChartOwnerSlides: [],
  fontPolicy: { basis: 'design', families: ['Aptos', 'Aptos Display'] },
  verifyArtifactToolImport: true,
  receiptPath: path.join(root, '.codex-pesachain-slide-render/finalization-validation.json'),
});
console.log(JSON.stringify(result));
