import { createPublicClient, http } from "viem";
import { defineChain } from "viem";

const localChain = defineChain({
  id: 31337,
  name: "Hardhat Local",
  nativeCurrency: {
    name: "Ether",
    symbol: "ETH",
    decimals: 18
  },
  rpcUrls: {
    default: {
      http: ["http://127.0.0.1:8545"]
    }
  }
});

const client = createPublicClient({
  chain: localChain,
  transport: http()
});

const address = "0x5fbdb2315678afecb367f032d93f642f64180aa3";

console.log("Chain ID:", await client.getChainId());

const code = await client.getCode({ address });

console.log("Contract address:", address);
console.log("Contract deployed:", Boolean(code && code !== "0x"));
console.log("Bytecode length:", code?.length ?? 0);
