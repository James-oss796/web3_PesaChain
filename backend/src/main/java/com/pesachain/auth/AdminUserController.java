package com.pesachain.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AdminUserService users;
    public AdminUserController(AdminUserService users) { this.users = users; }

    @GetMapping List<AdminUserService.StaffUser> list() { return users.list(); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    AdminUserService.StaffUser create(@Valid @RequestBody CreateRequest request, Authentication auth) {
        return users.create((UserPrincipal) auth.getPrincipal(), request.email(), request.password(), request.role());
    }

    @PatchMapping("/{id}")
    AdminUserService.StaffUser update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest request, Authentication auth) {
        return users.update((UserPrincipal) auth.getPrincipal(), id, request.role(), request.status());
    }

    record CreateRequest(@NotBlank @Email @Size(max=320) String email,
                         @NotBlank @Size(min=12,max=72) String password,
                         @NotBlank @Pattern(regexp="ADMINISTRATOR|LOAN_OFFICER") String role) {}
    record UpdateRequest(@Pattern(regexp="ADMINISTRATOR|LOAN_OFFICER") String role,
                         @Pattern(regexp="ACTIVE|SUSPENDED") String status) {}
}
