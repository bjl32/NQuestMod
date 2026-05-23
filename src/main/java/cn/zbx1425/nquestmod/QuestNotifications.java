package cn.zbx1425.nquestmod;

import cn.zbx1425.nquestmod.data.QuestDispatcher;
import cn.zbx1425.nquestmod.data.IQuestCallbacks;
import cn.zbx1425.nquestmod.data.criteria.Criterion;
import cn.zbx1425.nquestmod.data.quest.*;
import cn.zbx1425.nquestmod.data.ranking.RankingApiClient;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class QuestNotifications implements IQuestCallbacks {

    private final MinecraftServer server;

    public QuestNotifications(MinecraftServer server) {
        this.server = server;
    }

    public void onPlayerJoin(QuestDispatcher questEngine, UUID playerUuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player == null) return;
        removeLegacyBossBar(player);
        updateHudForPlayer(questEngine, player);
    }

    @Override
    public void onQuestStarted(QuestDispatcher questEngine, UUID playerUuid, Quest quest) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player == null) return;
        player.sendSystemMessage(Component.literal("⭐ Quest Started! ⭐")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true)), false);
        player.sendSystemMessage(Component.literal(quest.name).withStyle(ChatFormatting.YELLOW), false);

        if (!quest.steps.isEmpty()) {
            Step firstStep = quest.steps.get(0);
            player.sendSystemMessage(Component.literal("▶ First: ").withStyle(ChatFormatting.AQUA)
                    .append(firstStep.criteria.getDisplayRepr()), false);

            Criterion failureCriteria = firstStep.failureCriteria != null
                ? firstStep.failureCriteria
                : (quest.defaultCriteria != null
                ? quest.defaultCriteria.failureCriteria : null);
            if (failureCriteria != null) {
                MutableComponent failureMsg = Component.literal("   Do not: ").withStyle(ChatFormatting.GRAY)
                    .append(failureCriteria.getDisplayRepr());
                player.sendSystemMessage(failureMsg, false);
            }
        }
        sendSoundEffect(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0f, 1.0f);
        removeLegacyBossBar(player);
        updateHudForPlayer(questEngine, player, true);
    }

    @Override
    public void onStepCompleted(QuestDispatcher questEngine, UUID playerUuid, Quest quest, QuestProgress progress) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player == null) return;

        if (progress.currentStepIndex > 0) {
            Step completedStep = quest.steps.get(progress.currentStepIndex - 1);
            player.sendSystemMessage(Component.literal("✔ Step Complete: ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(completedStep.criteria.getDisplayRepr().getString()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)), false);
        }

        if (progress.currentStepIndex < quest.steps.size()) {
            Step nextStep = quest.steps.get(progress.currentStepIndex);
            MutableComponent nextStepMsg = Component.literal("▶ Next: ").withStyle(ChatFormatting.GOLD)
                    .append(nextStep.criteria.getDisplayRepr());
            player.sendSystemMessage(nextStepMsg, false);

            Criterion failureCriteria = nextStep.failureCriteria != null
                ? nextStep.failureCriteria
                : (quest.defaultCriteria != null
                    ? quest.defaultCriteria.failureCriteria : null);
            if (failureCriteria != null) {
                MutableComponent failureMsg = Component.literal("   Do not: ").withStyle(ChatFormatting.GRAY)
                    .append(failureCriteria.getDisplayRepr());
                player.sendSystemMessage(failureMsg, false);
            }
        }
        sendSoundEffect(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0f, 1.0f);
        removeLegacyBossBar(player);
        updateHudForPlayer(questEngine, player);
    }

    @Override
    public void onQuestCompleted(QuestDispatcher questEngine, UUID playerUuid, Quest quest, QuestCompletionData data) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player == null) return;
        boolean debug = questEngine.isDebugMode(playerUuid);
        player.sendSystemMessage(Component.literal("⭐ Quest Complete! ⭐")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true)), false);
        player.sendSystemMessage(Component.literal(quest.name).withStyle(ChatFormatting.YELLOW), false);
        player.sendSystemMessage(Component.literal("  Time taken: ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(formatDuration(data.durationMillis)).withStyle(ChatFormatting.GREEN)), false);
        player.sendSystemMessage(Component.literal("  Quest Points: ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("+" + quest.questPoints + " QP").withStyle(ChatFormatting.AQUA)), false);

        if (debug) {
            player.sendSystemMessage(Component.literal("  NOTE: Ask a staff to DISQUALIFY this completion " +
                    "if you don't want it to contribute to the leaderboard.")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
        }

        sendSoundEffect(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        removeLegacyBossBar(player);
        clearHud(player);
    }

    @Override
    public void onCompletionRanked(UUID playerUuid, Quest quest, QuestCompletionData data,
                                   boolean isPersonalBest, boolean isWorldRecord, int rank) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) return;

            if (isWorldRecord) {
                player.sendSystemMessage(Component.literal("  World Record!")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true)), false);
                sendSoundEffect(player, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.5f);
            } else if (isPersonalBest) {
                player.sendSystemMessage(Component.literal("  Personal Best!")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.BLUE).withBold(true)), false);
                sendSoundEffect(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.2f);
            }

            if (rank > 0) {
                player.sendSystemMessage(Component.literal("  Rank: ").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal("#" + rank).withStyle(ChatFormatting.GOLD)), false);
            }
        });
    }

    @Override
    public void onQuestAborted(QuestDispatcher questEngine, UUID playerUuid, Quest quest) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player == null) return;
        player.sendSystemMessage(Component.literal("✘ Quest Aborted ✘")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true)), false);
        player.sendSystemMessage(Component.literal(quest.name).withStyle(ChatFormatting.YELLOW), false);
        sendSoundEffect(player, SoundEvents.ANVIL_LAND, 0.5f, 1.0f);
        removeLegacyBossBar(player);
        clearHud(player);
    }

    @Override
    public void onQuestFailed(QuestDispatcher questEngine, UUID playerUuid, Quest quest, Component reason) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player == null) return;
        player.sendSystemMessage(Component.literal("✘ Quest Failed ✘")
            .withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true)), false);
        player.sendSystemMessage(Component.literal(quest.name).withStyle(ChatFormatting.YELLOW), false);
        player.sendSystemMessage(Component.literal("Player did not follow requirement").withStyle(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  Do not: ").withStyle(ChatFormatting.WHITE)
            .append(reason.copy().withStyle(ChatFormatting.RED)), false);
        sendSoundEffect(player, SoundEvents.ANVIL_LAND, 0.5f, 1.0f);
        removeLegacyBossBar(player);
        clearHud(player);
    }

    @Override
    public void onPlayerBanned(UUID playerUuid, List<RankingApiClient.ActiveBan> activeBans) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) return;

            player.sendSystemMessage(Component.literal("\u26D4 Cannot Start Quest \u26D4")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true)), false);

            RankingApiClient.ActiveBan ban = pickMostSevereBan(activeBans);
            if (ban != null && "TEMP".equals(ban.banType)) {
                player.sendSystemMessage(Component.literal("Your account is temporarily banned from Quest participation.")
                        .withStyle(ChatFormatting.WHITE), false);
                player.sendSystemMessage(Component.literal("  Reason: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(ban.reason).withStyle(ChatFormatting.WHITE)), false);
                if (ban.expiresAt != null) {
                    String formatted = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
                            .withZone(ZoneOffset.UTC)
                            .format(Instant.ofEpochMilli(ban.expiresAt));
                    player.sendSystemMessage(Component.literal("  Expires: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(formatted).withStyle(ChatFormatting.AQUA)), false);
                }
            } else {
                player.sendSystemMessage(Component.literal("Your account is permanently banned from Quest participation.")
                        .withStyle(ChatFormatting.WHITE), false);
                if (ban != null) {
                    player.sendSystemMessage(Component.literal("  Reason: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(ban.reason).withStyle(ChatFormatting.WHITE)), false);
                }
            }

            sendSoundEffect(player, SoundEvents.ANVIL_LAND, 0.5f, 1.0f);
        });
    }

    @Override
    public void onCompletionRejectedBan(UUID playerUuid, Quest quest) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) return;

            player.sendSystemMessage(Component.literal("\u26D4 Quest Completion Rejected \u26D4")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true)), false);
            player.sendSystemMessage(Component.literal(quest.name).withStyle(ChatFormatting.YELLOW), false);
            player.sendSystemMessage(Component.literal("Your account was banned during this quest. The completion will not be recorded.")
                    .withStyle(ChatFormatting.WHITE), false);

            sendSoundEffect(player, SoundEvents.ANVIL_LAND, 0.5f, 1.0f);
        });
    }

    private void updateHudForPlayer(QuestDispatcher questEngine, ServerPlayer player) {
        updateHudForPlayer(questEngine, player, false);
    }

    private void updateHudForPlayer(QuestDispatcher questEngine, ServerPlayer player, boolean resetCurrentSegment) {
        PlayerProfile profile = questEngine.getPlayerProfile(player.getGameProfile().getId());
        if (profile == null || profile.activeQuests.isEmpty()) {
            clearHud(player);
            return;
        }

        QuestProgress progress = profile.activeQuests.values().stream().findFirst().orElse(null);
        if (progress == null || progress.questSnapshot == null
                || progress.currentStepIndex >= progress.questSnapshot.steps.size()) {
            clearHud(player);
            return;
        }

        Quest quest = progress.questSnapshot;
        Step currentStep = quest.steps.get(progress.currentStepIndex);
        long now = System.currentTimeMillis();
        long segmentElapsedMillis = resetCurrentSegment ? 0 : Math.max(0, progress.getCurrentStepDuration(now));
        long totalElapsedMillis = segmentElapsedMillis;
        for (var entry : progress.previousSessionsStepDurationsMillis.entrySet()) {
            if (entry.getKey() != progress.currentStepIndex) {
                totalElapsedMillis += Math.max(0, entry.getValue());
            }
        }

        QuestHudNetworking.HudState state = new QuestHudNetworking.HudState(
                true,
                currentStep.getDisplayRepr(),
                questEngine.isDebugMode(profile.playerUuid),
                progress.currentStepIndex + 1,
                quest.steps.size(),
                totalElapsedMillis,
                segmentElapsedMillis
        );
        sendHudState(player, state);
    }

    private void clearHud(ServerPlayer player) {
        sendHudState(player, QuestHudNetworking.HudState.inactive());
    }

    private void sendHudState(ServerPlayer player, QuestHudNetworking.HudState state) {
        if (!ServerPlayNetworking.canSend(player, QuestHudNetworking.QUEST_HUD)) return;
        FriendlyByteBuf buf = PacketByteBufs.create();
        QuestHudNetworking.write(buf, state);
        ServerPlayNetworking.send(player, QuestHudNetworking.QUEST_HUD, buf);
    }

    private void removeLegacyBossBar(ServerPlayer player) {
        var event = player.getServer().getCustomBossEvents().get(NQuestMod.id(player.getGameProfile().getId().toString()));
        if (event != null) {
            event.removeAllPlayers();
            player.getServer().getCustomBossEvents().remove(event);
        }
    }

    private static RankingApiClient.ActiveBan pickMostSevereBan(List<RankingApiClient.ActiveBan> bans) {
        if (bans == null || bans.isEmpty()) return null;
        RankingApiClient.ActiveBan worst = null;
        for (RankingApiClient.ActiveBan ban : bans) {
            if ("PERM".equals(ban.banType)) return ban;
            if (worst == null
                    || (ban.expiresAt != null && (worst.expiresAt == null || ban.expiresAt > worst.expiresAt))) {
                worst = ban;
            }
        }
        return worst;
    }

    private String formatDuration(long millis) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void sendSoundEffect(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(
            BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
            SoundSource.MASTER,
            player.getX(), player.getY(), player.getZ(),
            volume, pitch, 0
        ));
    }
}
