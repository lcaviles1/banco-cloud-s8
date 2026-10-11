package cl.bancoxyz.paymentsservice;

import java.math.BigDecimal;
import java.util.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import cl.bancoxyz.common.Outbox;
@Service
public class Payments {
 public record Request(@NotNull @Pattern(regexp="DEPOSIT|WITHDRAWAL|TRANSFER") String type,@Positive long source,Long destination,@NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal amount){}
 private final JdbcTemplate db;private final ObjectMapper json;private final Outbox events;
 public Payments(JdbcTemplate d,ObjectMapper j,Outbox o){db=d;json=j;events=o;}
 @Transactional public Map<String,Object> submit(String key,String owner,Request req){
  if(!key.matches("[A-Za-z0-9_-]{8,80}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Clave invalida");
  if(req.type().equals("TRANSFER")&&(req.destination()==null||req.destination()==req.source()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Destino invalido");
  if(!req.type().equals("TRANSFER") && req.destination()!=null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Destino solo para transferencia");
  String fp=req.type()+"|"+req.source()+"|"+req.destination()+"|"+req.amount().stripTrailingZeros().toPlainString();
  String id=UUID.randomUUID().toString();
  db.update("insert into payments(id,request_key,owner,type,source,destination,amount,fingerprint,status) values(?,?,?,?,?,?,?,?,'PENDING') on conflict(owner,request_key) do nothing",id,key,owner,req.type(),req.source(),req.destination(),req.amount(),fp);
  var row=db.queryForMap("select * from payments where owner=? and request_key=?",owner,key);
  if(!row.get("fingerprint").equals(fp))throw new ResponseStatusException(HttpStatus.CONFLICT,"Clave reutilizada con otro contenido");return row;
 }
 public List<Map<String,Object>> list(String owner){return db.queryForList("select id,type,source,destination,amount,status,reason from payments where owner=? order by created desc limit 20",owner);}
 public Map<String,Object> one(String id,String owner){var rows=db.queryForList("select * from payments where id=? and owner=?",id,owner);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);return rows.getFirst();}
 public Map<String,Object> outbox(){return Map.of("pending",db.queryForObject("select count(*) from outbox where sent=false",Integer.class),"sent",db.queryForObject("select count(*) from outbox where sent=true",Integer.class));}
}
