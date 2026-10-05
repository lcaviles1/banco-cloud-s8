package cl.duoc.bank.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootApplication
public class WebBffApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebBffApplication.class, args);
    }

    @Bean(destroyMethod = "close")
    ExecutorService aggregationExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
