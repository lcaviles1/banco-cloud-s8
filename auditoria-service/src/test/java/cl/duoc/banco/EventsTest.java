package cl.duoc.banco;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import static org.junit.jupiter.api.Assertions.*;
class EventsTest {
 @Test void repeatedDeliveryIsStoredOnce() throws Exception {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);var db=new JdbcTemplate(ds);var c=new EventsController(db,new ObjectMapper());
  String e="{\"eventId\":\""+UUID.randomUUID()+"\",\"type\":\"OperacionRegistrada\",\"version\":1}";
  c.receive(e);c.receive(e);assertEquals(1,db.queryForObject("select count(*) from eventos",Integer.class));
  assertThrows(IllegalArgumentException.class,()->c.receive("{\"eventId\":\"invalid\"}"));
 }
}
