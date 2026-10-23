import { network } from "hardhat";

const { viem } = await network.create();
const [deployer] = await viem.getWalletClients();
if (!deployer) throw new Error("Configure PESACHAIN_BLOCKCHAIN_SIGNER_PRIVATE_KEY for local deployment.");

const ledger = await viem.deployContract("PesaChainEventLedger", [deployer.account.address]);
const chainId = await (await viem.getPublicClient()).getChainId();
console.log(`PesaChainEventLedger deployed at ${ledger.address} on chain ${chainId}.`);
console.log(`Owner and initial backend recorder: ${deployer.account.address}.`);
