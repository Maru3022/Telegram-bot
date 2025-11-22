 package com.example.telegram_bot.data;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserData {

    // Временное хранение состояния диалога
    private final Map<Long, State> userStates = new ConcurrentHashMap<>();
    // Временное хранение данных тренировок
    private final Map<Long, List<TrainingEntry>> userTrainingHistory = new ConcurrentHashMap<>();
    // Временное хранение текущей тренировки
    private final Map<Long, TrainingEntry> currentTraining = new ConcurrentHashMap<>();

    public enum State {
        IDLE,                   // Ожидание команды
        AWAITING_MUSCLE_GROUP,  // Ожидание группы мышц
        AWAITING_DURATION,      // Ожидание продолжительности
        AWAITING_WEIGHT         // Ожидание веса
        // AWAITING_TOTAL_TIME  // Ожидание общего тренировочного времени (по месяцам/годам)
    }

    // Внутренний класс для записи тренировки
    private static class TrainingEntry {
        public LocalDateTime startTime = LocalDateTime.now();
        public String muscleGroup;
        public double durationHours;
        public Optional<Double> weight = Optional.empty(); // Задача: добавить подсчет веса
    }

    // --- Методы для работы с состоянием ---
    public State getTrainingState(long chatId) {
        return userStates.getOrDefault(chatId, State.IDLE);
    }

    public void setTrainingState(long chatId, State state) {
        userStates.put(chatId, state);
        if (state == State.AWAITING_MUSCLE_GROUP) {
            currentTraining.put(chatId, new TrainingEntry()); // Начинаем новую запись
        }
    }

    // --- Методы для сохранения данных ---
    public void saveMuscleGroup(long chatId, String group) {
        currentTraining.get(chatId).muscleGroup = group;
    }

    public void saveDuration(long chatId, double duration) {
        currentTraining.get(chatId).durationHours = duration;
    }

    public void saveWeight(long chatId, double weight) {
        currentTraining.get(chatId).weight = Optional.of(weight);
    }

    public void finishTraining(long chatId) {
        TrainingEntry entry = currentTraining.remove(chatId);
        if (entry != null) {
            userTrainingHistory.computeIfAbsent(chatId, k -> new ArrayList<>()).add(entry);
        }
    }

    // --- Методы для отчетов (Задачи) ---

    // Задача: Выводит среднее время тренировки
    public Optional<String> getAverageTrainingTime(long chatId) {
        List<TrainingEntry> history = userTrainingHistory.get(chatId);
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        double totalDuration = history.stream().mapToDouble(e -> e.durationHours).sum();
        double average = totalDuration / history.size();
        return Optional.of(String.format("Среднее время тренировки: %.2f часа.", average));
    }

    // Задача: добавить подсчет времени (общее в часах, потом в днях или месяцах или годах)
    public String getTotalTrainingTime(long chatId) {
        List<TrainingEntry> history = userTrainingHistory.get(chatId);
        if (history == null || history.isEmpty()) {
            return "Общее время тренировок: 0 часов.";
        }
        double totalDurationHours = history.stream().mapToDouble(e -> e.durationHours).sum();

        if (totalDurationHours < 24) {
            return String.format("Общее время тренировок: %.2f часа.", totalDurationHours);
        } else if (totalDurationHours < 24 * 30) {
            double days = totalDurationHours / 24.0;
            return String.format("Общее время тренировок: %.2f дней.", days);
        } else {
            double months = totalDurationHours / (24.0 * 30.0);
            return String.format("Общее время тренировок: %.2f месяцев.", months);
        }
    }

    // Вспомогательный метод для отображения длительности в других единицах
    public String getDurationInOtherUnits(double durationHours) {
        if (durationHours < 1) {
            return String.format("%.0f минут", durationHours * 60);
        } else if (durationHours > 24) {
            return String.format("%.2f дней", durationHours / 24.0);
        }
        return String.format("%.2f часа", durationHours);
    }

    // Получить общий вес за все тренировки
    public Optional<Double> getTotalWeight(long chatId) {
        List<TrainingEntry> history = userTrainingHistory.get(chatId);
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        double total = history.stream()
                .mapToDouble(e -> e.weight.orElse(0.0))
                .sum();
        return total > 0 ? Optional.of(total) : Optional.empty();
    }

    // Получить информацию о последней тренировке
    public Optional<String> getLastTrainingInfo(long chatId) {
        List<TrainingEntry> history = userTrainingHistory.get(chatId);
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        TrainingEntry last = history.get(history.size() - 1);
        StringBuilder sb = new StringBuilder();
        sb.append("📅 **Последняя тренировка**\n");
        sb.append("Группа мышц: ").append(last.muscleGroup).append("\n");
        sb.append("Продолжительность: ").append(getDurationInOtherUnits(last.durationHours)).append("\n");
        if (last.weight.isPresent()) {
            sb.append("Вес: ").append(last.weight.get()).append(" кг");
        } else {
            sb.append("Вес: не указан");
        }
        return Optional.of(sb.toString());
    }
}