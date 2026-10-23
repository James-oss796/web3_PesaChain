package com.pesachain.blockchain;

import com.pesachain.auth.UserPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BlockchainMonitoringController {
    private final BlockchainMonitoringService service;
    public BlockchainMonitoringController(BlockchainMonitoringService service) { this.service = service; }

    @GetMapping("/api/v1/admin/blockchain-events")
    BlockchainMonitoringService.StatusPage administratorEvents(Authentication auth,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.administratorEvents(principal(auth), status, page, size);
    }

    @GetMapping("/api/v1/officer/blockchain-events")
    BlockchainMonitoringService.StatusPage officerEvents(Authentication auth,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.officerEvents(principal(auth), status, page, size);
    }

    @GetMapping("/api/v1/loans/{loanId}/blockchain-events")
    List<BlockchainMonitoringService.EventStatus> borrowerLoanEvents(Authentication auth, @PathVariable UUID loanId) {
        return service.borrowerLoanEvents(principal(auth), loanId);
    }

    @GetMapping("/api/v1/admin/blockchain-reconciliation/findings")
    List<BlockchainOutboxRepository.FindingView> findings(Authentication auth, @RequestParam(defaultValue = "50") int limit) {
        return service.findings(principal(auth), limit);
    }

    @PostMapping("/api/v1/admin/blockchain-reconciliation/run")
    BlockchainReconciliationService.ReconciliationResult reconcile(Authentication auth) {
        return service.reconcile(principal(auth));
    }

    private static UserPrincipal principal(Authentication auth) { return (UserPrincipal) auth.getPrincipal(); }
}
