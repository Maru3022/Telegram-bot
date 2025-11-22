package com.example.telegram_bot.config;

import com.example.telegram_bot.service.TrainingBot;
import javax.annotation.PostConstruct; // ← ИСПРАВЛЕНО: javax, а не jakarta
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
@RequiredArgsConstructor
public class BotInitializer {

    private final TrainingBot trainingBot;

    @PostConstruct
    public void init() throws Exception {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(trainingBot);
    }
}