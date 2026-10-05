package cl.duoc.bank.mobile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootApplication
public class MobileBffApplication {
    public static void main(String[] args) {
        SpringApplication.run(MobileBffApplication.class, args);
    }

    @Bean(destroyMethod = "close")
    ExecutorService aggregationExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
