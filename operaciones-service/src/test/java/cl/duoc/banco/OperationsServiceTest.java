package cl.duoc.banco;
import java.math.BigDecimal;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class OperationsServiceTest {
 JdbcTemplate db;OperationsService service;
 @BeforeEach void setup(){var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);db=new JdbcTemplate(ds);service=new OperationsService(db,new ObjectMapper());}
 @Test void sameKeyCreatesOneOperationAndOneEvent() throws Exception {
  var req=new OperationsController.Request(106,"DEPOSITO",new BigDecimal("100.00"));
  var a=service.register("same-key-123",req);var b=service.register("same-key-123",req);
  assertEquals(a.get("ID"),b.get("ID"));assertEquals(1,db.queryForObject("select count(*) from operaciones",Integer.class));assertEquals(1,db.queryForObject("select count(*) from outbox",Integer.class));
 }
 @Test void differentPayloadWithSameKeyIsConflict() throws Exception {
  service.register("same-key-123",new OperationsController.Request(106,"DEPOSITO",new BigDecimal("100.00")));
  assertEquals(409,assertThrows(ResponseStatusException.class,()->service.register("same-key-123",new OperationsController.Request(106,"DEPOSITO",new BigDecimal("200.00")))).getStatusCode().value());
 }
}
