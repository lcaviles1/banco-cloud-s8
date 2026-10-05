package cl.duoc.banco;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.retry.*;
import io.github.resilience4j.bulkhead.*;
import org.springframework.context.annotation.*;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
@Configuration
class HttpConfiguration {
 @Bean @LoadBalanced RestClient.Builder accountRestClient(){var f=new SimpleClientHttpRequestFactory();f.setConnectTimeout(Duration.ofSeconds(1));f.setReadTimeout(Duration.ofSeconds(2));return RestClient.builder().requestFactory(f);}
 @Bean CircuitBreaker accountsCircuit(){return CircuitBreaker.of("cuentas",CircuitBreakerConfig.custom().slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50).waitDurationInOpenState(Duration.ofSeconds(8)).permittedNumberOfCallsInHalfOpenState(2).ignoreExceptions(HttpClientErrorException.class).build());}
 @Bean Retry accountsRetry(){return Retry.of("cuentas",RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(200)).retryExceptions(ResourceAccessException.class,HttpServerErrorException.class,IllegalStateException.class).build());}
 @Bean Bulkhead accountsBulkhead(){return Bulkhead.of("cuentas",BulkheadConfig.custom().maxConcurrentCalls(8).maxWaitDuration(Duration.ZERO).build());}
}
@Service
public class AccountsClient {
 private final RestClient client;private final CircuitBreaker breaker;private final Retry retry;private final Bulkhead bulkhead;
 public AccountsClient(RestClient.Builder builder,CircuitBreaker breaker,Retry retry,Bulkhead bulkhead){this.client=builder.build();this.breaker=breaker;this.retry=retry;this.bulkhead=bulkhead;}
 @SuppressWarnings("unchecked") public Map<String,Object> get(long id,String token){
  Supplier<Map<String,Object>> remote=()->client.get().uri("http://account-service/api/cuentas/{id}",id).headers(h->h.setBearerAuth(token)).retrieve().body(Map.class);
  return Bulkhead.decorateSupplier(bulkhead,CircuitBreaker.decorateSupplier(breaker,Retry.decorateSupplier(retry,remote))).get();
 }
 public Map<String,Object> status(){return Map.of("circuitBreaker",breaker.getState().name(),"failedCalls",breaker.getMetrics().getNumberOfFailedCalls(),"notPermittedCalls",breaker.getMetrics().getNumberOfNotPermittedCalls(),"retryMaxAttempts",retry.getRetryConfig().getMaxAttempts(),"retriedFailed",retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt(),"bulkheadMaxConcurrentCalls",bulkhead.getMetrics().getMaxAllowedConcurrentCalls(),"bulkheadAvailable",bulkhead.getMetrics().getAvailableConcurrentCalls());}
}
