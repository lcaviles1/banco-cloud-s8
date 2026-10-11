package cl.bancoxyz.common;

import java.util.*;
import java.util.concurrent.*;
import java.math.BigDecimal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.*;
@RestController @ConditionalOnProperty(name="bank.channel")
public class BffController {
 private final Remote remote;private final String channel,pinHash;
 private final ExecutorService executor=Executors.newFixedThreadPool(8);
 public BffController(Remote r,@Value("${bank.channel}") String c,@Value("${bank.atm.pin-hash:}") String h){remote=r;channel=c;pinHash=h;}
 @jakarta.annotation.PreDestroy void stop(){executor.shutdown();}
 private Map safe(String service,String path,String token){try{return remote.get(service,path,token);}catch(org.springframework.web.client.HttpClientErrorException e){throw e;}catch(RuntimeException e){return Map.of("available",false,"status","TEMPORARILY_UNAVAILABLE");}}
 @GetMapping("/api/web/dashboard") @PreAuthorize("hasAuthority('SCOPE_accounts.read') and hasAuthority('SCOPE_customers.read') and hasAuthority('SCOPE_payments.read')") public Object web(@AuthenticationPrincipal Jwt j){require("web");String t=j.getTokenValue();var accounts=CompletableFuture.supplyAsync(()->safe("accounts-service","/api/accounts",t),executor);var customer=CompletableFuture.supplyAsync(()->safe("customers-service","/api/customers/me",t),executor);var payments=CompletableFuture.supplyAsync(()->safe("payments-service","/api/payments",t),executor);return Map.of("channel","web","customer",customer.join(),"accounts",accounts.join(),"payments",payments.join(),"generatedAt",java.time.Instant.now().toString());}
 @GetMapping("/api/mobile/home") @PreAuthorize("hasAuthority('SCOPE_accounts.read')") public Object mobile(@AuthenticationPrincipal Jwt j){require("mobile");var a=safe("accounts-service","/api/accounts",j.getTokenValue());if(!a.containsKey("accounts"))return a;var list=((List<Map>)a.get("accounts")).stream().map(x->Map.of("id",x.get("id"),"balance",x.get("balance"))).toList();return Map.of("accounts",list);}
 @GetMapping("/api/atm/balance/{id}") @PreAuthorize("hasAuthority('SCOPE_accounts.read')") public Object balance(@PathVariable long id,@RequestHeader("X-ATM-PIN") String pin,@AuthenticationPrincipal Jwt j){require("atm");pin(pin);try{var a=remote.get("accounts-service","/api/accounts/"+id,j.getTokenValue());return Map.of("account",id,"balance",a.get("balance"),"currency","CLP");}catch(org.springframework.web.client.HttpClientErrorException e){throw e;}catch(RuntimeException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No es posible confirmar saldo");}}
 public record Payment(@NotNull @Pattern(regexp="DEPOSIT|WITHDRAWAL|TRANSFER") String type,@Positive long source,Long destination,@NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal amount){}
 @PostMapping("/api/{route}/payments") @PreAuthorize("hasAuthority('SCOPE_payments.write')") public ResponseEntity<?> pay(@PathVariable String route,@RequestHeader("Idempotency-Key") String key,@RequestHeader(value="X-ATM-PIN",required=false) String pin,@Valid @RequestBody Payment p,@AuthenticationPrincipal Jwt j){require(route);if(channel.equals("atm")){pin(pin);if(!p.type().equals("WITHDRAWAL"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);if(p.amount().compareTo(new BigDecimal("500"))>0)throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Limite por retiro 500");}try{return ResponseEntity.accepted().body(remote.post("payments-service","/api/payments",j.getTokenValue(),p,key));}catch(org.springframework.web.client.HttpClientErrorException e){throw e;}catch(RuntimeException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Resultado no confirmado; consulte o reintente con la misma clave");}}
 @GetMapping("/api/{route}/payments/{id}") @PreAuthorize("hasAuthority('SCOPE_payments.read')") public Object payment(@PathVariable String route,@PathVariable String id,@AuthenticationPrincipal Jwt j){require(route);return remote.get("payments-service","/api/payments/"+id,j.getTokenValue());}
 private void require(String c){if(!channel.equals(c))throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
 private static String hash(String p){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(p.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private void pin(String p){if(p==null||pinHash.isBlank()||!java.security.MessageDigest.isEqual(hash(p).getBytes(java.nio.charset.StandardCharsets.UTF_8),pinHash.getBytes(java.nio.charset.StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"PIN invalido");}
}
