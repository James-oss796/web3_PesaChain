package com.pesachain.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(
            AuthService authService,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getToken(), csrfToken.getHeaderName());
    }

    @PostMapping("/register")
    ResponseEntity<AccountResponse> register(
            @Valid @RequestBody RegistrationRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        authService.register(request.email(), request.password(), request.fullName(), request.phone());
        Authentication authentication = authenticate(request.email(), request.password());
        saveSession(authentication, servletRequest, servletResponse);
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from((UserPrincipal) authentication.getPrincipal()));
    }

    @PostMapping("/login")
    AccountResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        Authentication authentication = authenticate(request.email(), request.password());
        saveSession(authentication, servletRequest, servletResponse);
        return AccountResponse.from((UserPrincipal) authentication.getPrincipal());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        securityContextRepository.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    AccountResponse me(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return AccountResponse.from(authService.getAccount(principal.id()));
    }

    @PostMapping("/change-password")
    AccountResponse changePassword(@Valid @RequestBody PasswordChangeRequest request, Authentication authentication,
                                   HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        UserPrincipal changed=authService.changePassword(((UserPrincipal)authentication.getPrincipal()).id(),request.currentPassword(),request.newPassword());
        Authentication refreshed=new UsernamePasswordAuthenticationToken(changed,changed.passwordHash(),changed.getAuthorities());
        saveSession(refreshed,servletRequest,servletResponse);
        return AccountResponse.from(changed);
    }

    private Authentication authenticate(String email, String password) {
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Email or password is incorrect.");
        }
        try {
            return authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        } catch (AuthenticationException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Email or password is incorrect.");
        }
    }

    private void saveSession(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    record RegistrationRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Pattern(regexp = "\\+?[0-9][0-9 ()-]{5,30}") String phone,
            @NotBlank @Size(min = 12, max = 72) String password) {}

    record LoginRequest(@NotBlank @Email @Size(max = 320) String email, @NotBlank @Size(max = 72) String password) {}

    record PasswordChangeRequest(@NotBlank String currentPassword, @NotBlank @Size(min=12,max=72) String newPassword) {}

    record AccountResponse(String id, String name, String email, String phone, String role, boolean passwordChangeRequired) {
        static AccountResponse from(UserPrincipal user) {
            return new AccountResponse(user.id().toString(), user.fullName(), user.email(), user.phone(), user.role(), user.passwordChangeRequired());
        }
    }

    record CsrfResponse(String token, String headerName) {}
}
