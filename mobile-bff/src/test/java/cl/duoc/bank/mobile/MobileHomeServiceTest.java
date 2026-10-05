package cl.duoc.bank.mobile;

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

class MobileHomeServiceTest {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach void close() { executor.close(); }

    @Test
    void projectsOnlyEssentialMobileFields() {
        BackendGateway gateway = mock(BackendGateway.class);
        when(gateway.account(101)).thenReturn(new BackendGateway.AccountData(101, "Diana Prince",
                new BigDecimal("8000"), "CLP", 35, "ahorro", "ACTIVA", Instant.now()));
        when(gateway.movementOverview(101)).thenReturn(new BackendGateway.MovementOverview(
                List.of(new BackendGateway.MovementData(101, LocalDate.of(2024, 12, 1), "compra",
                        new BigDecimal("-500"), "Compra", "CLP")),
                new BackendGateway.SummaryData(8, new BigDecimal("5000"), new BigDecimal("2500"),
                        LocalDate.of(2024, 12, 1))));

        var home = new MobileHomeService(gateway, executor).home(101);

        assertThat(home.saludo()).isEqualTo("Hola, Diana");
        assertThat(home.saldoDisponible()).isEqualByComparingTo("8000");
        assertThat(home.ultimoMovimiento().tipo()).isEqualTo("CARGO");
        assertThat(home.acciones()).containsExactly("TRANSFERIR", "PAGAR", "MOVIMIENTOS");
    }
}
