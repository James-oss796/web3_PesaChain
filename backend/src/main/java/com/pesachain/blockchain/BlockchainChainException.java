package com.pesachain.blockchain;

public class BlockchainChainException extends RuntimeException {
    private final String code;
    private final boolean retryable;

    public BlockchainChainException(String code, boolean retryable) {
        super(code);
        this.code = code;
        this.retryable = retryable;
    }

    public BlockchainChainException(String code, boolean retryable, Throwable cause) {
        super(code, cause);
        this.code = code;
        this.retryable = retryable;
    }

    public String code() { return code; }
    public boolean retryable() { return retryable; }
}
