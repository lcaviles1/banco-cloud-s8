package cl.duoc.bank.account;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class SecurityConfig {
 @Bean SecurityFilterChain security(HttpSecurity h) throws Exception {
 return h.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
 .authorizeHttpRequests(a->a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll().requestMatchers("/actuator/health/**").permitAll().requestMatchers(org.springframework.http.HttpMethod.POST,"/internal/accounts/*/withdrawals").hasAuthority("SCOPE_cuentas.write").requestMatchers("/internal/**","/api/cuentas/**").hasAuthority("SCOPE_cuentas.read").anyRequest().denyAll())
 .oauth2ResourceServer(o->o.jwt(Customizer.withDefaults())).build();
 }
}
