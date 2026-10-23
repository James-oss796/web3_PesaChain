package com.pesachain.loans;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanRepository {
    Optional<LoanView> findById(UUID loanId);
    Optional<LoanView> findMineById(UUID borrowerId,UUID loanId);
    List<LoanView> findMine(UUID borrowerId,int limit,int offset);
    long countMine(UUID borrowerId);
    List<LoanView> findAll(int limit,int offset);
    long countAll();
    Optional<LockedLoan> lockById(UUID loanId);
    boolean disburse(UUID loanId,UUID officerId,BigDecimal amount);

    record LoanView(UUID id,UUID applicationId,UUID borrowerId,BigDecimal principal,BigDecimal flatRatePercent,
            int termMonths,BigDecimal interestAmount,BigDecimal totalRepayable,BigDecimal outstandingBalance,
            String status,Instant approvedAt,Instant disbursedAt,Instant activatedAt,List<StatusEntry> history) {}
    record StatusEntry(String fromStatus,String toStatus,UUID actorId,Instant changedAt) {}
    record LockedLoan(UUID id,BigDecimal principal,String status) {}
}
