package cl.duoc.banco;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class SecurityConfig {
 @Bean UserDetailsService users(@Value("${INFRA_PASSWORD}") String secret) {return new InMemoryUserDetailsManager(User.withUsername("infra").password(new BCryptPasswordEncoder().encode(secret)).roles("INFRA").build());}
 @Bean org.springframework.security.crypto.password.PasswordEncoder encoder(){return new BCryptPasswordEncoder();}
 @Bean SecurityFilterChain security(HttpSecurity h) throws Exception {return h.csrf(c->c.disable()).authorizeHttpRequests(a->a.requestMatchers("/actuator/health/**").permitAll().anyRequest().authenticated()).httpBasic(Customizer.withDefaults()).build();}
}
