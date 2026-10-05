package cl.duoc.banco;
import java.math.BigDecimal;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DuplicateKeyException;
@RestController
public class OperationsController {
 public record Request(@Positive long cuentaId,@NotNull @Pattern(regexp="DEPOSITO|RETIRO") String tipo,@NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal monto){}
 private final AccountsClient accounts;private final OperationsService service;
 public OperationsController(AccountsClient accounts,OperationsService service){this.accounts=accounts;this.service=service;}
 @GetMapping("/api/operaciones") public Object all(){return service.all();}
 @GetMapping("/api/operaciones/outbox") public Object pending(){return service.outbox();}
 @GetMapping("/api/resiliencia") public Object resilience(){return accounts.status();}
 @GetMapping("/api/operaciones/contexto/{id}") public Object context(@PathVariable long id,@AuthenticationPrincipal Jwt token){
  try{return Map.of("estado","DISPONIBLE","cuenta",accounts.get(id,token.getTokenValue()));}
  catch(HttpClientErrorException e){throw new ResponseStatusException(e.getStatusCode());}
  catch(RuntimeException e){return Map.of("estado","DEGRADADO","cuentaId",id,"detalle","Cuentas temporalmente no disponible; no se informa saldo estimado");}
 }
 @PostMapping("/api/operaciones") public ResponseEntity<?> register(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Request req,@AuthenticationPrincipal Jwt token) throws Exception {
  if(!key.matches("[A-Za-z0-9_-]{8,80}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Idempotency-Key invalida");
  try{accounts.get(req.cuentaId(),token.getTokenValue());}
  catch(HttpClientErrorException e){throw new ResponseStatusException(e.getStatusCode());}
  catch(RuntimeException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Cuentas no disponible; operacion no registrada");}
  try{return ResponseEntity.status(HttpStatus.CREATED).body(service.register(key,req));}
  catch(DuplicateKeyException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Solicitud concurrente; reintente con la misma clave");}
 }
}
