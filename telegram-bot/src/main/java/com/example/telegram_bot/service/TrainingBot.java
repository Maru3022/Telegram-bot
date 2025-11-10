package com.example.telegram_bot.service;

import com.example.telegram_bot.config.BotConfig;
import com.example.telegram_bot.data.UserData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Optional;

// @Component делает его Spring Bean'ом, который будет зарегистрирован API
@Component
@Slf4j // Lombok аннотация для логгирования
public class TrainingBot extends TelegramLongPollingBot {

    private final BotConfig config;
    private final MotivationGenerator motivationGenerator;
    private final UserData userData; // Для хранения данных о пользователях/тренировках

    // Spring автоматически внедрит зависимости
    public TrainingBot(BotConfig config, MotivationGenerator motivationGenerator, UserData userData) {
        super(config.getBotToken()); // Устанавливаем токен
        this.config = config;
        this.motivationGenerator = motivationGenerator;
        this.userData = userData;
    }

    @Override
    public String getBotUsername() {
        return config.getBotUsername();
    }

    // Главный метод для обработки входящих сообщений
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (messageText.startsWith("/")) {
                handleCommand(chatId, messageText);
            } else {
                handlePlainText(chatId, messageText);
            }
        }
    }

    private void handleCommand(long chatId, String command) {
        String responseText;
        switch (command) {
            case "/start":
                responseText = "Привет! Я твой тренировочный бот. Чтобы начать, используй /new_training.";
                break;
            case "/motivate":
                // Задача: Сделать генерацию случайных мотивационных речей
                responseText = motivationGenerator.getRandomMotivation();
                break;
            case "/new_training":
                // Задача: Добавить группы мышц, подсчет времени, веса и т.д.
                // На этом этапе можно сохранить состояние пользователя (например, ожидаем ввод данных)
                userData.setTrainingState(chatId, UserData.State.AWAITING_MUSCLE_GROUP);
                responseText = "Начинаем новую тренировку. Введи название группы мышц (например, Ноги или Спина):";
                break;
            case "/average_time":
                // Задача: Выводит среднее время тренировки
                Optional<String> avgTime = userData.getAverageTrainingTime(chatId);
                responseText = avgTime.orElse("Пока нет данных о тренировках.");
                break;
            case "/help":
                responseText = "Доступные команды: /start, /motivate, /new_training, /average_time.";
                break;
            default:
                responseText = "Неизвестная команда. Попробуй /help.";
        }
        sendMessage(chatId, responseText);
    }

    private void handlePlainText(long chatId, String messageText) {
        // Логика обработки простого текста в зависимости от текущего состояния пользователя
        UserData.State currentState = userData.getTrainingState(chatId);
        String responseText;

        switch (currentState) {
            case AWAITING_MUSCLE_GROUP:
                // Здесь сохраняем группу мышц и переводим в следующее состояние
                userData.saveMuscleGroup(chatId, messageText);
                userData.setTrainingState(chatId, UserData.State.AWAITING_DURATION);
                responseText = "Группа мышц сохранена: " + messageText + ". Теперь введи общую **продолжительность** тренировки в часах (например, 1.5):";
                break;
            case AWAITING_DURATION:
                // Здесь обрабатываем продолжительность
                try {
                    double duration = Double.parseDouble(messageText);
                    userData.saveDuration(chatId, duration);
                    // Здесь можно реализовать конвертацию в дни/месяцы/годы (часы -> дни и т.д.)
                    String durationInfo = userData.getDurationInOtherUnits(duration);
                    userData.setTrainingState(chatId, UserData.State.AWAITING_WEIGHT);
                    responseText = "Продолжительность сохранена (" + durationInfo + "). Введи **вес** (кг) или 'Нет':";
                } catch (NumberFormatException e) {
                    responseText = "Ошибка: введи продолжительность в виде числа (например, 1.5).";
                }
                break;
            case AWAITING_WEIGHT:
                // Здесь обрабатываем вес
                if (!"Нет".equalsIgnoreCase(messageText)) {
                    try {
                        double weight = Double.parseDouble(messageText);
                        userData.saveWeight(chatId, weight);
                    } catch (NumberFormatException e) {
                        responseText = "Ошибка: введи вес в виде числа (например, 75.5) или 'Нет'.";
                        break;
                    }
                }
                // Завершение тренировки (или переход к следующему шагу - 'тренируешься ...')
                userData.finishTraining(chatId);
                userData.setTrainingState(chatId, UserData.State.IDLE);
                responseText = "Тренировка сохранена! Твоя общая продолжительность тренировок:\n" + userData.getTotalTrainingTime(chatId);
                break;
            case IDLE:
            default:
                responseText = "Я не понимаю эту команду. Используй /help для списка команд или /new_training, чтобы начать.";
                break;
        }
        sendMessage(chatId, responseText);
    }

    // Вспомогательный метод для отправки сообщений
    private void sendMessage(long chatId, String textToSend) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(textToSend);

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Error sending message to chat {}: {}", chatId, e.getMessage());
        }
    }

    // Задача: Сделать, чтобы его можно было добавить в группы и его могло бы использовать несколько человек.
    // Это уже реализовано, так как TelegramLongPollingBot обрабатывает обновления со всех чатов (включая группы).
    // Тебе нужно будет только убедиться, что логика (UserData) правильно использует Chat ID и User ID.
}