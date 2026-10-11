package cl.bancoxyz.accountsservice;

import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import cl.bancoxyz.common.*;
@RestController @RequestMapping("/api/accounts")
public class AccountsController {
 public record Open(@NotNull @Pattern(regexp="SAVINGS|LOAN") String type){}
 private final Accounts service;public AccountsController(Accounts s){service=s;}
 @GetMapping @PreAuthorize("hasAuthority('SCOPE_accounts.read')") public Object list(@AuthenticationPrincipal Jwt j){return Map.of("accounts",service.list(SecurityConfig.owner(j)));}
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('SCOPE_accounts.read')") public Object one(@PathVariable long id,@AuthenticationPrincipal Jwt j){var a=service.one(id);SecurityConfig.owned(j,a.get("owner").toString());return a;}
 @PostMapping @PreAuthorize("hasAuthority('SCOPE_accounts.write')") public Object open(@Valid @RequestBody Open r,@AuthenticationPrincipal Jwt j){return service.open(SecurityConfig.owner(j),r.type());}
 @PatchMapping("/{id}") @PreAuthorize("hasAuthority('SCOPE_accounts.write')") public Object maintain(@PathVariable long id,@Valid @RequestBody Open r,@AuthenticationPrincipal Jwt j){return service.maintain(id,SecurityConfig.owner(j),r.type(),false);}
 @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('SCOPE_accounts.write')") public Object close(@PathVariable long id,@AuthenticationPrincipal Jwt j){var a=service.one(id);return service.maintain(id,SecurityConfig.owner(j),a.get("type").toString(),true);}
 @PostMapping("/postings") @PreAuthorize("hasAuthority('SCOPE_accounts.post')") public Object post(@Valid @RequestBody Accounts.Posting p){return service.apply(p);}
}
