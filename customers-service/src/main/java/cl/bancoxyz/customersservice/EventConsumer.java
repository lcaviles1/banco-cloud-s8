package cl.bancoxyz.customersservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.jms.annotation.JmsListener;
@Component
public class EventConsumer {
 private final JdbcTemplate db;private final ObjectMapper json;public EventConsumer(JdbcTemplate d,ObjectMapper j){db=d;json=j;}
 @KafkaListener(topics={"transactions.completed.v1","security.alerts.v1"},groupId="customers-audit-v1") @Transactional public void kafka(String payload)throws Exception{store("KAFKA",payload);}
 @JmsListener(destination="bank.security.alerts") @Transactional public void jms(String payload)throws Exception{store("JMS",payload);}
 private void store(String channel,String payload)throws Exception{var e=json.readTree(payload);String id=e.path("eventId").asText();if(!id.matches("[0-9a-f-]{36}")||e.path("version").asInt()!=1)throw new IllegalArgumentException("Evento invalido");db.update("insert into inbox(event_id,channel,payload) values(?,?,?) on conflict do nothing",id,channel,payload);org.slf4j.LoggerFactory.getLogger(getClass()).info("EVENT_RECEIVED id={} channel={}",id,channel);}
}
