package cl.duoc.bank.atm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

@Service
public class AtmService {
    private final BackendGateway gateway;
    private final ExecutorService executor;
    private final String configuredPin;

    public AtmService(BackendGateway gateway, ExecutorService executor,
                      @Value("${security.atm-pin}") String configuredPin) {
        this.gateway = gateway;
        this.executor = executor;
        this.configuredPin = configuredPin;
    }

    AtmSession session(long accountId, String pin) {
        verifyPin(pin);
        CompletableFuture<BackendGateway.AccountData> account =
                CompletableFuture.supplyAsync(() -> gateway.account(accountId), executor);
        CompletableFuture<BackendGateway.MovementOverview> movements =
                CompletableFuture.supplyAsync(() -> gateway.movementOverview(accountId), executor);
        try {
            CompletableFuture.allOf(account, movements).join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException runtime) throw runtime;
            throw ex;
        }
        var a = account.join();
        var recent = movements.join().movimientos().stream()
                .map(m -> new AtmMovement(m.fecha().toString(), m.tipo().toUpperCase(), m.monto()))
                .toList();
        return new AtmSession(mask(a.cuentaId()), a.saldo(), a.moneda(), recent,
                List.of("CONSULTAR_SALDO", "RETIRAR", "ULTIMOS_MOVIMIENTOS"));
    }

    Balance balance(long accountId, String pin) {
        verifyPin(pin);
        var a = gateway.account(accountId);
        return new Balance(mask(a.cuentaId()), a.saldo(), a.moneda(), a.actualizadoEn());
    }

    WithdrawalReceipt withdraw(long accountId, BigDecimal amount, String pin) {
        verifyPin(pin);
        if (amount == null || amount.signum() <= 0 || amount.remainder(BigDecimal.valueOf(1000)).signum() != 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El monto debe ser positivo y múltiplo de 1000");
        }
        var updated = gateway.withdraw(accountId, amount);
        return new WithdrawalReceipt("APROBADO", UUID.randomUUID().toString(), mask(accountId), amount,
                updated.saldo(), updated.moneda(), Instant.now());
    }

    private void verifyPin(String pin) {
        if (pin == null || !MessageDigest.isEqual(configuredPin.getBytes(StandardCharsets.UTF_8),
                pin.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "PIN inválido");
        }
    }

    private String mask(long accountId) {
        String raw = Long.toString(accountId);
        return "****" + raw.substring(Math.max(0, raw.length() - 3));
    }

    record AtmSession(String cuentaEnmascarada, BigDecimal saldoDisponible, String moneda,
                      List<AtmMovement> ultimosMovimientos, List<String> operacionesPermitidas) {}
    record AtmMovement(String fecha, String tipo, BigDecimal monto) {}
    record Balance(String cuentaEnmascarada, BigDecimal saldoDisponible, String moneda, Instant actualizadoEn) {}
    record WithdrawalReceipt(String estado, String operacionId, String cuentaEnmascarada,
                             BigDecimal montoRetirado, BigDecimal saldoDisponible, String moneda, Instant fecha) {}
}
