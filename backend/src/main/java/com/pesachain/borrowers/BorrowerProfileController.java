package com.pesachain.borrowers;

import com.pesachain.auth.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/borrowers/me/profile")
public class BorrowerProfileController {
    private final BorrowerProfileService profiles;

    public BorrowerProfileController(BorrowerProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    BorrowerProfileService.ProfileResponse get(Authentication authentication) {
        return profiles.get((UserPrincipal) authentication.getPrincipal());
    }

    @PatchMapping
    BorrowerProfileService.ProfileResponse update(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        return profiles.update((UserPrincipal) authentication.getPrincipal(), request.fullName(), request.phone());
    }

    record UpdateProfileRequest(
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @NotBlank @Pattern(regexp = "\\+?[0-9][0-9 ()-]{5,30}") String phone) {}
}
