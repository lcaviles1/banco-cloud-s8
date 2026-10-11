package cl.bancoxyz.common;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.junit.jupiter.api.Assertions.*;
class OwnershipTest {
 Jwt token(String owner){return Jwt.withTokenValue("test").header("alg","RS256").claim("customer_id",owner).build();}
 @Test void customerCannotReadOtherCustomer(){assertDoesNotThrow(()->SecurityConfig.owned(token("C001"),"C001"));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->SecurityConfig.owned(token("C001"),"C002"));}
 @Test void technicalTokenWithoutOwnerDenied(){var j=Jwt.withTokenValue("test").header("alg","RS256").claim("scope","accounts.post").build();assertThrows(org.springframework.web.server.ResponseStatusException.class,()->SecurityConfig.owner(j));}
}
