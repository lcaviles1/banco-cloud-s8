package cl.bancoxyz.configserver;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
@Configuration public class InfrastructureSecurity {
 @Bean UserDetailsService users(org.springframework.core.env.Environment e){return new InMemoryUserDetailsManager(User.withUsername(e.getRequiredProperty("INFRA_USER")).password("{bcrypt}"+new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(e.getRequiredProperty("INFRA_PASSWORD"))).roles("INFRA").build());}
 @Bean SecurityFilterChain chain(HttpSecurity h)throws Exception{return h.csrf(c->c.disable()).authorizeHttpRequests(a->a.requestMatchers("/actuator/health/**").permitAll().anyRequest().authenticated()).httpBasic(b->{}).build();}
}