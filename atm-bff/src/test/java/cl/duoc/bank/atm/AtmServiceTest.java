package cl.duoc.bank.atm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AtmServiceTest {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach void close() { executor.close(); }

    @Test
    void protectsAndMinimizesAtmSession() {
        BackendGateway gateway = mock(BackendGateway.class);
        when(gateway.account(101)).thenReturn(account("8000"));
        when(gateway.movementOverview(101)).thenReturn(new BackendGateway.MovementOverview(
                List.of(new BackendGateway.MovementData(101, LocalDate.of(2024, 12, 1), "compra",
                        new BigDecimal("-1000"), "Compra privada", "CLP")),
                new BackendGateway.SummaryData(1, BigDecimal.ZERO, new BigDecimal("1000"),
                        LocalDate.of(2024, 12, 1))));

        var session = new AtmService(gateway, executor, "1234").session(101, "1234");
        assertThat(session.cuentaEnmascarada()).isEqualTo("****101");
        assertThat(session.ultimosMovimientos().getFirst().monto()).isEqualByComparingTo("-1000");
        assertThat(session.operacionesPermitidas()).contains("RETIRAR");
    }

    @Test
    void validatesPinAndWithdrawalDenomination() {
        BackendGateway gateway = mock(BackendGateway.class);
        var service = new AtmService(gateway, executor, "1234");
        assertThatThrownBy(() -> service.balance(101, "0000")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.withdraw(101, new BigDecimal("1500"), "1234"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void returnsReceiptAfterApprovedWithdrawal() {
        BackendGateway gateway = mock(BackendGateway.class);
        when(gateway.withdraw(101, new BigDecimal("1000"))).thenReturn(account("7000"));
        var receipt = new AtmService(gateway, executor, "1234")
                .withdraw(101, new BigDecimal("1000"), "1234");
        assertThat(receipt.estado()).isEqualTo("APROBADO");
        assertThat(receipt.saldoDisponible()).isEqualByComparingTo("7000");
    }

    private BackendGateway.AccountData account(String balance) {
        return new BackendGateway.AccountData(101, "Diana Prince", new BigDecimal(balance), "CLP",
                35, "ahorro", "ACTIVA", Instant.now());
    }
}
