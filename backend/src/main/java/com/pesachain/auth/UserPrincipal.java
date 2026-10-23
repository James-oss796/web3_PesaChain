package com.pesachain.auth;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record UserPrincipal(
        UUID id,
        String email,
        String passwordHash,
        String role,
        String status,
        String fullName,
        String phone,
        boolean passwordChangeRequired) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (passwordChangeRequired) {
            return List.of(new SimpleGrantedAuthority("ROLE_PASSWORD_CHANGE_REQUIRED"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equals(status);
    }

    @Override
    public boolean isAccountNonLocked() {
        return isEnabled();
    }
}
