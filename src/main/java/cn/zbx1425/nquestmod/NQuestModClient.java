package cn.zbx1425.nquestmod;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NQuestModClient implements ClientModInitializer {

    private static final int HUD_X = 8;
    private static final int HUD_Y = 8;
    private static final int PADDING = 5;
    private static final int LINE_GAP = 2;
    private static final int MAX_TEXT_WIDTH = 220;
    private static final int PANEL_COLOR = 0xA0000000;
    private static final int LABEL_COLOR = 0xFFAAAAAA;
    private static final int VALUE_COLOR = 0xFFFFFFFF;

    private static ClientConfig config = new ClientConfig();
    private static ClientHudState hudState = ClientHudState.inactive();

    @Override
    public void onInitializeClient() {
        config = ClientConfig.load();

        ClientPlayNetworking.registerGlobalReceiver(QuestHudNetworking.QUEST_HUD,
                (client, handler, buf, responseSender) -> {
                    QuestHudNetworking.HudState receivedState = QuestHudNetworking.read(buf);
                    client.execute(() -> hudState = ClientHudState.from(receivedState));
                });

        HudRenderCallback.EVENT.register(NQuestModClient::renderHud);
    }

    private static void renderHud(GuiGraphics graphics, float tickDelta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!hudState.active || minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || minecraft.options.hideGui) {
            return;
        }

        Font font = minecraft.font;
        int textMaxWidth = Math.max(40, Math.min(MAX_TEXT_WIDTH, graphics.guiWidth() - HUD_X - PADDING * 2 - 4));

        Component objective = hudState.objective;
        if (hudState.debugMode && config.showDebugPrefix) {
            objective = Component.literal("[DEBUG] ").withStyle(ChatFormatting.DARK_GRAY).append(objective);
        }

        List<FormattedCharSequence> lines = new ArrayList<>();
        List<FormattedCharSequence> objectiveLines = font.split(objective, textMaxWidth);
        if (objectiveLines.isEmpty()) {
            lines.add(Component.empty().getVisualOrderText());
        } else if (objectiveLines.size() <= 2) {
            lines.addAll(objectiveLines);
        } else {
            lines.add(objectiveLines.get(0));
            String plainObjective = objective.getString();
            String firstPlainLine = font.plainSubstrByWidth(plainObjective, textMaxWidth);
            String remainingObjective = plainObjective.substring(Math.min(firstPlainLine.length(), plainObjective.length())).trim();
            String secondLine = ellipsize(remainingObjective, font, textMaxWidth);
            lines.add(Component.literal(secondLine).getVisualOrderText());
        }

        lines.add(Component.literal("Step " + hudState.currentStepNumber + "/" + hudState.totalSteps)
                .withStyle(ChatFormatting.GRAY)
                .getVisualOrderText());

        long elapsedSinceReceive = System.currentTimeMillis() - hudState.receivedAtMillis;
        String totalTime = formatDuration(hudState.totalElapsedMillis + elapsedSinceReceive);
        String segmentTime = formatDuration(hudState.segmentElapsedMillis + elapsedSinceReceive);

        String totalLine = "Total time: " + totalTime;
        String segmentLine = "Segment time: " + segmentTime;

        int contentWidth = Math.max(font.width(totalLine), font.width(segmentLine));
        for (FormattedCharSequence line : lines) {
            contentWidth = Math.max(contentWidth, font.width(line));
        }
        contentWidth = Math.min(contentWidth, textMaxWidth);

        int lineHeight = font.lineHeight;
        int contentHeight = (lines.size() + 2) * lineHeight + (lines.size() + 1) * LINE_GAP;
        int panelWidth = contentWidth + PADDING * 2;
        int panelHeight = contentHeight + PADDING * 2;

        graphics.fill(HUD_X, HUD_Y, HUD_X + panelWidth, HUD_Y + panelHeight, PANEL_COLOR);

        int y = HUD_Y + PADDING;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, HUD_X + PADDING, y, VALUE_COLOR, true);
            y += lineHeight + LINE_GAP;
        }
        drawLabelValue(graphics, font, "Total time: ", totalTime, HUD_X + PADDING, y);
        y += lineHeight + LINE_GAP;
        drawLabelValue(graphics, font, "Segment time: ", segmentTime, HUD_X + PADDING, y);
    }

    private static void drawLabelValue(GuiGraphics graphics, Font font, String label, String value, int x, int y) {
        graphics.drawString(font, label, x, y, LABEL_COLOR, true);
        graphics.drawString(font, value, x + font.width(label), y, VALUE_COLOR, true);
    }

    private static String ellipsize(String value, Font font, int maxWidth) {
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (font.width(value) <= maxWidth) return value;
        if (ellipsisWidth >= maxWidth) return ellipsis;
        return font.plainSubstrByWidth(value, maxWidth - ellipsisWidth) + ellipsis;
    }

    private static String formatDuration(long millis) {
        long clamped = Math.max(0, millis);
        long hours = TimeUnit.MILLISECONDS.toHours(clamped);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(clamped) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(clamped) % 60;
        return String.format("%d:%02d:%02d", hours, minutes, seconds);
    }

    private record ClientHudState(
            boolean active,
            Component objective,
            boolean debugMode,
            int currentStepNumber,
            int totalSteps,
            long totalElapsedMillis,
            long segmentElapsedMillis,
            long receivedAtMillis
    ) {

        static ClientHudState inactive() {
            return new ClientHudState(false, Component.empty(), false, 0, 0, 0, 0, 0);
        }

        static ClientHudState from(QuestHudNetworking.HudState state) {
            if (!state.active()) {
                return inactive();
            }
            return new ClientHudState(
                    true,
                    state.objective(),
                    state.debugMode(),
                    state.currentStepNumber(),
                    state.totalSteps(),
                    state.totalElapsedMillis(),
                    state.segmentElapsedMillis(),
                    System.currentTimeMillis()
            );
        }
    }

    private static class ClientConfig {

        boolean showDebugPrefix = true;

        static ClientConfig load() {
            Path path = FabricLoader.getInstance().getConfigDir().resolve("nquest-client.json");
            ClientConfig result = new ClientConfig();
            if (Files.exists(path)) {
                try {
                    JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                    if (json.has("showDebugPrefix")) {
                        result.showDebugPrefix = json.get("showDebugPrefix").getAsBoolean();
                    }
                } catch (Exception e) {
                    NQuestMod.LOGGER.warn("Failed to load NQuest client config, using defaults", e);
                }
            }
            try {
                Files.createDirectories(path.getParent());
                JsonObject json = new JsonObject();
                json.addProperty("showDebugPrefix", result.showDebugPrefix);
                Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(json));
            } catch (IOException e) {
                NQuestMod.LOGGER.warn("Failed to save NQuest client config", e);
            }
            return result;
        }
    }
}
