package cl.bancoxyz.authserver;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.*;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.*;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class AuthorizationConfig {
 @Bean @Order(1) SecurityFilterChain oauth(HttpSecurity h) throws Exception {
 var cfg=OAuth2AuthorizationServerConfigurer.authorizationServer();
 return h.securityMatcher(cfg.getEndpointsMatcher()).csrf(c->c.ignoringRequestMatchers(cfg.getEndpointsMatcher())).with(cfg,c->{}).authorizeHttpRequests(a->a.anyRequest().authenticated()).build();
 }
 @Bean @Order(2) SecurityFilterChain other(HttpSecurity h) throws Exception {return h.authorizeHttpRequests(a->a.requestMatchers("/actuator/health/**").permitAll().anyRequest().denyAll()).build();}
 @Bean RegisteredClientRepository clients(org.springframework.core.env.Environment env) {
 var enc=new BCryptPasswordEncoder();var all=new ArrayList<RegisteredClient>();
 Map<String,List<String>> scopes=new LinkedHashMap<>();
 scopes.put("web-cli",List.of("accounts.read","accounts.write","customers.read","customers.write","payments.read","payments.write"));
 scopes.put("mobile-cli",List.of("accounts.read","payments.read","payments.write"));
 scopes.put("atm-cli",List.of("accounts.read","payments.read","payments.write"));
 scopes.put("teller-cli",List.of("accounts.read","payments.read","payments.write","deposits.write"));
 scopes.put("operator-cli",List.of("ops.read","batch.read","batch.run"));
 scopes.put("reader-cli",List.of("accounts.read"));
 scopes.put("foreign-cli",List.of("accounts.read","payments.read","payments.write"));
 scopes.put("payments-worker",List.of("accounts.post"));
 for(var entry:scopes.entrySet()){String key=entry.getKey().toUpperCase(Locale.ROOT).replace('-','_')+"_SECRET";var c=RegisteredClient.withId(entry.getKey()).clientId(entry.getKey()).clientSecret("{bcrypt}"+enc.encode(env.getRequiredProperty(key))).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofMinutes(5)).build());entry.getValue().forEach(c::scope);all.add(c.build());}
 return new InMemoryRegisteredClientRepository(all);
 }
 @Bean AuthorizationServerSettings settings(@Value("${OAUTH_ISSUER:http://localhost:9000}") String issuer){return AuthorizationServerSettings.builder().issuer(issuer).build();}
 @Bean OAuth2TokenCustomizer<JwtEncodingContext> audience(){return c->{String id=c.getRegisteredClient().getClientId();c.getClaims().audience(List.of("bank-api"));if(!id.equals("operator-cli")&&!id.equals("payments-worker"))c.getClaims().claim("customer_id",id.equals("foreign-cli")?"C002":"C001");c.getClaims().claim("channel",id.equals("atm-cli")?"atm":id.equals("mobile-cli")?"mobile":"web");};}
 @Bean JWKSource<SecurityContext> keys(@Value("${KEY_FILE:/app/runtime/signing.jwk}") String file) throws Exception {
  Path path=Path.of(file);RSAKey key;
  if(Files.exists(path)){key=RSAKey.parse(Files.readString(path));}
  else {var g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);var p=g.generateKeyPair();key=new RSAKey.Builder((java.security.interfaces.RSAPublicKey)p.getPublic()).privateKey(p.getPrivate()).keyID(UUID.randomUUID().toString()).build();Files.createDirectories(path.getParent());Files.writeString(path,key.toJSONString(),StandardOpenOption.CREATE_NEW);try{Files.setPosixFilePermissions(path,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));}catch(UnsupportedOperationException ignored){}}
  return new ImmutableJWKSet<>(new JWKSet(key));
 }
}
