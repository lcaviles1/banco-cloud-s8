package cl.duoc.bank.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/internal")
public class AccountController {
    private final LegacyAccountRepository repository;
    private final String serviceToken;

    public AccountController(LegacyAccountRepository repository,
                             @Value("${security.service-token}") String serviceToken) {
        this.repository = repository;
        this.serviceToken = serviceToken;
    }

    @GetMapping("/accounts/{accountId}")
    Account get(@PathVariable long accountId) {
        return repository.find(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    @GetMapping("/data-quality")
    LegacyAccountRepository.DataQuality quality() {
        return repository.quality();
    }

    @PostMapping("/accounts/{accountId}/withdrawals")
    Account withdraw(@PathVariable long accountId,
                     
                     @Valid @RequestBody WithdrawalRequest request) {
        return repository.withdraw(accountId, request.monto());
    }

    record WithdrawalRequest(@NotNull @DecimalMin("1") BigDecimal monto) {}

    @ExceptionHandler(AccountNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(AccountNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({InvalidWithdrawalException.class, IllegalArgumentException.class})
    ResponseEntity<Map<String, Object>> invalid(RuntimeException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(InvalidServiceTokenException.class)
    ResponseEntity<Map<String, Object>> unauthorized() {
        return error(HttpStatus.UNAUTHORIZED, "Credencial interna inválida");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(Map.of(
                "estado", status.value(), "detalle", detail, "instante", Instant.now().toString()));
    }
}

class InvalidServiceTokenException extends RuntimeException {}
