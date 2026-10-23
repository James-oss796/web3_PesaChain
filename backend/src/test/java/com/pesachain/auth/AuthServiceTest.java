package com.pesachain.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private AccountRepository accounts;
    @Mock private JdbcTemplate jdbc;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(accounts, new BCryptPasswordEncoder(4), jdbc);
    }

    @Test
    void registersBorrowerWithNormalizedEmailAndHashedPassword() {
        when(accounts.emailExists("borrower@example.com")).thenReturn(false);
        when(accounts.createBorrower(anyString(), anyString(), anyString(), anyString())).thenReturn(UUID.randomUUID());

        service.register(" Borrower@Example.com ", "long-secure-pass", "  Borrower Name ", "+254 712 345 678");

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(accounts).createBorrower(
                org.mockito.ArgumentMatchers.eq("borrower@example.com"),
                captor.capture(),
                org.mockito.ArgumentMatchers.eq("Borrower Name"),
                org.mockito.ArgumentMatchers.eq("+254 712 345 678"));
        org.junit.jupiter.api.Assertions.assertNotEquals("long-secure-pass", captor.getValue());
        org.junit.jupiter.api.Assertions.assertTrue(new BCryptPasswordEncoder(4).matches("long-secure-pass", captor.getValue()));
    }

    @Test
    void rejectsDuplicateEmailBeforeCreatingAccount() {
        when(accounts.emailExists("borrower@example.com")).thenReturn(true);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.register("borrower@example.com", "long-secure-pass", "Borrower Name", "+254712345678"));

        assertEquals(HttpStatus.CONFLICT, exception.status());
        verify(accounts, never()).createBorrower(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void rejectsPasswordExceedingBcryptUtf8Limit() {
        String tooLong = "é".repeat(37);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.register("borrower@example.com", tooLong, "Borrower Name", "+254712345678"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.status());
        verify(accounts, never()).emailExists(anyString());
    }
}
