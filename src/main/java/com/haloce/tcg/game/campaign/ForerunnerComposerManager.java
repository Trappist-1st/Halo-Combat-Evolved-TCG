package com.haloce.tcg.game.campaign;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 先行者合成器系统 - 单位进化与转化
 * Forerunner Composer - Unit Evolution & Bio-Digital Conversion
 * 
 * 核心机制：
 * 1. 将哨兵升级为普罗米修斯单位
 * 2. 消耗被击败的生物单位数据进行转化
 * 3. 体现宣教士用"合成器"强行转化生物的冷酷设定
 */
public class ForerunnerComposerManager {
    
    // 合成工厂是否建造 (playerId -> 是否拥有)
    private final Map<String, Boolean> composerLabBuilt = new HashMap<>();
    
    // 收集的生物数据 (playerId -> dataCount)
    private final Map<String, Integer> biologicalDataByPlayer = new HashMap<>();
    
    // 哨兵升级队列 (sentinelId -> 升级进度)
    private final Map<String, UpgradeProgress> sentinelUpgradeQueue = new HashMap<>();
    
    // 转化费用
    private static final int SENTINEL_TO_KNIGHT_DATA_COST = 5;
    private static final int SENTINEL_TO_CRAWLER_DATA_COST = 2;
    private static final int SENTINEL_TO_WATCHER_DATA_COST = 3;
    
    /**
     * 升级进度记录
     */
    public static record UpgradeProgress(
            String sentinelId,
            PrometheanTargetType targetType,
            int startTurn,
            int requiredTurns
    ) {}
    
    /**
     * 普罗米修斯目标类型
     */
    public enum PrometheanTargetType {
        KNIGHT,   // 骑士
        CRAWLER,  // 爬行者
        WATCHER   // 观察者
    }
    
    /**
     * 建造合成工厂
     */
    public void buildComposerLab(String playerId) {
        composerLabBuilt.put(playerId, true);
    }
    
    /**
     * 检查是否拥有合成工厂
     */
    public boolean hasComposerLab(String playerId) {
        return composerLabBuilt.getOrDefault(playerId, false);
    }
    
    /**
     * 收集生物单位数据
     * 当任何阵营的生物单位阵亡时，先行者可以收集其数据
     */
    public void collectBiologicalData(String playerId, String defeatedUnitId) {
        if (!hasComposerLab(playerId)) {
            return;
        }
        
        // 判断是否为生物单位（非机械）
        if (isBiological(defeatedUnitId)) {
            int current = biologicalDataByPlayer.getOrDefault(playerId, 0);
            biologicalDataByPlayer.put(playerId, current + 1);
        }
    }
    
    /**
     * 判断单位是否为生物
     */
    private boolean isBiological(String unitId) {
        // UNSC陆战队、星盟精英等都是生物
        // 机械单位（如疣猪式载具）不算
        return !unitId.contains("VEHICLE") && !unitId.contains("MECH");
    }
    
    /**
     * 获取已收集的生物数据数量
     */
    public int getBiologicalDataCount(String playerId) {
        return biologicalDataByPlayer.getOrDefault(playerId, 0);
    }
    
    /**
     * 开始哨兵升级
     * 消耗生物数据，将哨兵转化为普罗米修斯单位
     */
    public boolean startSentinelUpgrade(
            String playerId,
            String sentinelId,
            PrometheanTargetType targetType,
            int currentTurn
    ) {
        if (!hasComposerLab(playerId)) {
            return false;
        }
        
        int cost = getUpgradeCost(targetType);
        int available = getBiologicalDataCount(playerId);
        
        if (available < cost) {
            return false;
        }
        
        // 消耗数据
        biologicalDataByPlayer.put(playerId, available - cost);
        
        // 加入升级队列
        int requiredTurns = getUpgradeTime(targetType);
        sentinelUpgradeQueue.put(sentinelId, new UpgradeProgress(
                sentinelId, targetType, currentTurn, requiredTurns
        ));
        
        return true;
    }
    
    /**
     * 获取升级费用
     */
    private int getUpgradeCost(PrometheanTargetType type) {
        return switch (type) {
            case KNIGHT -> SENTINEL_TO_KNIGHT_DATA_COST;
            case CRAWLER -> SENTINEL_TO_CRAWLER_DATA_COST;
            case WATCHER -> SENTINEL_TO_WATCHER_DATA_COST;
        };
    }
    
    /**
     * 获取升级所需回合数
     */
    private int getUpgradeTime(PrometheanTargetType type) {
        return switch (type) {
            case KNIGHT -> 3;  // 骑士需要3回合
            case CRAWLER -> 1; // 爬行者需要1回合
            case WATCHER -> 2; // 观察者需要2回合
        };
    }
    
    /**
     * 检查升级是否完成
     */
    public boolean isUpgradeComplete(String sentinelId, int currentTurn) {
        UpgradeProgress progress = sentinelUpgradeQueue.get(sentinelId);
        if (progress == null) {
            return false;
        }
        
        int elapsed = currentTurn - progress.startTurn();
        return elapsed >= progress.requiredTurns();
    }
    
    /**
     * 完成升级，返回新单位类型
     */
    public PrometheanTargetType completeUpgrade(String sentinelId) {
        UpgradeProgress progress = sentinelUpgradeQueue.remove(sentinelId);
        return progress != null ? progress.targetType() : null;
    }
    
    /**
     * 获取升级进度
     */
    public UpgradeProgress getUpgradeProgress(String sentinelId) {
        return sentinelUpgradeQueue.get(sentinelId);
    }
    
    /**
     * 取消升级（哨兵被摧毁）
     * 返还部分数据
     */
    public void cancelUpgrade(String playerId, String sentinelId) {
        UpgradeProgress progress = sentinelUpgradeQueue.remove(sentinelId);
        if (progress != null) {
            // 返还50%数据
            int refund = getUpgradeCost(progress.targetType()) / 2;
            int current = biologicalDataByPlayer.getOrDefault(playerId, 0);
            biologicalDataByPlayer.put(playerId, current + refund);
        }
    }
    
    /**
     * 合成器超载
     * 消耗大量数据和能量，直接生成高级普罗米修斯单位（无需基础哨兵）
     */
    public boolean canDirectCompose(String playerId, PrometheanTargetType type) {
        int cost = getUpgradeCost(type) * 2; // 直接合成费用翻倍
        return getBiologicalDataCount(playerId) >= cost;
    }
    
    /**
     * 执行直接合成
     */
    public boolean directCompose(String playerId, PrometheanTargetType type) {
        if (!canDirectCompose(playerId, type)) {
            return false;
        }
        
        int cost = getUpgradeCost(type) * 2;
        int current = biologicalDataByPlayer.get(playerId);
        biologicalDataByPlayer.put(playerId, current - cost);
        
        return true;
    }

    public List<String> checkUpgradeCompletion(String playerId, int globalTurnIndex) {
        // Placeholder for checking upgrade completion
        return List.of();
    }
}
