package cl.duoc.banco;
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
 @Bean RegisteredClientRepository clients(@Value("${BANK_CLIENT_SECRET}") String bank,@Value("${READER_CLIENT_SECRET}") String reader,@Value("${BFF_CLIENT_SECRET}") String bff) {
  var enc=new BCryptPasswordEncoder();
  var full=RegisteredClient.withId("bank-cli").clientId("bank-cli").clientSecret("{bcrypt}"+enc.encode(bank)).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).scope("cuentas.read").scope("operaciones.read").scope("operaciones.write").scope("auditoria.read").scope("movimientos.read").scope("bff.read").scope("atm.write").tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofMinutes(5)).build()).build();
  var read=RegisteredClient.withId("bank-reader").clientId("bank-reader").clientSecret("{bcrypt}"+enc.encode(reader)).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).scope("cuentas.read").scope("operaciones.read").tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofMinutes(5)).build()).build();
  var internal=RegisteredClient.withId("bff-service").clientId("bff-service").clientSecret("{bcrypt}"+enc.encode(bff)).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).scope("cuentas.read").scope("movimientos.read").scope("cuentas.write").tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofMinutes(5)).build()).build();
  return new InMemoryRegisteredClientRepository(full,read,internal);
 }
 @Bean AuthorizationServerSettings settings(@Value("${OAUTH_ISSUER:http://localhost:9000}") String issuer){return AuthorizationServerSettings.builder().issuer(issuer).build();}
 @Bean OAuth2TokenCustomizer<JwtEncodingContext> audience(){return c->c.getClaims().audience(List.of("banco-api"));}
 @Bean JWKSource<SecurityContext> keys(@Value("${KEY_FILE:/data/signing.jwk}") String file) throws Exception {
  Path path=Path.of(file);RSAKey key;
  if(Files.exists(path)){key=RSAKey.parse(Files.readString(path));}
  else {var g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);var p=g.generateKeyPair();key=new RSAKey.Builder((java.security.interfaces.RSAPublicKey)p.getPublic()).privateKey(p.getPrivate()).keyID(UUID.randomUUID().toString()).build();Files.createDirectories(path.getParent());Files.writeString(path,key.toJSONString(),StandardOpenOption.CREATE_NEW);try{Files.setPosixFilePermissions(path,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));}catch(UnsupportedOperationException ignored){}}
  return new ImmutableJWKSet<>(new JWKSet(key));
 }
}
