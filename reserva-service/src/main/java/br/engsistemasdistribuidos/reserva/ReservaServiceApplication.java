package br.engsistemasdistribuidos.reserva;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ReservaServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ReservaServiceApplication.class, args);
    }
}
