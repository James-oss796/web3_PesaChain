package com.pesachain.applications;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository {
    ApplicationView create(UUID borrowerId, BigDecimal amount, String purpose, int requestedTermMonths, UUID idempotencyKey);

    List<ApplicationView> findMine(UUID borrowerId, int limit, int offset);

    long countMine(UUID borrowerId);

    Optional<ApplicationView> findMineById(UUID borrowerId, UUID applicationId);

    java.util.List<OfficerApplicationView> findPendingForOfficer(int limit, int offset, String borrowerNamePrefix);

    long countPendingForOfficer(String borrowerNamePrefix);

    Optional<OfficerApplicationView> findForOfficer(UUID applicationId);

    boolean recordDecision(UUID applicationId, UUID officerId, boolean approved, String rationale,
            String rejectionReason, BigDecimal principal, int termMonths, BigDecimal flatRate,
            BigDecimal interest, BigDecimal totalRepayable);

    record ApplicationView(
            UUID id,
            BigDecimal requestedAmount,
            String purpose,
            String status,
            Instant submittedAt,
            Instant decisionAt,
            String rejectionReason,
            Integer requestedTermMonths,
            BigDecimal approvedPrincipal,
            Integer approvedTermMonths,
            BigDecimal approvedFlatRatePercent,
            BigDecimal calculatedInterest,
            BigDecimal totalRepayable) {}

    record OfficerApplicationView(ApplicationView application, UUID borrowerId, String borrowerName,
                                  String decisionRationale) {}
}
