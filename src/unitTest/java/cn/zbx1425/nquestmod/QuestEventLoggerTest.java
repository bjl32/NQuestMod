package cn.zbx1425.nquestmod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;

public class QuestEventLoggerTest {

    public static void main(String[] args) throws Exception {
        writesDailyAuditLines();
        usesSameTimestampForLineAndFileDate();
        deletesFilesOutsideRetentionWindow();
        keepsFilesWhenRetentionDisabled();
    }

    private static void writesDailyAuditLines() throws Exception {
        Path tempDir = Files.createTempDirectory("nquest-event-log-test");
        QuestEventLogger logger = new QuestEventLogger(tempDir, ZoneId.of("UTC"), 7, () -> 1780213651L);

        logger.logStart("biggynb", "quest one");
        logger.logProgress("biggynb", "quest one", 3, 30);
        logger.logFail("biggynb", "quest one", "OverSpeedCriterion");
        logger.logFinish("biggynb", "quest one");
        logger.logAbort("biggynb", "quest one");

        List<String> lines = Files.readAllLines(tempDir.resolve("quest_events-2026-05-31.log"));
        assertEquals(List.of(
                "Start biggynb 1780213651 UTC quest_one",
                "Progress biggynb 1780213651 UTC quest_one 3/30",
                "Fail biggynb 1780213651 UTC quest_one OverSpeedCriterion",
                "Finish biggynb 1780213651 UTC quest_one",
                "Abort biggynb 1780213651 UTC quest_one"
        ), lines);
    }

    private static void usesSameTimestampForLineAndFileDate() throws Exception {
        Path tempDir = Files.createTempDirectory("nquest-event-log-midnight-test");
        long[] timestamps = {1780213651L, 1780271999L, 1780272000L};
        int[] index = {0};
        QuestEventLogger logger = new QuestEventLogger(tempDir, ZoneId.of("UTC"), 7,
                () -> timestamps[Math.min(index[0]++, timestamps.length - 1)]);

        logger.logStart("edge", "quest");

        assertEquals(List.of("Start edge 1780271999 UTC quest"),
                Files.readAllLines(tempDir.resolve("quest_events-2026-05-31.log")));
        assertEquals(false, Files.exists(tempDir.resolve("quest_events-2026-06-01.log")));
    }

    private static void deletesFilesOutsideRetentionWindow() throws Exception {
        Path tempDir = Files.createTempDirectory("nquest-event-log-retention-test");
        Files.writeString(tempDir.resolve("quest_events-2026-05-24.log"), "old\n");
        Files.writeString(tempDir.resolve("quest_events-2026-05-25.log"), "keep\n");
        Files.writeString(tempDir.resolve("quest_events-2026-05-31.log"), "today\n");

        new QuestEventLogger(tempDir, ZoneId.of("UTC"), 7, () -> 1780213651L);

        assertEquals(false, Files.exists(tempDir.resolve("quest_events-2026-05-24.log")));
        assertEquals(true, Files.exists(tempDir.resolve("quest_events-2026-05-25.log")));
        assertEquals(true, Files.exists(tempDir.resolve("quest_events-2026-05-31.log")));
    }

    private static void keepsFilesWhenRetentionDisabled() throws Exception {
        Path tempDir = Files.createTempDirectory("nquest-event-log-keep-test");
        Files.writeString(tempDir.resolve("quest_events-2026-01-01.log"), "keep\n");

        new QuestEventLogger(tempDir, ZoneId.of("UTC"), 0, () -> 1780213651L);

        assertEquals(true, Files.exists(tempDir.resolve("quest_events-2026-01-01.log")));
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
