package cl.duoc.bank.atm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootApplication
public class AtmBffApplication {
    public static void main(String[] args) {
        SpringApplication.run(AtmBffApplication.class, args);
    }

    @Bean(destroyMethod = "close")
    ExecutorService aggregationExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
