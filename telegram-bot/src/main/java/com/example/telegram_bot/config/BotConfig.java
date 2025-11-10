package com.example.telegram_bot.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class BotConfig {

    @Value("${bot.username}")
    private String botUsername;

    @Value("${bot.token}")
    private String botToken;
}

