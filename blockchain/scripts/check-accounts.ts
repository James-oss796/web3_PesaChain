import { createPublicClient, http, defineChain } from "viem";

const chain = defineChain({
  id: 31337,
  name: "Hardhat Local",
  nativeCurrency: { name: "Ether", symbol: "ETH", decimals: 18 },
  rpcUrls: { default: { http: ["http://127.0.0.1:8545"] } }
});

const client = createPublicClient({ chain, transport: http() });
const accounts = await client.request({ method: "eth_accounts" });

console.log("Accounts exposed by local node:");
for (const account of accounts) console.log(account);
