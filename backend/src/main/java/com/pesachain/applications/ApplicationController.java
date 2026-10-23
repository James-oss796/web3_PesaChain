package com.pesachain.applications;

import com.pesachain.auth.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {
    private final ApplicationService applications;

    public ApplicationController(ApplicationService applications) {
        this.applications = applications;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApplicationService.ApplicationResponse submit(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SubmitApplicationRequest request) {
        UUID key;
        try {
            key = UUID.fromString(idempotencyKey == null ? "" : idempotencyKey);
            if (!key.toString().equalsIgnoreCase(idempotencyKey)) {
                throw new IllegalArgumentException("Non-canonical UUID");
            }
        } catch (IllegalArgumentException invalidKey) {
            throw new com.pesachain.auth.ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "A valid Idempotency-Key UUID is required to submit an application safely.");
        }
        return applications.submit(
                (UserPrincipal) authentication.getPrincipal(), request.requestedAmount(), request.purpose(), request.requestedTermMonths(), key);
    }

    @GetMapping("/mine")
    ApplicationService.ApplicationPage listMine(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return applications.listMine((UserPrincipal) authentication.getPrincipal(), page, size);
    }

    @GetMapping("/{applicationId}")
    ApplicationService.ApplicationResponse getMine(
            Authentication authentication,
            @PathVariable UUID applicationId) {
        return applications.getMine((UserPrincipal) authentication.getPrincipal(), applicationId);
    }

    record SubmitApplicationRequest(
            @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 28, fraction = 2)
                    BigDecimal requestedAmount,
            @NotBlank @Size(max = 1000) String purpose,
            @NotNull @Positive Integer requestedTermMonths) {}
}
