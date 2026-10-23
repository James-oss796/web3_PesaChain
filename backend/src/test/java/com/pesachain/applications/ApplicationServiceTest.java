package com.pesachain.applications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pesachain.auth.ApiException;
import com.pesachain.auth.UserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {
    @Mock
    private ApplicationRepository applications;

    private ApplicationService service;
    private UserPrincipal borrower;

    @BeforeEach
    void setUp() {
        service = new ApplicationService(applications);
        borrower = new UserPrincipal(UUID.randomUUID(), "borrower@example.com", "hash", "BORROWER", "ACTIVE", "Borrower Name", "+254712345678", false);
    }

    @Test
    void submitsPositiveKesAmountAndPurposeForTheAuthenticatedBorrower() {
        var expected = new ApplicationRepository.ApplicationView(
                UUID.randomUUID(), new BigDecimal("10000.00"), "Equipment", "PENDING", Instant.now(), null, null,
                6,null,null,null,null,null);
        UUID key = UUID.randomUUID();
        when(applications.create(borrower.id(), new BigDecimal("10000.00"), "Equipment",6,key)).thenReturn(expected);

        var result = service.submit(borrower, new BigDecimal("10000.00"), "  Equipment  ",6,key);

        assertEquals("KES", result.currency());
        assertEquals("PENDING", result.status());
        assertEquals("Equipment", result.purpose());
        assertEquals(6,result.requestedTermMonths());
        verify(applications).create(borrower.id(), new BigDecimal("10000.00"), "Equipment",6,key);
    }

    @Test
    void rejectsZeroNegativeAndOverPrecisionAmounts() {
        for (BigDecimal invalid : new BigDecimal[] {
                BigDecimal.ZERO, new BigDecimal("-1.00"), new BigDecimal("1.001"),
                new BigDecimal("1" + "0".repeat(28) + ".00")}) {
            assertThrows(ApiException.class, () -> service.submit(borrower, invalid, "Purpose",6,UUID.randomUUID()));
        }
        verify(applications, never()).create(any(), any(), anyString(),anyInt(),any());
    }

    @Test
    void rejectsNonBorrowerAndBoundsListPagination() {
        UserPrincipal administrator = new UserPrincipal(UUID.randomUUID(), "admin@example.com", "hash", "ADMINISTRATOR", "ACTIVE", null, null, false);
        ApiException roleError = assertThrows(ApiException.class, () -> service.listMine(administrator, 0, 20));
        assertEquals(HttpStatus.FORBIDDEN, roleError.status());

        ApiException pageError = assertThrows(ApiException.class, () -> service.listMine(borrower, 0, 101));
        assertEquals(HttpStatus.BAD_REQUEST, pageError.status());
        verify(applications, never()).findMine(any(), anyInt(), anyInt());
    }

    @Test
    void hidesApplicationsThatDoNotBelongToBorrower() {
        UUID otherApplicationId = UUID.randomUUID();
        when(applications.findMineById(borrower.id(), otherApplicationId)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> service.getMine(borrower, otherApplicationId));

        assertEquals(HttpStatus.NOT_FOUND, exception.status());
        verify(applications).findMineById(borrower.id(), otherApplicationId);
    }
}
