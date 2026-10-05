package cl.duoc.bank.web;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

@Service
public class WebDashboardService {
    private final BackendGateway gateway;
    private final ExecutorService executor;

    public WebDashboardService(BackendGateway gateway, ExecutorService executor) {
        this.gateway = gateway;
        this.executor = executor;
    }

    WebDashboard dashboard(long accountId) {
        CompletableFuture<BackendGateway.AccountData> account =
                CompletableFuture.supplyAsync(() -> gateway.account(accountId), executor);
        CompletableFuture<BackendGateway.MovementOverview> movements =
                CompletableFuture.supplyAsync(() -> gateway.movementOverview(accountId, 10), executor);
        try {
            CompletableFuture.allOf(account, movements).join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException runtime) throw runtime;
            throw ex;
        }

        var a = account.join();
        var m = movements.join();
        BigDecimal monthlyInterest = a.tipo().equals("ahorro")
                ? a.saldo().multiply(new BigDecimal("0.004")).setScale(0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new WebDashboard(
                new Customer(a.titular(), a.edad()),
                new Account(a.cuentaId(), a.tipo(), a.estado(), a.saldo(), a.saldo(), a.moneda(), monthlyInterest),
                m.movimientos().stream().map(x -> new Movement(x.fecha().toString(), x.tipo(), x.descripcion(),
                        x.monto(), x.moneda())).toList(),
                new Totals(m.resumen().cantidad(), m.resumen().totalAbonos(), m.resumen().totalCargos(),
                        m.resumen().ultimoMovimiento() == null ? null : m.resumen().ultimoMovimiento().toString()),
                List.of(new Link("self", "/api/web/accounts/" + accountId + "/dashboard"),
                        new Link("mobile", "http://localhost:8092/api/mobile/accounts/" + accountId + "/home"),
                        new Link("atm", "http://localhost:8093/api/atm/accounts/" + accountId + "/session")),
                Instant.now());
    }

    record WebDashboard(Customer cliente, Account cuenta, List<Movement> movimientosRecientes,
                        Totals resumenAnual, List<Link> enlaces, Instant generadoEn) {}
    record Customer(String nombreCompleto, int edad) {}
    record Account(long id, String producto, String estado, BigDecimal saldoContable,
                   BigDecimal saldoDisponible, String moneda, BigDecimal interesMensualEstimado) {}
    record Movement(String fecha, String tipo, String descripcion, BigDecimal monto, String moneda) {}
    record Totals(int cantidadMovimientos, BigDecimal totalAbonos, BigDecimal totalCargos,
                  String fechaUltimoMovimiento) {}
    record Link(String relacion, String href) {}
}
