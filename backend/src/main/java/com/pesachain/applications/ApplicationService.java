package com.pesachain.applications;

import com.pesachain.auth.ApiException;
import com.pesachain.auth.UserPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {
    private final ApplicationRepository applications;

    public ApplicationService(ApplicationRepository applications) {
        this.applications = applications;
    }

    @Transactional
    public ApplicationResponse submit(UserPrincipal borrower, BigDecimal amount, String purpose, Integer requestedTermMonths, UUID idempotencyKey) {
        requireBorrower(borrower);
        if (requestedTermMonths == null || requestedTermMonths < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Requested loan term must be a positive number of months.");
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.precision() - amount.scale() > 28) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Enter a positive KES amount with up to two decimal places.");
        }
        BigDecimal normalizedAmount;
        try {
            normalizedAmount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException invalidScale) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Enter a positive KES amount with up to two decimal places.");
        }
        if (purpose == null || purpose.isBlank() || purpose.trim().length() > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Provide a purpose of up to 1000 characters.");
        }
        if (idempotencyKey == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "A valid Idempotency-Key is required.");
        }
        var application = applications.create(borrower.id(), normalizedAmount, purpose.trim(), requestedTermMonths, idempotencyKey);
        if (application.requestedAmount().compareTo(normalizedAmount) != 0
                || application.requestedTermMonths() != requestedTermMonths
                || !application.purpose().equals(purpose.trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "This submission key was already used for different application details. Start a new submission.");
        }
        return ApplicationResponse.from(application);
    }

    @Transactional(readOnly = true)
    public ApplicationPage listMine(UserPrincipal borrower, int page, int size) {
        requireBorrower(borrower);
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Page must be nonnegative and size must be between 1 and 100.");
        }
        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Page is out of range.");
        }
        List<ApplicationResponse> items = applications.findMine(borrower.id(), size, (int) offset)
                .stream()
                .map(ApplicationResponse::from)
                .toList();
        return new ApplicationPage(items, page, size, applications.countMine(borrower.id()));
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getMine(UserPrincipal borrower, UUID applicationId) {
        requireBorrower(borrower);
        return applications.findMineById(borrower.id(), applicationId)
                .map(ApplicationResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Application was not found."));
    }

    private void requireBorrower(UserPrincipal user) {
        if (!"BORROWER".equals(user.role()) || !user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Borrower access is required.");
        }
    }

    public record ApplicationResponse(
            UUID id,
            BigDecimal requestedAmount,
            String currency,
            String purpose,
            String status,
            java.time.Instant submittedAt,
            java.time.Instant decisionAt,
            String rejectionReason,
            Integer requestedTermMonths,
            BigDecimal approvedPrincipal,
            Integer approvedTermMonths,
            BigDecimal approvedFlatRatePercent,
            BigDecimal calculatedInterest,
            BigDecimal totalRepayable) {
        static ApplicationResponse from(ApplicationRepository.ApplicationView application) {
            return new ApplicationResponse(
                    application.id(),
                    application.requestedAmount(),
                    "KES",
                    application.purpose(),
                    application.status(),
                    application.submittedAt(),
                    application.decisionAt(),
                    application.rejectionReason(),
                    application.requestedTermMonths(),
                    application.approvedPrincipal(),
                    application.approvedTermMonths(),
                    application.approvedFlatRatePercent(),
                    application.calculatedInterest(),
                    application.totalRepayable());
        }
    }

    public record ApplicationPage(List<ApplicationResponse> items, int page, int size, long totalItems) {}
}
