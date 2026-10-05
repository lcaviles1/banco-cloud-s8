package cl.duoc.banco;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
@org.springframework.cloud.config.server.EnableConfigServer
public class Application { public static void main(String[] args) { SpringApplication.run(Application.class,args); } }
