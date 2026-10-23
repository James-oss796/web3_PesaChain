package com.pesachain.blockchain;

import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.UUID;
import org.web3j.crypto.Hash;

final class BlockchainIds {
    private BlockchainIds() {}

    static String toBytes32(UUID id) {
        ByteBuffer bytes = ByteBuffer.allocate(16);
        bytes.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
        return "0x" + HexFormat.of().formatHex(Hash.sha3(bytes.array()));
    }

    static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length != 32) throw new IllegalArgumentException("Expected a 32-byte aggregate key.");
        return "0x" + HexFormat.of().formatHex(bytes);
    }
}
