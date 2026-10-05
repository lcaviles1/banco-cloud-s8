package cl.duoc.bank.mobile;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

@Service
public class MobileHomeService {
    private final BackendGateway gateway;
    private final ExecutorService executor;

    public MobileHomeService(BackendGateway gateway, ExecutorService executor) {
        this.gateway = gateway;
        this.executor = executor;
    }

    MobileHome home(long accountId) {
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
        var last = movements.join().movimientos().stream().findFirst()
                .map(m -> new LastMovement(m.fecha().toString(), m.monto(), shortType(m.tipo())))
                .orElse(null);
        String firstName = a.titular().split("\\s+")[0];
        return new MobileHome(a.cuentaId(), "Hola, " + firstName, a.saldo(), a.moneda(), last,
                List.of("TRANSFERIR", "PAGAR", "MOVIMIENTOS"));
    }

    private String shortType(String type) {
        return type.equals("deposito") ? "ABONO" : "CARGO";
    }

    record MobileHome(long cuentaId, String saludo, BigDecimal saldoDisponible, String moneda,
                      LastMovement ultimoMovimiento, List<String> acciones) {}
    record LastMovement(String fecha, BigDecimal monto, String tipo) {}
}
