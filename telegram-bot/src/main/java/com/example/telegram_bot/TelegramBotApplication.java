package com.example.telegram_bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TelegramBotApplication {

    public static void main(String[] args) {
        // Запуск Spring Boot контекста
        SpringApplication.run(TelegramBotApplication.class, args);
    }
}