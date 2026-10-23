import hardhatToolboxViemPlugin from "@nomicfoundation/hardhat-toolbox-viem";
import { defineConfig } from "hardhat/config";

export default defineConfig({
  plugins: [hardhatToolboxViemPlugin],
  solidity: "0.8.28",
  networks: {
    localhost: {
      type: "http",
      chainType: "l1",
      url: process.env.PESACHAIN_RPC_URL ?? "http://127.0.0.1:8545",
      chainId: 31337,
      accounts: process.env.PESACHAIN_BLOCKCHAIN_SIGNER_PRIVATE_KEY
        ? [process.env.PESACHAIN_BLOCKCHAIN_SIGNER_PRIVATE_KEY]
        : [],
    },
  },
});
