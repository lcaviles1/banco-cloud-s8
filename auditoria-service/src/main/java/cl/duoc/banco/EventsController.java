package cl.duoc.banco;
import java.util.*;
import java.time.Instant;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController
public class EventsController {
 private final JdbcTemplate db;private final ObjectMapper json;
 public EventsController(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
 @JmsListener(destination="banco.operaciones") @Transactional public void receive(String payload) throws Exception {
  var event=json.readTree(payload);
  String id=event.path("eventId").asText();UUID.fromString(id);
  if(!event.path("type").asText().equals("OperacionRegistrada")||event.path("version").asInt()!=1)throw new IllegalArgumentException("Contrato no soportado");
  if(db.queryForObject("select count(*) from eventos where id=?",Integer.class,id)==0){
   db.update("insert into eventos values(?,?,?,?)",id,"OperacionRegistrada",payload,Instant.now().toString());
   org.slf4j.LoggerFactory.getLogger(getClass()).info("EVENT_CONSUMED eventId={}",id);
  }
 }
 @GetMapping("/api/eventos") public Object all(){return db.queryForList("select id,tipo,cast(payload as varchar) as payload,recibido from eventos order by recibido,id");}
}
