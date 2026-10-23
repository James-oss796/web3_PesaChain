package com.pesachain.applications;

import com.pesachain.auth.UserPrincipal;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/officer/borrowers")
public class OfficerBorrowerHistoryController {
    private final OfficerBorrowerHistoryService history;

    public OfficerBorrowerHistoryController(OfficerBorrowerHistoryService history) { this.history = history; }

    @GetMapping("/{borrowerId}/history")
    OfficerBorrowerHistoryService.BorrowerHistory history(Authentication authentication, @PathVariable UUID borrowerId) {
        return history.history((UserPrincipal) authentication.getPrincipal(), borrowerId);
    }
}
