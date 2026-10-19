package com.atlas.erp.bootstrap;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock
    private AdminBootstrapService bootstrapService;

    @Test
    void run_whenDisabled_doesNotInvokeBootstrap() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                bootstrapService, false, "", "", "");

        runner.run(new DefaultApplicationArguments());

        verifyNoInteractions(bootstrapService);
    }

    @Test
    void run_whenEnabledPassesConfiguredCredentialsToBootstrapService() {
        when(bootstrapService.createFirstAdmin("Initial Admin", "admin@example.test", "LongTestPass123"))
                .thenReturn(true);
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                bootstrapService, true, "Initial Admin", "admin@example.test", "LongTestPass123");

        runner.run(new DefaultApplicationArguments());

        verify(bootstrapService).createFirstAdmin("Initial Admin", "admin@example.test", "LongTestPass123");
    }
}
