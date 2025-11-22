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
        super(); // новая версия требует пустой конструктор
        this.config = config;
        this.motivationGenerator = motivationGenerator;
        this.userData = userData;
    }

    @Override
    public String getBotUsername() {
        return config.getBotUsername();
    }

    @Override
    public String getBotToken() {
        return config.getBotToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            // ------------------------------
            // Обработка текстовых сообщений
            // ------------------------------
            if (update.hasMessage() && update.getMessage().hasText()) {
                String text = update.getMessage().getText().trim();
                long chatId = update.getMessage().getChatId();

                if ("/start".equalsIgnoreCase(text)) {
                    sendMainMenu(chatId);
                } else {
                    handlePlainText(chatId, text);
                }
            }

            // ------------------------------
            // Обработка нажатий на кнопки
            // ------------------------------
            if (update.hasCallbackQuery()) {

                if (update.getCallbackQuery().getMessage() == null) return;

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
                        Optional<String> last = userData.getLastTrainingInfo(chatId);
                        sendMessage(chatId, last.orElse("📅 Нет данных о последней тренировке."));
                        break;

                    case "MOTIVATE":
                        sendMessage(chatId, motivationGenerator.getRandomMotivation());
                        break;

                    case "NEW_TRAINING":
                        userData.setTrainingState(chatId, UserData.State.AWAITING_MUSCLE_GROUP);
                        sendMessage(chatId, "Начинаем новую тренировку. Введи группу мышц:");
                        break;
                }

                // удаление кнопок
                try {
                    EditMessageReplyMarkup edit = new EditMessageReplyMarkup();
                    edit.setChatId(String.valueOf(chatId));
                    edit.setMessageId(update.getCallbackQuery().getMessage().getMessageId());
                    edit.setReplyMarkup(null);
                    execute(edit);
                } catch (TelegramApiException e) {
                    log.warn("Не удалось убрать кнопки", e);
                }
            }

        } catch (Exception ex) {
            log.error("Ошибка при обработке update", ex);
        }
    }

    // -----------------------------
    // Логика текстовых сообщений
    // -----------------------------
    private void handlePlainText(long chatId, String msg) {
        UserData.State state = userData.getTrainingState(chatId);
        String answer;

        switch (state) {
            case AWAITING_MUSCLE_GROUP:
                userData.saveMuscleGroup(chatId, msg);
                userData.setTrainingState(chatId, UserData.State.AWAITING_DURATION);
                answer = "Группа мышц сохранена. Теперь введи продолжительность (в часах, например 1.5):";
                break;

            case AWAITING_DURATION:
                try {
                    double duration = Double.parseDouble(msg.replace(",", "."));
                    userData.saveDuration(chatId, duration);
                    userData.setTrainingState(chatId, UserData.State.AWAITING_WEIGHT);
                    answer = "Продолжительность сохранена. Теперь введи вес (кг) или 'Нет':";
                } catch (NumberFormatException e) {
                    answer = "Ошибка: введи число, например 1.5";
                }
                break;

            case AWAITING_WEIGHT:
                if (!msg.equalsIgnoreCase("нет")) {
                    try {
                        double weight = Double.parseDouble(msg.replace(",", "."));
                        userData.saveWeight(chatId, weight);
                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "Ошибка: введи число или 'Нет'");
                        return;
                    }
                }
                userData.finishTraining(chatId);
                userData.setTrainingState(chatId, UserData.State.IDLE);
                answer = "✅ Тренировка сохранена!\n" + userData.getTotalTrainingTime(chatId);
                break;

            default:
                answer = "Напиши /start, чтобы открыть меню.";
        }

        sendMessage(chatId, answer);
    }

    // -----------------------------
    // Главное меню
    // -----------------------------
    private void sendMainMenu(long chatId) {
        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText("📊 *Тренировочный бот*\nВыбери действие:");
        msg.setParseMode("Markdown");

        msg.setReplyMarkup(createMainMenu());

        try {
            execute(msg);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки меню", e);
        }
    }

    private InlineKeyboardMarkup createMainMenu() {

        InlineKeyboardButton btnTime = new InlineKeyboardButton();
        btnTime.setText("⏱ Общее время");
        btnTime.setCallbackData("TOTAL_TIME");

        InlineKeyboardButton btnWeight = new InlineKeyboardButton();
        btnWeight.setText("⚖️ Вес");
        btnWeight.setCallbackData("TOTAL_WEIGHT");

        InlineKeyboardButton btnLast = new InlineKeyboardButton();
        btnLast.setText("📅 Последняя тренировка");
        btnLast.setCallbackData("LAST_TRAINING");

        InlineKeyboardButton btnMotivate = new InlineKeyboardButton();
        btnMotivate.setText("💪 Мотивация");
        btnMotivate.setCallbackData("MOTIVATE");

        InlineKeyboardButton btnNew = new InlineKeyboardButton();
        btnNew.setText("➕ Новая тренировка");
        btnNew.setCallbackData("NEW_TRAINING");

        List<List<InlineKeyboardButton>> keyboard = Arrays.asList(
                Arrays.asList(btnTime, btnWeight),
                Arrays.asList(btnLast, btnMotivate),
                Collections.singletonList(btnNew)
        );

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }

    // -----------------------------
    // Утилита отправки сообщений
    // -----------------------------
    private void sendMessage(long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText(text);
        msg.setParseMode("Markdown");

        try {
            execute(msg);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения", e);
        }
    }
}
