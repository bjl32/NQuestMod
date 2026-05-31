package cn.zbx1425.nquestmod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

public class QuestEventLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger("NQuestMod");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String FILE_PREFIX = "quest_events-";
    private static final String FILE_SUFFIX = ".log";

    private final Path logDir;
    private final ZoneId zoneId;
    private final int retentionDays;
    private final LongSupplier unixTimeSeconds;
    private LocalDate lastCleanupDate;

    public QuestEventLogger(Path logDir, ZoneId zoneId, int retentionDays) {
        this(logDir, zoneId, retentionDays, () -> System.currentTimeMillis() / 1000L);
    }

    QuestEventLogger(Path logDir, ZoneId zoneId, int retentionDays, LongSupplier unixTimeSeconds) {
        this.logDir = logDir;
        this.zoneId = zoneId;
        this.retentionDays = retentionDays;
        this.unixTimeSeconds = unixTimeSeconds;
        cleanupOldFiles(currentDate());
    }

    public void logStart(String playerName, String questId) {
        long timestamp = unixTimeSeconds.getAsLong();
        append("Start " + prefix(playerName, timestamp, questId), timestamp);
    }

    public void logProgress(String playerName, String questId, int currentStep, int totalSteps) {
        long timestamp = unixTimeSeconds.getAsLong();
        append("Progress " + prefix(playerName, timestamp, questId)
                + " " + currentStep + "/" + totalSteps, timestamp);
    }

    public void logFail(String playerName, String questId, String failReason) {
        long timestamp = unixTimeSeconds.getAsLong();
        append("Fail " + prefix(playerName, timestamp, questId)
                + " " + token(failReason), timestamp);
    }

    public void logFinish(String playerName, String questId) {
        long timestamp = unixTimeSeconds.getAsLong();
        append("Finish " + prefix(playerName, timestamp, questId), timestamp);
    }

    public void logAbort(String playerName, String questId) {
        long timestamp = unixTimeSeconds.getAsLong();
        append("Abort " + prefix(playerName, timestamp, questId), timestamp);
    }

    private synchronized void append(String line, long timestamp) {
        try {
            LocalDate today = dateFor(timestamp);
            cleanupOldFiles(today);
            Files.createDirectories(logDir);
            Files.writeString(logFile(today), line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.warn("Failed to write quest event log", e);
        }
    }

    private String prefix(String playerName, long timestamp, String questId) {
        return token(playerName) + " " + timestamp
                + " " + token(zoneId.getId()) + " " + token(questId);
    }

    private LocalDate currentDate() {
        return dateFor(unixTimeSeconds.getAsLong());
    }

    private LocalDate dateFor(long timestamp) {
        return Instant.ofEpochSecond(timestamp).atZone(zoneId).toLocalDate();
    }

    private Path logFile(LocalDate date) {
        return logDir.resolve(FILE_PREFIX + FILE_DATE.format(date) + FILE_SUFFIX);
    }

    private void cleanupOldFiles(LocalDate today) {
        if (retentionDays <= 0 || today.equals(lastCleanupDate)) return;
        LocalDate cutoff = today.minusDays(retentionDays - 1L);
        try {
            Files.createDirectories(logDir);
            try (Stream<Path> files = Files.list(logDir)) {
                files.filter(Files::isRegularFile)
                        .forEach(path -> {
                            LocalDate fileDate = fileDate(path);
                            if (fileDate == null || !fileDate.isBefore(cutoff)) return;
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                LOGGER.warn("Failed to delete old quest event log {}", path, e);
                            }
                        });
            }
            lastCleanupDate = today;
        } catch (IOException e) {
            LOGGER.warn("Failed to clean old quest event logs", e);
        }
    }

    private static LocalDate fileDate(Path path) {
        String filename = path.getFileName().toString();
        if (!filename.startsWith(FILE_PREFIX) || !filename.endsWith(FILE_SUFFIX)) return null;
        String date = filename.substring(FILE_PREFIX.length(), filename.length() - FILE_SUFFIX.length());
        try {
            return LocalDate.parse(date, FILE_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String token(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.trim().replaceAll("\\s+", "_");
    }
}
