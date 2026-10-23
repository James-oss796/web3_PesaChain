package com.pesachain.loans;

import com.pesachain.auth.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoanController {
    private final LoanServicingService loans;
    public LoanController(LoanServicingService loans) { this.loans=loans; }

    @GetMapping("/api/v1/loans/mine")
    LoanServicingService.LoanPage mine(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return loans.listMine((UserPrincipal)auth.getPrincipal(),page,size);
    }
    @GetMapping("/api/v1/loans")
    LoanServicingService.LoanPage officerLoans(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return loans.listOfficerLoans((UserPrincipal)auth.getPrincipal(),page,size);
    }
    @GetMapping("/api/v1/loans/{id}")
    LoanRepository.LoanView mineById(Authentication auth,@PathVariable UUID id) { return loans.getMine((UserPrincipal)auth.getPrincipal(),id); }

    @PostMapping("/api/v1/officer/loans/{id}/disbursement")
    @ResponseStatus(HttpStatus.OK)
    LoanRepository.LoanView disburse(Authentication auth,@PathVariable UUID id,@Valid @RequestBody DisbursementRequest request) {
        return loans.disburse((UserPrincipal)auth.getPrincipal(),id,request.amount());
    }
    record DisbursementRequest(@NotNull @DecimalMin(value="0.01") @Digits(integer=28,fraction=2) BigDecimal amount) {}
}
