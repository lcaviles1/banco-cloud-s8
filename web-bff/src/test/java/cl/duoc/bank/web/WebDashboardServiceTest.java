package cl.duoc.bank.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebDashboardServiceTest {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach void close() { executor.close(); }

    @Test
    void aggregatesRichDataFromBothBackendServices() {
        BackendGateway gateway = mock(BackendGateway.class);
        when(gateway.account(101)).thenReturn(new BackendGateway.AccountData(101, "Diana Prince",
                new BigDecimal("8000"), "CLP", 35, "ahorro", "ACTIVA", Instant.now()));
        when(gateway.movementOverview(101, 10)).thenReturn(new BackendGateway.MovementOverview(
                List.of(new BackendGateway.MovementData(101, LocalDate.of(2024, 12, 1), "deposito",
                        new BigDecimal("1000"), "Ingreso", "CLP")),
                new BackendGateway.SummaryData(4, new BigDecimal("3000"), new BigDecimal("900"),
                        LocalDate.of(2024, 12, 1))));

        var dashboard = new WebDashboardService(gateway, executor).dashboard(101);

        assertThat(dashboard.cliente().nombreCompleto()).isEqualTo("Diana Prince");
        assertThat(dashboard.cuenta().interesMensualEstimado()).isEqualByComparingTo("32");
        assertThat(dashboard.movimientosRecientes()).hasSize(1);
        assertThat(dashboard.resumenAnual().cantidadMovimientos()).isEqualTo(4);
        assertThat(dashboard.enlaces()).hasSize(3);
    }
}
