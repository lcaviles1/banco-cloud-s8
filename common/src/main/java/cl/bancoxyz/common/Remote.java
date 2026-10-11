package cl.bancoxyz.common;

import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.retry.*;
import io.github.resilience4j.bulkhead.*;
@Component
public class Remote {
 private final RestClient http;private final CircuitBreakerRegistry cb;private final RetryRegistry retry;private final BulkheadRegistry bh;
 public Remote(RestClient.Builder b,CircuitBreakerRegistry cb,RetryRegistry r,BulkheadRegistry h){http=b.build();this.cb=cb;retry=r;bh=h;}
 private <T>T call(String name,Supplier<T>s){return Bulkhead.decorateSupplier(bh.bulkhead(name),CircuitBreaker.decorateSupplier(cb.circuitBreaker(name),Retry.decorateSupplier(retry.retry(name),s))).get();}
 public Map get(String service,String path,String token){return call(service,()->http.get().uri("http://"+service+path).headers(h->h.setBearerAuth(token)).retrieve().body(Map.class));}
 public Map post(String service,String path,String token,Object body,String key){return call(service,()->http.post().uri("http://"+service+path).headers(h->{h.setBearerAuth(token);h.set("Idempotency-Key",key);}).body(body).retrieve().body(Map.class));}
 public List<Map<String,Object>> status(){return cb.getAllCircuitBreakers().stream().map(c->Map.<String,Object>of("service",c.getName(),"state",c.getState().name(),"failures",c.getMetrics().getNumberOfFailedCalls())).toList();}
}
