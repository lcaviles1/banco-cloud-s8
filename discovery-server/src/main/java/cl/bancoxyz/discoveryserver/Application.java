package cl.bancoxyz.discoveryserver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@org.springframework.cloud.netflix.eureka.server.EnableEurekaServer
@SpringBootApplication(scanBasePackages={"cl.bancoxyz.discoveryserver", "cl.bancoxyz.common"})
public class Application { public static void main(String[] args) {SpringApplication.run(Application.class,args);} }