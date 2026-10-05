package cl.duoc.bank.account;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController @RequestMapping("/api/cuentas")
public class CloudAccountsController {
 private final LegacyAccountRepository repo;
 public CloudAccountsController(LegacyAccountRepository repo){this.repo=repo;}
 private Map<String,Object> map(Account a){return Map.of("ID",a.cuentaId(),"NOMBRE",a.titular(),"SALDO",a.saldo(),"EDAD",a.edad(),"TIPO",a.tipo());}
 @GetMapping public Object all(){return repo.all().stream().map(this::map).toList();}
 @GetMapping("/{id}") public Object one(@PathVariable long id){return map(repo.find(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)));}
 @GetMapping("/importacion") public Object quality(){var q=repo.quality();return Map.of("resumen",List.of(Map.of("NOMBRE","intereses.csv","ACEPTADOS",q.cuentasDisponibles(),"RECHAZADOS",q.filasRechazadas()+q.duplicadosOmitidos())),"rechazos",List.of(),"calidadOriginal",q);}
}
