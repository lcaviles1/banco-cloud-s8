package cl.duoc.bank.atm;
import java.time.*;
import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
@Configuration
public class OAuthClientConfig {
 private String cached;private Instant expires=Instant.EPOCH;
 @Value("${BFF_CLIENT_SECRET}") private String secret;
 @Value("${BFF_OAUTH_SCOPE}") private String scope;
 @Bean RestClientCustomizer credentials(){return b->b.requestInterceptor((req,body,next)->{req.getHeaders().setBearerAuth(token());return next.execute(req,body);});}
 private synchronized String token(){
  if(Instant.now().isBefore(expires))return cached;
  var form=new LinkedMultiValueMap<String,String>();form.add("grant_type","client_credentials");form.add("scope",scope);
  var result=RestClient.create().post().uri("http://auth-server:9000/oauth2/token").headers(h->h.setBasicAuth("bff-service",secret)).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
  cached=(String)result.get("access_token");expires=Instant.now().plusSeconds(((Number)result.get("expires_in")).longValue()-30);return cached;
 }
}
