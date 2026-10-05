package cl.duoc.banco;
import java.time.Duration;
import io.github.resilience4j.retry.*;
import io.github.resilience4j.bulkhead.*;
import io.github.resilience4j.circuitbreaker.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.*;
import org.springframework.test.web.client.MockRestServiceServer;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.junit.jupiter.api.Assertions.*;
class ResilienceTest {
 @Test void transientFailureRetriesThenReturnsAccount(){
  var b=RestClient.builder();var server=MockRestServiceServer.bindTo(b).build();
  server.expect(requestTo("http://account-service/api/cuentas/106")).andRespond(withServerError());
  server.expect(requestTo("http://account-service/api/cuentas/106")).andExpect(header("Authorization","Bearer token")).andRespond(withSuccess("{\"ID\":106}",MediaType.APPLICATION_JSON));
  var r=Retry.of("test",RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ZERO).retryExceptions(HttpServerErrorException.class).build());
  var c=new AccountsClient(b,CircuitBreaker.ofDefaults("test"),r,Bulkhead.ofDefaults("test"));
  assertEquals(106,c.get(106,"token").get("ID"));assertEquals(1,r.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt());server.verify();
 }
 @Test void clientErrorIsNotRetried(){
  var b=RestClient.builder();var s=MockRestServiceServer.bindTo(b).build();s.expect(requestTo("http://account-service/api/cuentas/999")).andRespond(withResourceNotFound());
  var cfg=new HttpConfiguration();var c=new AccountsClient(b,cfg.accountsCircuit(),cfg.accountsRetry(),cfg.accountsBulkhead());
  assertThrows(HttpClientErrorException.NotFound.class,()->c.get(999,"token"));s.verify();assertEquals("CLOSED",c.status().get("circuitBreaker"));
 }
 @Test void bulkheadRejectsExcessConcurrencyWithoutRemoteCall(){
  var cfg=new HttpConfiguration();var bulk=cfg.accountsBulkhead();for(int i=0;i<8;i++)assertTrue(bulk.tryAcquirePermission());
  var c=new AccountsClient(RestClient.builder(),cfg.accountsCircuit(),cfg.accountsRetry(),bulk);
  assertThrows(BulkheadFullException.class,()->c.get(106,"token"));for(int i=0;i<8;i++)bulk.onComplete();
 }
}
