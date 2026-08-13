package com.likeLion.backend.aiserver;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AiServerApplication {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure()
                .directory("/Users/wooddang-mac/Desktop/code/1. Study/Lionbackend/AiServer")
                .ignoreIfMissing()
                .load();
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

        SpringApplication.run(AiServerApplication.class, args);
    }

}
