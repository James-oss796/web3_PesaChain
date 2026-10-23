package com.pesachain.applications;

import com.pesachain.auth.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/v1/officer/applications")
public class OfficerApplicationController {
    private final LoanDecisionService decisions;
    public OfficerApplicationController(LoanDecisionService decisions) { this.decisions=decisions; }

    @GetMapping
    LoanDecisionService.OfficerApplicationPage pending(Authentication authentication,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String q) {
        return decisions.listPending((UserPrincipal)authentication.getPrincipal(),page,size,q);
    }

    @GetMapping("/{id}")
    LoanDecisionService.OfficerApplication get(Authentication authentication,@PathVariable UUID id) {
        return decisions.get((UserPrincipal)authentication.getPrincipal(),id);
    }

    @PostMapping("/{id}/decision")
    @ResponseStatus(HttpStatus.OK)
    LoanDecisionService.OfficerApplication decide(Authentication authentication,@PathVariable UUID id,
            @Valid @RequestBody DecisionRequest request) {
        return decisions.decide((UserPrincipal)authentication.getPrincipal(),id,new LoanDecisionService.DecisionCommand(
                request.decision(),request.approvedPrincipal(),request.approvedTermMonths(),request.flatRatePercent(),
                request.rationale(),request.rejectionReason()));
    }

    record DecisionRequest(@NotBlank @Pattern(regexp="APPROVE|REJECT") String decision,
            @DecimalMin(value="0.01") @Digits(integer=28,fraction=2) BigDecimal approvedPrincipal,
            @jakarta.validation.constraints.Positive Integer approvedTermMonths,
            @DecimalMin(value="0.0") @Digits(integer=20,fraction=10) BigDecimal flatRatePercent,
            @NotBlank @Size(max=2000) String rationale,
            @Size(max=1000) String rejectionReason) {}
}
