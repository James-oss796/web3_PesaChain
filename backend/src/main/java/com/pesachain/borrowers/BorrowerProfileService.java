package com.pesachain.borrowers;

import com.pesachain.auth.AccountRepository;
import com.pesachain.auth.ApiException;
import com.pesachain.auth.AuthService;
import com.pesachain.auth.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BorrowerProfileService {
    private final AccountRepository accounts;
    private final AuthService authService;

    public BorrowerProfileService(AccountRepository accounts, AuthService authService) {
        this.accounts = accounts;
        this.authService = authService;
    }

    public ProfileResponse get(UserPrincipal principal) {
        UserPrincipal current = authService.getAccount(principal.id());
        return response(current);
    }

    @Transactional
    public ProfileResponse update(UserPrincipal principal, String fullName, String phone) {
        String normalizedName = fullName.trim();
        if (normalizedName.length() < 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Name must contain at least two characters.");
        }
        accounts.updateBorrowerProfile(principal.id(), normalizedName, phone.trim());
        return response(authService.getAccount(principal.id()));
    }

    private ProfileResponse response(UserPrincipal user) {
        if (!"BORROWER".equals(user.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Borrower profile access is required.");
        }
        return new ProfileResponse(user.id().toString(), user.fullName(), user.email(), user.phone());
    }

    public record ProfileResponse(String userId, String name, String email, String phone) {}
}
