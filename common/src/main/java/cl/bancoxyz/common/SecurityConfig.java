package cl.bancoxyz.common;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
@Configuration @EnableMethodSecurity
public class SecurityConfig {
 @Bean JwtDecoder decoder(@Value("${bank.issuer}") String issuer,@Value("${bank.jwks}") String jwks,@Value("${bank.channel:}") String channel){
  var decoder=NimbusJwtDecoder.withJwkSetUri(jwks).build();
  OAuth2TokenValidator<Jwt> claims=j->{
   boolean valid=j.getAudience().contains("bank-api") && (channel.isBlank() || channel.equals(j.getClaimAsString("channel")));
   return valid?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Audiencia o canal incorrecto",null));
  };
  decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),claims));return decoder;
 }
 @Bean SecurityFilterChain security(HttpSecurity h) throws Exception {
  return h.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
  .authorizeHttpRequests(a->a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll().requestMatchers("/actuator/health/**").permitAll().requestMatchers("/actuator/**").hasAuthority("SCOPE_ops.read").anyRequest().authenticated())
  .oauth2ResourceServer(o->o.jwt(j->{})).build();
 }
 public static String owner(Jwt j){String id=j.getClaimAsString("customer_id");if(id==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Sin identidad de cliente");return id;}
 public static void owned(Jwt j,String owner){if(!owner(j).equals(owner))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Recurso de otro cliente");}
}
