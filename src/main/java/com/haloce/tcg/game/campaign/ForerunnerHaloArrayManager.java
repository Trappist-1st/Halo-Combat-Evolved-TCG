package com.haloce.tcg.game.campaign;

import com.haloce.tcg.game.Lane;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 先行者环带协议 - 终极威慑武器
 * Forerunner Halo Array Protocol - The Ultimate Threat
 * 
 * 核心机制：
 * 1. 收集3张索引器（Index）卡牌
 * 2. 在地标（Objective）停留3个回合
 * 3. 启动局部环带脉冲 - 清除该Lane所有生物单位
 * 4. 全局博弈：其他阵营必须联合破坏终端
 */
public class ForerunnerHaloArrayManager {
    
    // 玩家收集的索引器数量
    private final Map<String, Set<String>> indexCollectionByPlayer = new HashMap<>();
    
    // 终端激活状态 (playerId -> ActivationTerminal)
    private final Map<String, ActivationTerminal> activeTerminals = new HashMap<>();
    
    // 环带脉冲充能进度
    private final Map<String, Integer> pulseChargeProgress = new HashMap<>();
    
    // 索引器总数要求
    private static final int REQUIRED_INDEX_COUNT = 3;
    
    // 激活需要的停留回合数
    private static final int REQUIRED_TURNS_AT_OBJECTIVE = 3;
    
    /**
     * 激活终端信息
     */
    public static record ActivationTerminal(
            Lane lane,
            String objectiveInstanceId,
            int activationStartTurn,
            int currentProgress
    ) {}
    
    /**
     * 收集索引器
     */
    public void collectIndex(String playerId, String indexCardId) {
        indexCollectionByPlayer.computeIfAbsent(playerId, k -> new HashSet<>()).add(indexCardId);
    }
    
    /**
     * 检查是否收集齐索引器
     */
    public boolean hasAllIndices(String playerId) {
        Set<String> indices = indexCollectionByPlayer.get(playerId);
        return indices != null && indices.size() >= REQUIRED_INDEX_COUNT;
    }
    
    /**
     * 获取已收集的索引器数量
     */
    public int getIndexCount(String playerId) {
        Set<String> indices = indexCollectionByPlayer.get(playerId);
        return indices != null ? indices.size() : 0;
    }
    
    /**
     * 开始激活终端
     * 需要在地标停留
     */
    public boolean startTerminalActivation(String playerId, Lane lane, String objectiveId, int currentTurn) {
        if (!hasAllIndices(playerId)) {
            return false;
        }
        
        activeTerminals.put(playerId, new ActivationTerminal(lane, objectiveId, currentTurn, 0));
        pulseChargeProgress.put(playerId, 0);
        return true;
    }
    
    /**
     * 回合进度更新
     * 检查是否仍在地标停留
     */
    public boolean updateActivationProgress(String playerId, boolean stillAtObjective, int currentTurn) {
        ActivationTerminal terminal = activeTerminals.get(playerId);
        if (terminal == null) {
            return false;
        }
        
        if (!stillAtObjective) {
            // 离开地标，激活中断
            activeTerminals.remove(playerId);
            pulseChargeProgress.remove(playerId);
            return false;
        }
        
        int turnsElapsed = currentTurn - terminal.activationStartTurn();
        int newProgress = Math.min(turnsElapsed, REQUIRED_TURNS_AT_OBJECTIVE);
        pulseChargeProgress.put(playerId, newProgress);
        
        return newProgress >= REQUIRED_TURNS_AT_OBJECTIVE;
    }
    
    /**
     * 检查是否可以发射脉冲
     */
    public boolean canFirePulse(String playerId) {
        Integer progress = pulseChargeProgress.get(playerId);
        return progress != null && progress >= REQUIRED_TURNS_AT_OBJECTIVE;
    }
    
    /**
     * 发射局部环带脉冲
     * @return 受影响的Lane
     */
    public Lane firePulse(String playerId) {
        ActivationTerminal terminal = activeTerminals.remove(playerId);
        if (terminal == null) {
            return null;
        }
        
        pulseChargeProgress.remove(playerId);
        // 清空索引器（一次性使用）
        indexCollectionByPlayer.remove(playerId);
        
        return terminal.lane();
    }
    
    /**
     * 获取激活进度
     */
    public int getActivationProgress(String playerId) {
        return pulseChargeProgress.getOrDefault(playerId, 0);
    }
    
    /**
     * 终端被破坏（敌方攻击）
     */
    public void destroyTerminal(String playerId) {
        activeTerminals.remove(playerId);
        pulseChargeProgress.remove(playerId);
        // 索引器保留，可以重新尝试激活
    }
    
    /**
     * 检查终端是否激活中
     */
    public boolean hasActiveTerminal(String playerId) {
        return activeTerminals.containsKey(playerId);
    }
    
    /**
     * 获取终端信息
     */
    public ActivationTerminal getTerminal(String playerId) {
        return activeTerminals.get(playerId);
    }
    
    /**
     * 环带脉冲效果范围
     */
    public enum PulseEffectType {
        LANE_CLEAR,      // 局部：清除指定Lane所有生物单位
        SECTOR_CLEAR,    // 中级：清除相邻3个Lane
        GALAXY_RESET     // 终极：全局重置（游戏结束条件）
    }
    
    /**
     * 计算脉冲威力
     * 基于停留时间和索引器数量
     */
    public PulseEffectType calculatePulseEffect(int indexCount, int turnProgress) {
        if (indexCount >= 7 && turnProgress >= 5) {
            return PulseEffectType.GALAXY_RESET;
        } else if (indexCount >= 5 && turnProgress >= 4) {
            return PulseEffectType.SECTOR_CLEAR;
        } else {
            return PulseEffectType.LANE_CLEAR;
        }
    }
}
