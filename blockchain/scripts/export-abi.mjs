import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const artifactPath = resolve(root, "artifacts/contracts/PesaChainEventLedger.sol/PesaChainEventLedger.json");
const artifact = JSON.parse(await readFile(artifactPath, "utf8"));
const target = resolve(root, "../backend/src/main/resources/contracts/PesaChainEventLedger.json");
await mkdir(dirname(target), { recursive: true });
await writeFile(target, `${JSON.stringify({ contractName: artifact.contractName, abi: artifact.abi }, null, 2)}\n`);
console.log(`Exported ${artifact.abi.length} ABI entries to backend resources.`);
