package cl.duoc.bank.mobile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
public class BackendGateway {
    private final RestClient accounts;
    private final RestClient movements;

    public BackendGateway(RestClient.Builder builder,
                          @Value("${clients.accounts.base-url}") String accountsUrl,
                          @Value("${clients.movements.base-url}") String movementsUrl) {
        this.accounts = builder.clone().baseUrl(accountsUrl).build();
        this.movements = builder.clone().baseUrl(movementsUrl).build();
    }

    AccountData account(long id) {
        try {
            return accounts.get().uri("/internal/accounts/{id}", id).retrieve().body(AccountData.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada");
            }
            throw unavailable(ex);
        } catch (RestClientException ex) {
            throw unavailable(ex);
        }
    }

    MovementOverview movementOverview(long id) {
        try {
            return movements.get().uri(uri -> uri.path("/internal/accounts/{id}/movements-overview")
                            .queryParam("limit", 1).build(id))
                    .retrieve().body(MovementOverview.class);
        } catch (RestClientException ex) {
            throw unavailable(ex);
        }
    }

    private ResponseStatusException unavailable(Exception ex) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Servicio bancario temporalmente no disponible", ex);
    }

    record AccountData(long cuentaId, String titular, BigDecimal saldo, String moneda, int edad,
                       String tipo, String estado, Instant actualizadoEn) {}
    record MovementData(long cuentaId, LocalDate fecha, String tipo, BigDecimal monto,
                        String descripcion, String moneda) {}
    record SummaryData(int cantidad, BigDecimal totalAbonos, BigDecimal totalCargos,
                       LocalDate ultimoMovimiento) {}
    record MovementOverview(List<MovementData> movimientos, SummaryData resumen) {}
}
