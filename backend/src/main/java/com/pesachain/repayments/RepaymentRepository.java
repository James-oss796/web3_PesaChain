package com.pesachain.repayments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepaymentRepository {
    Optional<Event> findEvent(UUID id);
    Optional<Event> findBorrowerEvent(UUID borrowerId, UUID id);
    Optional<Event> findByIdempotencyKey(UUID borrowerId, UUID key);
    List<Event> listBorrowerEvents(UUID borrowerId, UUID loanId);
    List<Repayment> listBorrowerRepayments(UUID borrowerId, UUID loanId);
    List<Event> listOfficerEvents(String status, int limit, int offset);
    Optional<Event> lockEvent(UUID id);
    Optional<Loan> findLoan(UUID id);
    Optional<Loan> lockLoan(UUID id);
    Optional<Event> createEvent(UUID id, UUID borrowerId, UUID loanId, BigDecimal amount, UUID key, String signature, Instant createdAt);
    boolean verify(UUID eventId, UUID officerId);
    Repayment record(UUID eventId, UUID officerId, BigDecimal amount, BigDecimal balance, boolean completed);
    void auditEvent(UUID actorId, String actorRole, String action, UUID eventId);

    record Event(UUID id, UUID loanId, UUID borrowerId, BigDecimal amount, UUID idempotencyKey,
            String signature, String status, Instant createdAt, Instant sourceVerifiedAt,
            UUID verifiedBy, Instant verifiedAt, Instant recordedAt) {}
    record Loan(UUID id, UUID borrowerId, BigDecimal outstandingBalance, String status) {}
    record Repayment(UUID id, UUID eventId, UUID loanId, BigDecimal amountApplied,
            BigDecimal resultingBalance, UUID recordedBy, Instant recordedAt) {}
}
