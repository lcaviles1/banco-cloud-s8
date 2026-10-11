package cl.bancoxyz.paymentsservice;

import java.util.*;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.web.client.HttpClientErrorException;
import cl.bancoxyz.common.*;
@Component
public class PaymentWorker {
 private final JdbcTemplate db;private final Remote remote;private final Outbox events;private final TransactionTemplate tx;private final OAuth2AuthorizedClientManager auth;
 public PaymentWorker(JdbcTemplate d,Remote r,Outbox o,org.springframework.transaction.PlatformTransactionManager tm,ClientRegistrationRepository registrations,OAuth2AuthorizedClientService clients){db=d;remote=r;events=o;tx=new TransactionTemplate(tm);var manager=new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations,clients);manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());auth=manager;}
 @Scheduled(fixedDelay=2500,initialDelay=15000) public void reconcile(){
  try{tx.executeWithoutResult(s->{for(var p:db.queryForList("select * from payments where status='PENDING' and next_attempt<=current_timestamp order by created limit 10 for update skip locked")){
   String id=p.get("id").toString();
   try{
    var authorized=auth.authorize(OAuth2AuthorizeRequest.withClientRegistrationId("payments-worker").principal("payments-service").build());
    var body=new LinkedHashMap<String,Object>();for(String k:List.of("owner","type","source","destination","amount"))body.put(k,p.get(k));body.put("paymentId",id);
    remote.post("accounts-service","/api/accounts/postings",authorized.getAccessToken().getTokenValue(),body,id);
    db.update("update payments set status='COMPLETED',reason=null where id=?",id);
    var e=Map.<String,Object>of("type","TransactionCompleted","paymentId",id,"customerId",p.get("owner"),"source",p.get("source"),"amount",p.get("amount"));
    events.add("KAFKA","transactions.completed.v1",p.get("source").toString(),e);
    if(((BigDecimal)p.get("amount")).compareTo(new BigDecimal("10000"))>=0){var alert=Map.<String,Object>of("type","HighValueTransaction","paymentId",id,"customerId",p.get("owner"));events.add("KAFKA","security.alerts.v1",id,alert);events.add("JMS","bank.security.alerts",id,alert);}
   }catch(HttpClientErrorException e){if(e.getStatusCode().value()==401 || e.getStatusCode().value()==403){retry(id);}else{db.update("update payments set status='REJECTED',reason=? where id=?","ACCOUNTS_HTTP_"+e.getStatusCode().value(),id);}}
   catch(RuntimeException e){retry(id);}
  }});}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Reconciliacion pendiente: {}",e.getClass().getSimpleName());}
 }
 private void retry(String id){db.update("update payments set attempts=attempts+1,next_attempt=current_timestamp+interval '10 seconds',reason='RECONCILING' where id=?",id);}
}
