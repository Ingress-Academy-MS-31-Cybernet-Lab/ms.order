package az.ingress;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

import static org.springframework.boot.SpringApplication.run;

@EnableRetry
@SpringBootApplication
public class OrderApplication {

    public static void main(String[] args) {
        run(OrderApplication.class, args);
    }
}