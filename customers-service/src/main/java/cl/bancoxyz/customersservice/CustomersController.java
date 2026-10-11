package cl.bancoxyz.customersservice;

import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import cl.bancoxyz.common.*;
@RestController @RequestMapping("/api/customers")
public class CustomersController {
 public record Profile(@NotBlank @Size(max=120) String name,@NotBlank @Email @Size(max=150) String email){}
 private final JdbcTemplate db;public CustomersController(JdbcTemplate d){db=d;}
 @GetMapping("/me") @PreAuthorize("hasAuthority('SCOPE_customers.read')") public Object me(@AuthenticationPrincipal Jwt j){return db.queryForMap("select id,name,email,status from customers where id=?",SecurityConfig.owner(j));}
 @PutMapping("/me") @PreAuthorize("hasAuthority('SCOPE_customers.write')") @Transactional public Object update(@Valid @RequestBody Profile p,@AuthenticationPrincipal Jwt j){String id=SecurityConfig.owner(j);db.update("insert into customers(id,name,email,status) values(?,?,?,'ACTIVE') on conflict(id) do update set name=excluded.name,email=excluded.email",id,p.name(),p.email());return me(j);}
 @DeleteMapping("/me") @PreAuthorize("hasAuthority('SCOPE_customers.write')") public Object deactivate(@AuthenticationPrincipal Jwt j){db.update("update customers set status='INACTIVE' where id=?",SecurityConfig.owner(j));return Map.of("status","INACTIVE");}
 @GetMapping("/events") @PreAuthorize("hasAuthority('SCOPE_ops.read')") public Object events(){return Map.of("events",db.queryForList("select event_id,channel,received from inbox order by received desc limit 30"),"count",db.queryForObject("select count(*) from inbox",Integer.class));}
}
