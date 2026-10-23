package com.pesachain.borrowers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pesachain.auth.AccountRepository;
import com.pesachain.auth.ApiException;
import com.pesachain.auth.AuthService;
import com.pesachain.auth.UserPrincipal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class BorrowerProfileServiceTest {
    @Mock
    private AccountRepository accounts;

    @Mock
    private AuthService authService;

    private BorrowerProfileService service;
    private UUID borrowerId;
    private UserPrincipal borrower;

    @BeforeEach
    void setUp() {
        service = new BorrowerProfileService(accounts, authService);
        borrowerId = UUID.randomUUID();
        borrower = new UserPrincipal(borrowerId, "b@example.com", "hash", "BORROWER", "ACTIVE", "Name Person", "+254712345678", false);
    }

    @Test
    void readsOnlyTheAuthenticatedBorrowerProfile() {
        when(authService.getAccount(borrowerId)).thenReturn(borrower);

        var result = service.get(borrower);

        assertEquals(borrowerId.toString(), result.userId());
        assertEquals("b@example.com", result.email());
        verify(authService).getAccount(borrowerId);
    }

    @Test
    void preventsNonBorrowerFromUsingBorrowerProfileService() {
        UserPrincipal admin = new UserPrincipal(UUID.randomUUID(), "a@example.com", "hash", "ADMINISTRATOR", "ACTIVE", null, null, false);
        when(authService.getAccount(admin.id())).thenReturn(admin);

        ApiException exception = assertThrows(ApiException.class, () -> service.get(admin));

        assertEquals(HttpStatus.FORBIDDEN, exception.status());
        verify(accounts, never()).updateBorrowerProfile(admin.id(), "Name Person", "+254712345678");
    }
}
