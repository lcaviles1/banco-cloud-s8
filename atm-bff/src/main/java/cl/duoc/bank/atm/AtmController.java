package cl.duoc.bank.atm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/atm")
public class AtmController {
    private final AtmService service;

    public AtmController(AtmService service) {
        this.service = service;
    }

    @GetMapping("/accounts/{accountId}/session")
    ResponseEntity<AtmService.AtmSession> session(@PathVariable long accountId,
                                                  @RequestHeader("X-ATM-PIN") String pin) {
        return ResponseEntity.ok().header("X-BFF-Channel", "atm-secure")
                .cacheControl(CacheControl.noStore()).body(service.session(accountId, pin));
    }

    @GetMapping("/accounts/{accountId}/balance")
    ResponseEntity<AtmService.Balance> balance(@PathVariable long accountId,
                                               @RequestHeader("X-ATM-PIN") String pin) {
        return ResponseEntity.ok().header("X-BFF-Channel", "atm-secure")
                .cacheControl(CacheControl.noStore()).body(service.balance(accountId, pin));
    }

    @PostMapping("/accounts/{accountId}/withdrawals")
    ResponseEntity<AtmService.WithdrawalReceipt> withdraw(@PathVariable long accountId,
                                                          @RequestHeader("X-ATM-PIN") String pin,
                                                          @Valid @RequestBody WithdrawalRequest request) {
        return ResponseEntity.ok().header("X-BFF-Channel", "atm-secure")
                .cacheControl(CacheControl.noStore()).body(service.withdraw(accountId, request.monto(), pin));
    }

    record WithdrawalRequest(@NotNull @DecimalMin("1") BigDecimal monto) {}
}
