package cl.duoc.banco;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class SecurityConfig {
 @Bean SecurityFilterChain security(HttpSecurity h) throws Exception {
 return h.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
 .authorizeHttpRequests(a->a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll().requestMatchers("/actuator/health/**").permitAll().requestMatchers(org.springframework.http.HttpMethod.POST,"/api/operaciones").hasAuthority("SCOPE_operaciones.write").requestMatchers("/api/operaciones/**","/api/resiliencia").hasAuthority("SCOPE_operaciones.read").anyRequest().denyAll())
 .oauth2ResourceServer(o->o.jwt(Customizer.withDefaults())).build();
 }
}
