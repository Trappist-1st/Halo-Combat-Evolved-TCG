package com.haloce.tcg.game.campaign;

import java.util.HashMap;
import java.util.Map;

/**
 * 先行者虚空能源系统 - 物质重组与资源回收
 * Forerunner Vacuum Energy & Matter Reconfiguration
 * 
 * 核心机制：
 * 1. 单位死亡时返还40%构建费用（原始数据流）
 * 2. 能级系统逐回合提升
 */
public class ForerunnerVacuumEnergyManager {
    
    // 玩家能级池 - 先行者不使用补给/信仰，使用能级
    private final Map<String, Integer> powerLevelByPlayer = new HashMap<>();
    
    // 单位构建费用记录
    private final Map<String, Integer> unitCostByInstanceId = new HashMap<>();
    
    // 能级上限
    private static final int MAX_POWER_LEVEL = 20;
    
    // 物质重组返还比例
    private static final double RECONFIGURATION_REFUND_RATE = 0.4;
    
    /**
     * 初始化玩家能级
     */
    public void initializePowerLevel(String playerId, int startingLevel) {
        powerLevelByPlayer.put(playerId, startingLevel);
    }
    
    /**
     * 回合开始时提升能级
     */
    public void incrementPowerLevel(String playerId) {
        int current = powerLevelByPlayer.getOrDefault(playerId, 0);
        if (current < MAX_POWER_LEVEL) {
            powerLevelByPlayer.put(playerId, current + 1);
        }
    }
    
    /**
     * 获取当前能级
     */
    public int getPowerLevel(String playerId) {
        return powerLevelByPlayer.getOrDefault(playerId, 0);
    }
    
    /**
     * 记录单位构建费用 (2 参数版本)
     */
    public void recordUnitCost(String instanceId, int cost) {
        unitCostByInstanceId.put(instanceId, cost);
    }

    /**
     * 记录单位构建费用 (3 参数版本 - 兼容性)
     */
    public void recordUnitCost(String playerId, String instanceId, int cost) {
        unitCostByInstanceId.put(instanceId, cost);
    }
    
    /**
     * 物质重组：单位死亡时返还资源
     * 先行者单位化为"原始数据流"返回资源池
     * 
     * @return 返还的能量值
     */
    public int reconfigureMatter(String playerId, String instanceId) {
        Integer originalCost = unitCostByInstanceId.remove(instanceId);
        if (originalCost == null) {
            return 0;
        }
        
        int refund = (int) Math.ceil(originalCost * RECONFIGURATION_REFUND_RATE);
        
        // 将能量返还给玩家
        int current = powerLevelByPlayer.getOrDefault(playerId, 0);
        powerLevelByPlayer.put(playerId, Math.min(current + refund, MAX_POWER_LEVEL));
        
        return refund;
    }
    
    /**
     * 消耗能级
     */
    public boolean consumePowerLevel(String playerId, int amount) {
        int current = powerLevelByPlayer.getOrDefault(playerId, 0);
        if (current >= amount) {
            powerLevelByPlayer.put(playerId, current - amount);
            return true;
        }
        return false;
    }
    
    /**
     * 单位自适应：根据Lane切换形态无需额外费用
     */
    public boolean canAdaptFormWithoutCost(String instanceId) {
        // 先行者单位（普罗米修斯骑士）可以根据Lane切换形态
        return unitCostByInstanceId.containsKey(instanceId);
    }
}
