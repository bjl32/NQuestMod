package cn.zbx1425.nquestmod.data;

import cn.zbx1425.nquestmod.data.quest.Quest;
import cn.zbx1425.nquestmod.data.quest.QuestCompletionData;
import cn.zbx1425.nquestmod.data.quest.QuestProgress;
import cn.zbx1425.nquestmod.data.ranking.RankingApiClient;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

public interface IQuestCallbacks {

    void onQuestStarted(QuestDispatcher questEngine, UUID playerUuid, Quest quest);

    void onStepCompleted(QuestDispatcher questEngine, UUID playerUuid, Quest quest, QuestProgress progress);

    void onQuestCompleted(QuestDispatcher questEngine, UUID playerUuid, Quest quest, QuestCompletionData data);

    void onCompletionRanked(UUID playerUuid, Quest quest, QuestCompletionData data,
                            boolean isPersonalBest, boolean isWorldRecord, int rank);

    void onQuestAborted(QuestDispatcher questEngine, UUID playerUuid, Quest quest);

    void onQuestFailed(QuestDispatcher questEngine, UUID playerUuid, Quest quest,
                       Component reason, String failureType);

    void onPlayerBanned(UUID playerUuid, List<RankingApiClient.ActiveBan> activeBans);

    void onCompletionRejectedBan(UUID playerUuid, Quest quest);

}
