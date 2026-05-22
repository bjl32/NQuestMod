package cn.zbx1425.nquestmod.data.ranking;

import cn.zbx1425.nquestmod.NQuestMod;
import cn.zbx1425.nquestmod.data.NQuestGson;
import cn.zbx1425.nquestmod.data.quest.QuestCompletionData;
import com.google.gson.Gson;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class PendingCompletions {

    private final Path walFile;
    private final Gson gson = NQuestGson.INSTANCE;
    private final AtomicBoolean replayInProgress = new AtomicBoolean(false);

    public PendingCompletions(Path basePath) {
        this.walFile = basePath.resolve("pending_completions.jsonl");
    }

    public synchronized void enqueue(QuestCompletionData data) {
        try {
            String line = gson.toJson(data) + "\n";
            Files.writeString(walFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            NQuestMod.LOGGER.error("Failed to write pending completion to WAL", e);
        }
    }

    public boolean hasPending() {
        return Files.exists(walFile);
    }

    private record PendingEntry(String line, QuestCompletionData data) {}

    private synchronized List<PendingEntry> readAll() {
        List<PendingEntry> results = new ArrayList<>();
        if (!Files.exists(walFile)) return results;
        try {
            List<String> lines = Files.readAllLines(walFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    results.add(new PendingEntry(trimmed, gson.fromJson(trimmed, QuestCompletionData.class)));
                } catch (Exception e) {
                    NQuestMod.LOGGER.warn("Skipping malformed WAL entry: {}", trimmed, e);
                }
            }
        } catch (IOException e) {
            NQuestMod.LOGGER.error("Failed to read pending completions from WAL", e);
        }
        return results;
    }

    private synchronized void removeSucceededEntries(List<PendingEntry> attempted, List<PendingEntry> failed) {
        List<String> succeededLines = new ArrayList<>();
        for (PendingEntry entry : attempted) {
            if (!failed.contains(entry)) {
                succeededLines.add(entry.line());
            }
        }
        if (succeededLines.isEmpty() || !Files.exists(walFile)) return;

        try {
            List<String> currentLines = Files.readAllLines(walFile, StandardCharsets.UTF_8);
            List<String> remainingLines = new ArrayList<>();
            for (String line : currentLines) {
                int index = succeededLines.indexOf(line.trim());
                if (index >= 0) {
                    succeededLines.remove(index);
                } else {
                    remainingLines.add(line);
                }
            }

            if (remainingLines.isEmpty()) {
                Files.deleteIfExists(walFile);
            } else {
                Files.write(walFile, remainingLines, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException e) {
            NQuestMod.LOGGER.error("Failed to update pending completions WAL after replay", e);
        }
    }

    public void replayAll(RankingApiClient rankingApi) {
        if (!replayInProgress.compareAndSet(false, true)) return;
        List<PendingEntry> pending = readAll();
        if (pending.isEmpty()) {
            replayInProgress.set(false);
            return;
        }

        NQuestMod.LOGGER.info("Replaying {} pending completions from WAL", pending.size());
        List<PendingEntry> failed = Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Void>> submissions = new ArrayList<>();
        for (PendingEntry entry : pending) {
            submissions.add(rankingApi.submitCompletion(entry.data()).<Void>handle((response, error) -> {
                    QuestCompletionData data = entry.data();
                    if (error != null) {
                        NQuestMod.LOGGER.error("Failed to replay pending completion for quest {}, re-enqueuing", data.questId, error);
                        failed.add(entry);
                    }
                    return null;
                })
            );
        }

        CompletableFuture.allOf(submissions.toArray(CompletableFuture[]::new)).whenComplete((unused, error) -> {
            removeSucceededEntries(pending, failed);
            replayInProgress.set(false);
        });
    }

    public void replayIfNeeded(RankingApiClient rankingApi) {
        if (!hasPending()) return;
        replayAll(rankingApi);
    }
}
