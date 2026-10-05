package cl.duoc.banco;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class OperationsService {
 private final JdbcTemplate db;private final ObjectMapper json;
 public OperationsService(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
 public List<Map<String,Object>> all(){return db.queryForList("select * from operaciones order by creado,id");}
 @Transactional public Map<String,Object> register(String key,OperationsController.Request req) throws Exception {
  // Database uniqueness remains authoritative when concurrent requests reuse a key.
  var previous=db.queryForList("select * from operaciones where request_key=?",key);
  if(!previous.isEmpty()){
   var p=previous.getFirst();
   if(((Number)p.get("CUENTA_ID")).longValue()!=req.cuentaId()||!p.get("TIPO").equals(req.tipo())||((BigDecimal)p.get("MONTO")).compareTo(req.monto())!=0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Clave reutilizada con otro contenido");
   return p;
  }
  String id=UUID.randomUUID().toString(),now=Instant.now().toString();
  db.update("insert into operaciones values(?,?,?,?,?,?)",id,key,req.cuentaId(),req.tipo(),req.monto(),now);
  var event=Map.of("eventId",id,"type","OperacionRegistrada","version",1,"cuentaId",req.cuentaId(),"tipo",req.tipo(),"monto",req.monto(),"occurredAt",now);
  db.update("insert into outbox(id,payload) values(?,?)",id,json.writeValueAsString(event));
  return db.queryForMap("select * from operaciones where id=?",id);
 }
 public Map<String,Object> outbox(){return Map.of("pendientes",db.queryForObject("select count(*) from outbox where enviado=false",Integer.class),"enviados",db.queryForObject("select count(*) from outbox where enviado=true",Integer.class));}
}
