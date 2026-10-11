package cl.bancoxyz.accountsservice;

import java.math.BigDecimal;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class Accounts {
 public record Posting(@NotBlank String paymentId,@NotBlank String owner,@NotBlank @Pattern(regexp="DEPOSIT|WITHDRAWAL|TRANSFER") String type,@Positive long source,Long destination,@NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal amount){}
 private final JdbcTemplate db;public Accounts(JdbcTemplate d){db=d;}
 public Map<String,Object> one(long id){var l=db.queryForList("select * from accounts where id=?",id);if(l.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Cuenta no encontrada");return l.getFirst();}
 public List<Map<String,Object>> list(String owner){return db.queryForList("select * from accounts where owner=? order by id",owner);}
 @Transactional public Map<String,Object> open(String owner,String type){long id=db.queryForObject("insert into accounts(owner,type,balance,status) values(?,?,0,'OPEN') returning id",Long.class,owner,type);return one(id);}
 @Transactional public Map<String,Object> maintain(long id,String owner,String type,boolean close){var a=db.queryForMap("select * from accounts where id=? for update",id);if(!a.get("owner").equals(owner))throw new ResponseStatusException(HttpStatus.FORBIDDEN);if(close && ((BigDecimal)a.get("balance")).signum()!=0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Cuenta con saldo; cierre rechazado");if(a.get("status").equals("CLOSED"))throw new ResponseStatusException(HttpStatus.CONFLICT,"Cuenta cerrada");db.update("update accounts set type=?,status=? where id=?",type,close?"CLOSED":"OPEN",id);return one(id);}
 @Transactional public Map<String,Object> apply(Posting p){
  String fingerprint=p.owner()+"|"+p.type()+"|"+p.source()+"|"+p.destination()+"|"+p.amount().stripTrailingZeros().toPlainString();
  int inserted=db.update("insert into ledger(payment_id,fingerprint,applied) values(?,?,false) on conflict do nothing",p.paymentId(),fingerprint);
  if(inserted==0){var old=db.queryForMap("select * from ledger where payment_id=?",p.paymentId());if(!old.get("fingerprint").equals(fingerprint))throw new ResponseStatusException(HttpStatus.CONFLICT,"Operacion reutilizada");return Map.of("paymentId",p.paymentId(),"applied",true,"replayed",true);}
  if(p.type().equals("TRANSFER") && (p.destination()==null || p.destination()==p.source()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Destino invalido");
  long other=p.destination()==null?p.source():p.destination();
  // Stable lock order prevents transfer A->B / B->A deadlocks.
  var locked=db.queryForList("select * from accounts where id in (?,?) order by id for update",p.source(),other);
  var a=locked.stream().filter(x->((Number)x.get("id")).longValue()==p.source()).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  if(!a.get("owner").equals(p.owner()))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  if(!a.get("status").equals("OPEN"))throw new ResponseStatusException(HttpStatus.CONFLICT,"Cuenta cerrada");
  BigDecimal delta=p.type().equals("DEPOSIT")?p.amount():p.amount().negate();
  if(((BigDecimal)a.get("balance")).add(delta).signum()<0)throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Fondos insuficientes");
  if(p.type().equals("TRANSFER")){var dest=locked.stream().filter(x->((Number)x.get("id")).longValue()==other).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));if(!dest.get("status").equals("OPEN"))throw new ResponseStatusException(HttpStatus.CONFLICT,"Destino cerrado");db.update("update accounts set balance=balance+? where id=?",p.amount(),other);}
  db.update("update accounts set balance=balance+? where id=?",delta,p.source());db.update("update ledger set applied=true where payment_id=?",p.paymentId());
  return Map.of("paymentId",p.paymentId(),"applied",true,"replayed",false);
 }
}
