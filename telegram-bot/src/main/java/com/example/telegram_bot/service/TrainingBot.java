package com.example.telegram_bot.service;

import com.example.telegram_bot.config.BotConfig;
import com.example.telegram_bot.data.UserData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;

@Component
@Slf4j
public class TrainingBot extends TelegramLongPollingBot {

    private final BotConfig config;
    private final MotivationGenerator motivationGenerator;
    private final UserData userData;

    public TrainingBot(BotConfig config, MotivationGenerator motivationGenerator, UserData userData) {
        super(config.getBotToken());
        this.config = config;
        this.motivationGenerator = motivationGenerator;
        this.userData = userData;
    }

    @Override
    public String getBotUsername() {
        return config.getBotUsername();
    }

    @Override
    public void onUpdateReceived(Update update) {
        // Обработка текстовых сообщений
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if ("/start".equals(text)) {
                sendMainMenu(chatId);
            } else {
                handlePlainText(chatId, text);
            }
        }

        // Обработка нажатий на кнопки
        if (update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            switch (data) {
                case "TOTAL_TIME":
                    String totalTime = userData.getTotalTrainingTime(chatId);
                    sendMessage(chatId, totalTime);
                    break;
                case "TOTAL_WEIGHT":
                    Optional<Double> totalWeight = userData.getTotalWeight(chatId);
                    String weightMsg = totalWeight
                            .map(w -> String.format("⚖️ Общий вес за все тренировки: %.1f кг", w))
                            .orElse("⚖️ Вес не указан ни в одной тренировке.");
                    sendMessage(chatId, weightMsg);
                    break;
                case "LAST_TRAINING":
                    Optional<String> lastInfo = userData.getLastTrainingInfo(chatId);
                    sendMessage(chatId, lastInfo.orElse("📅 Нет данных о последней тренировке."));
                    break;
                case "MOTIVATE":
                    sendMessage(chatId, motivationGenerator.getRandomMotivation());
                    break;
                case "NEW_TRAINING":
                    userData.setTrainingState(chatId, UserData.State.AWAITING_MUSCLE_GROUP);
                    sendMessage(chatId, "Начинаем новую тренировку. Введи название группы мышц (например, Ноги или Спина):");
                    break;
            }

            // Убираем кнопки после нажатия
            try {
                execute(EditMessageReplyMarkup.builder()
                        .chatId(chatId)
                        .messageId(update.getCallbackQuery().getMessage().getMessageId())
                        .replyMarkup(null)
                        .build());
            } catch (TelegramApiException e) {
                log.warn("Не удалось убрать кнопки", e);
            }
        }
    }

    private void handlePlainText(long chatId, String messageText) {
        UserData.State currentState = userData.getTrainingState(chatId);
        String responseText;

        switch (currentState) {
            case AWAITING_MUSCLE_GROUP:
                userData.saveMuscleGroup(chatId, messageText);
                userData.setTrainingState(chatId, UserData.State.AWAITING_DURATION);
                responseText = "Группа мышц сохранена: " + messageText + ". Теперь введи общую **продолжительность** тренировки в часах (например, 1.5):";
                break;
            case AWAITING_DURATION:
                try {
                    double duration = Double.parseDouble(messageText);
                    userData.saveDuration(chatId, duration);
                    String durationInfo = userData.getDurationInOtherUnits(duration);
                    userData.setTrainingState(chatId, UserData.State.AWAITING_WEIGHT);
                    responseText = "Продолжительность сохранена (" + durationInfo + "). Введи **вес** (кг) или 'Нет':";
                } catch (NumberFormatException e) {
                    responseText = "Ошибка: введи продолжительность в виде числа (например, 1.5).";
                }
                break;
            case AWAITING_WEIGHT:
                if (!"Нет".equalsIgnoreCase(messageText)) {
                    try {
                        double weight = Double.parseDouble(messageText);
                        userData.saveWeight(chatId, weight);
                    } catch (NumberFormatException e) {
                        responseText = "Ошибка: введи вес в виде числа (например, 75.5) или 'Нет'.";
                        sendMessage(chatId, responseText);
                        return;
                    }
                }
                userData.finishTraining(chatId);
                userData.setTrainingState(chatId, UserData.State.IDLE);
                responseText = "✅ Тренировка сохранена!\n" + userData.getTotalTrainingTime(chatId);
                break;
            case IDLE:
            default:
                responseText = "Напиши /start, чтобы открыть меню.";
                break;
        }
        sendMessage(chatId, responseText);
    }

    private void sendMainMenu(long chatId) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText("📊 *Твой тренировочный бот*\nВыбери действие:");
        msg.setParseMode("Markdown");
        msg.setReplyMarkup(createMainMenu());
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки меню", e);
        }
    }

    private InlineKeyboardMarkup createMainMenu() {
        // Строка 1
        List<InlineKeyboardButton> row1 = Arrays.asList(
                new InlineKeyboardButton().setText("⏱ Общее время").setCallbackData("TOTAL_TIME"),
                new InlineKeyboardButton().setText("⚖️ Вес").setCallbackData("TOTAL_WEIGHT")
        );

        // Строка 2
        List<InlineKeyboardButton> row2 = Arrays.asList(
                new InlineKeyboardButton().setText("📅 Последняя тренировка").setCallbackData("LAST_TRAINING"),
                new InlineKeyboardButton().setText("💪 Мотивация").setCallbackData("MOTIVATE")
        );

        // Строка 3
        List<InlineKeyboardButton> row3 = Collections.singletonList(
                new InlineKeyboardButton().setText("➕ Новая тренировка").setCallbackData("NEW_TRAINING")
        );

        // Собираем клавиатуру
        List<List<InlineKeyboardButton>> keyboard = Arrays.asList(row1, row2, row3);
        return new InlineKeyboardMarkup(keyboard);
    }

    private void sendMessage(long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(text);
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения", e);
        }
    }
}