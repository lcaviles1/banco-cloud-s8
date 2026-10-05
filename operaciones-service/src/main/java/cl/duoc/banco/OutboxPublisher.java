package cl.duoc.banco;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
@Configuration @EnableScheduling class SchedulingConfiguration {}
@Component
public class OutboxPublisher {
 private final JdbcTemplate db;private final JmsTemplate jms;
 public OutboxPublisher(JdbcTemplate db,JmsTemplate jms){this.db=db;this.jms=jms;}
 @Scheduled(fixedDelay=2000,initialDelay=5000) public void publish(){
  for(var row:db.queryForList("select id,cast(payload as varchar) as payload from outbox where enviado=false order by id limit 50")){
   String id=(String)row.get("ID");
   try {jms.convertAndSend("banco.operaciones",row.get("PAYLOAD"));db.update("update outbox set enviado=true where id=?",id);org.slf4j.LoggerFactory.getLogger(getClass()).info("EVENT_PUBLISHED eventId={}",id);}
   catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("EVENT_PENDING eventId={} reason={}",id,e.getClass().getSimpleName());break;}
  }
 }
}
