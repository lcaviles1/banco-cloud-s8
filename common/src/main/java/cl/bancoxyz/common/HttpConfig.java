package cl.bancoxyz.common;

import java.time.Duration;
import org.springframework.context.annotation.*;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.retry.*;
import io.github.resilience4j.bulkhead.*;
@Configuration
public class HttpConfig {
 @Bean @LoadBalanced RestClient.Builder bankRest(){var f=new SimpleClientHttpRequestFactory();f.setConnectTimeout(Duration.ofSeconds(1));f.setReadTimeout(Duration.ofSeconds(2));return RestClient.builder().requestFactory(f);}
 @Bean CircuitBreakerRegistry circuitRegistry(){return CircuitBreakerRegistry.of(CircuitBreakerConfig.custom().slidingWindowSize(6).minimumNumberOfCalls(3).failureRateThreshold(50).waitDurationInOpenState(Duration.ofSeconds(8)).ignoreExceptions(org.springframework.web.client.HttpClientErrorException.class).build());}
 @Bean RetryRegistry retryRegistry(){return RetryRegistry.of(RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(200)).retryExceptions(org.springframework.web.client.ResourceAccessException.class,org.springframework.web.client.HttpServerErrorException.class).build());}
 @Bean BulkheadRegistry bulkheadRegistry(){return BulkheadRegistry.of(BulkheadConfig.custom().maxConcurrentCalls(12).maxWaitDuration(Duration.ZERO).build());}
}
