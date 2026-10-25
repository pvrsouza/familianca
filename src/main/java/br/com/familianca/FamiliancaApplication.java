package br.com.familianca;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FamiliancaApplication {

    public static void main(String[] args) {
        SpringApplication.run(FamiliancaApplication.class, args);
    }
}
