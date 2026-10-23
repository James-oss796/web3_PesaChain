package com.pesachain.repayments;

import com.pesachain.auth.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RepaymentController {
    private final RepaymentService service;
    public RepaymentController(RepaymentService service) { this.service=service; }

    @PostMapping("/api/v1/loans/{loanId}/simulated-repayments")
    @ResponseStatus(HttpStatus.CREATED)
    RepaymentService.EventResponse initiate(Authentication auth,@PathVariable UUID loanId,
            @RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody AmountRequest request) {
        return RepaymentService.EventResponse.from(service.initiate((UserPrincipal)auth.getPrincipal(),loanId,request.amount(),key));
    }
    @GetMapping("/api/v1/loans/{loanId}/simulated-repayments")
    List<RepaymentService.EventResponse> borrowerEvents(Authentication auth,@PathVariable UUID loanId) {
        return service.borrowerEvents((UserPrincipal)auth.getPrincipal(),loanId);
    }
    @GetMapping("/api/v1/loans/{loanId}/repayments")
    List<RepaymentRepository.Repayment> repayments(Authentication auth,@PathVariable UUID loanId) {
        return service.borrowerRepayments((UserPrincipal)auth.getPrincipal(),loanId);
    }
    @GetMapping("/api/v1/officer/repayment-events")
    List<RepaymentService.EventResponse> queue(Authentication auth,@RequestParam(defaultValue="PENDING") String status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.officerQueue((UserPrincipal)auth.getPrincipal(),page,size,status);
    }
    @GetMapping("/api/v1/officer/repayment-events/{eventId}")
    RepaymentService.EventResponse event(Authentication auth,@PathVariable UUID eventId) {
        return service.officerEvent((UserPrincipal)auth.getPrincipal(),eventId);
    }
    @PostMapping("/api/v1/officer/repayment-events/{eventId}/verify")
    RepaymentService.EventResponse verify(Authentication auth,@PathVariable UUID eventId) {
        return service.verify((UserPrincipal)auth.getPrincipal(),eventId);
    }
    @PostMapping("/api/v1/officer/repayment-events/{eventId}/record")
    RepaymentRepository.Repayment record(Authentication auth,@PathVariable UUID eventId) {
        return service.record((UserPrincipal)auth.getPrincipal(),eventId);
    }
    record AmountRequest(@NotNull @DecimalMin(value="0.01") @Digits(integer=28,fraction=2) BigDecimal amount) {}
}
