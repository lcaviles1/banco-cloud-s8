package cl.bancoxyz.configserver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@org.springframework.cloud.config.server.EnableConfigServer
@SpringBootApplication(scanBasePackages={"cl.bancoxyz.configserver", "cl.bancoxyz.common"})
public class Application { public static void main(String[] args) {SpringApplication.run(Application.class,args);} }