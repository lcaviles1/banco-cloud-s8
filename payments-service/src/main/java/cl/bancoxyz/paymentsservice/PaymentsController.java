package cl.bancoxyz.paymentsservice;

import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import cl.bancoxyz.common.*;
@RestController @RequestMapping("/api/payments")
public class PaymentsController {
 private final Payments payments;private final Remote remote;public PaymentsController(Payments p,Remote r){payments=p;remote=r;}
 @PostMapping @PreAuthorize("hasAuthority('SCOPE_payments.write')") public ResponseEntity<?> create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Payments.Request r,@AuthenticationPrincipal Jwt j){
  if(r.type().equals("DEPOSIT")&&!j.getClaimAsStringList("scope").contains("deposits.write"))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,"Deposito requiere rol de caja");
  if("atm".equals(j.getClaimAsString("channel"))&&!r.type().equals("WITHDRAWAL"))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
  // Ownership checked before accepting, then again atomically by the accounts ledger.
  remote.get("accounts-service","/api/accounts/"+r.source(),j.getTokenValue());
  return ResponseEntity.accepted().body(payments.submit(key,SecurityConfig.owner(j),r));
 }
 @GetMapping @PreAuthorize("hasAuthority('SCOPE_payments.read')") public Object all(@AuthenticationPrincipal Jwt j){return Map.of("payments",payments.list(SecurityConfig.owner(j)));}
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('SCOPE_payments.read')") public Object one(@PathVariable String id,@AuthenticationPrincipal Jwt j){return payments.one(id,SecurityConfig.owner(j));}
 @GetMapping("/operations/status") @PreAuthorize("hasAuthority('SCOPE_ops.read')") public Object operations(){return Map.of("outbox",payments.outbox(),"resilience",remote.status());}
}
