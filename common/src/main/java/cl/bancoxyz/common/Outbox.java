package cl.bancoxyz.common;

import java.util.*;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
@Component @ConditionalOnProperty(name="bank.messaging.enabled",havingValue="true")
public class Outbox {
 private final JdbcTemplate db;private final ObjectMapper json;private final KafkaTemplate<String,String> kafka;private final JmsTemplate jms;private final TransactionTemplate tx;
 public Outbox(JdbcTemplate d,ObjectMapper j,KafkaTemplate<String,String> k,JmsTemplate m,org.springframework.transaction.PlatformTransactionManager tm){db=d;json=j;kafka=k;jms=m;tx=new TransactionTemplate(tm);}
 // Call from a domain transaction: domain state and message become durable together.
 public void add(String target,String topic,String key,Map<String,Object> data){try{String id=UUID.randomUUID().toString();var event=new LinkedHashMap<>(data);event.put("eventId",id);event.put("version",1);event.put("occurredAt",java.time.Instant.now().toString());db.update("insert into outbox(id,target,topic,event_key,payload) values(?,?,?,?,?)",id,target,topic,key,json.writeValueAsString(event));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
 @Scheduled(fixedDelay=2000,initialDelay=10000) public void publish(){
  try{tx.executeWithoutResult(s->{for(var r:db.queryForList("select * from outbox where sent=false order by created limit 20 for update skip locked")){
   try{if(r.get("target").equals("JMS"))jms.convertAndSend(r.get("topic").toString(),r.get("payload").toString());else kafka.send(r.get("topic").toString(),r.get("event_key").toString(),r.get("payload").toString()).get(5,TimeUnit.SECONDS);
    db.update("update outbox set sent=true where id=?",r.get("id"));org.slf4j.LoggerFactory.getLogger(getClass()).info("EVENT_SENT id={} target={}",r.get("id"),r.get("target"));
   }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();db.update("update outbox set attempts=attempts+1 where id=?",r.get("id"));break;}
  }});}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Outbox temporalmente no disponible: {}",e.getClass().getSimpleName());}
 }
}
