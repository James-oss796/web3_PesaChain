package com.pesachain.blockchain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pesachain.auth.ApiException;
import com.pesachain.auth.UserPrincipal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class BlockchainMonitoringServiceTest {
    @Mock private BlockchainOutboxRepository repository;
    @Mock private BlockchainReconciliationService reconciliation;
    @Mock private JdbcTemplate jdbc;
    @Mock private BlockchainClient client;
    private BlockchainMonitoringService service;
    private UserPrincipal administrator;

    @BeforeEach
    void setUp() {
        service = new BlockchainMonitoringService(repository, reconciliation, jdbc, client);
        administrator = new UserPrincipal(UUID.randomUUID(), "admin@example.test", "hash", "ADMINISTRATOR",
                "ACTIVE", "Admin", "+254700000000", false);
    }

    @Test
    void healthReturnsLiveConnectionAndOutboxCounts() {
        when(client.health()).thenReturn(BlockchainNodeHealth.available(31337, "31337", "42", Instant.now()));
        when(repository.statusCounts()).thenReturn(Map.of("FAILED", 2L, "PENDING", 1L));

        var result = service.health(administrator);

        assertEquals("AVAILABLE", result.connection().status());
        assertEquals(2L, result.outboxCounts().get("FAILED"));
        assertEquals(1L, result.outboxCounts().get("PENDING"));
        assertEquals(0L, result.outboxCounts().get("RECORDED"));
    }

    @Test
    void manualRetryRequiresAHealthyConfiguredChain() {
        UUID eventId = UUID.randomUUID();
        when(client.health()).thenReturn(BlockchainNodeHealth.unavailable(31337, Instant.now(), "RPC_UNAVAILABLE"));

        assertThrows(ApiException.class, () -> service.retryFailedEvent(administrator, eventId));

        verify(repository, never()).queueManualRetry(eventId, administrator.id());
    }

    @Test
    void manualRetryQueuesOnlyForAdministratorAfterHealthCheck() {
        UUID eventId = UUID.randomUUID();
        when(client.health()).thenReturn(BlockchainNodeHealth.available(31337, "31337", "42", Instant.now()));
        when(repository.queueManualRetry(eventId, administrator.id())).thenReturn(true);

        var result = service.retryFailedEvent(administrator, eventId);

        assertEquals(eventId, result.eventId());
        assertEquals("Blockchain delivery retry queued. The operational loan record is unchanged.", result.message());
        verify(repository).queueManualRetry(eventId, administrator.id());
    }

    @Test
    void otherRolesCannotInspectOrRetryChainOperations() {
        UserPrincipal officer = new UserPrincipal(UUID.randomUUID(), "officer@example.test", "hash", "LOAN_OFFICER",
                "ACTIVE", "Officer", "+254700000001", false);
        assertThrows(ApiException.class, () -> service.health(officer));
        assertThrows(ApiException.class, () -> service.retryFailedEvent(officer, UUID.randomUUID()));
        verify(client, never()).health();
    }
}
