package cl.bancoxyz.paymentsservice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication(scanBasePackages={"cl.bancoxyz.paymentsservice", "cl.bancoxyz.common"})
public class Application { public static void main(String[] args) {SpringApplication.run(Application.class,args);} }