import { createPublicClient, http, defineChain, getAddress } from "viem";
import artifact from "../artifacts/contracts/PesaChainEventLedger.sol/PesaChainEventLedger.json" with { type: "json" };

const chain = defineChain({
  id: 31337,
  name: "Hardhat Local",
  nativeCurrency: { name: "Ether", symbol: "ETH", decimals: 18 },
  rpcUrls: { default: { http: ["http://127.0.0.1:8545"] } }
});

const client = createPublicClient({ chain, transport: http() });
const address = getAddress("0x5fbdb2315678afecb367f032d93f642f64180aa3");

const ownerResult = await client.readContract({
  address,
  abi: artifact.abi,
  functionName: "owner"
});

const knownAccount = getAddress("0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266");

const recorderResult = await client.readContract({
  address,
  abi: artifact.abi,
  functionName: "isRecorder",
  args: [knownAccount]
});

console.log("Contract owner:", ownerResult);
console.log("Known Hardhat account authorized:", recorderResult);

