package com.haloce.tcg.game.campaign;

import com.haloce.tcg.game.Lane;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 先行者哨兵网络系统
 * Forerunner Sentinel Network - The Automated Defense Grid
 * 
 * 核心机制：
 * 1. 哨兵制造厂：每回合生成2个哨兵Token
 * 2. 自动修复：哨兵回到制造厂Lane时回合结束回满血
 * 3. 抑制器领域：降低敌方Token单位攻击速度
 * 4. 群体智能：多个哨兵攻击力叠加
 * 5. 节点依赖：需要Monitor/制造厂指挥
 */
public class ForerunnerSentinelNetworkManager {
    
    // 哨兵制造厂位置 (instanceId -> Lane)
    private final Map<String, Lane> sentinelManufactoryByInstance = new HashMap<>();
    
    // 哨兵与其归属制造厂的关系
    private final Map<String, String> sentinelToManufactory = new HashMap<>();
    
    // 指挥节点（Monitor/制造厂）
    private final Map<String, Set<String>> commandNodeToSentinels = new HashMap<>();
    
    // 抑制器领域激活状态 (playerId + Lane -> 是否激活)
    private final Map<String, Boolean> suppressorFieldActive = new HashMap<>();
    
    // 待修复的哨兵列表 (在制造厂Lane内的受损哨兵)
    private final Map<Lane, List<String>> repairQueueByLane = new HashMap<>();
    
    /**
     * 注册哨兵制造厂
     */
    public void registerManufactory(String instanceId, Lane lane, String playerId) {
        sentinelManufactoryByInstance.put(instanceId, lane);
        commandNodeToSentinels.put(instanceId, new HashSet<>());
    }
    
    /**
     * 移除制造厂（被摧毁时）
     */
    public void removeManufactory(String instanceId) {
        sentinelManufactoryByInstance.remove(instanceId);
        Set<String> sentinels = commandNodeToSentinels.remove(instanceId);
        
        // 所有归属的哨兵失去指挥，命中率下降50%
        if (sentinels != null) {
            for (String sentinelId : sentinels) {
                sentinelToManufactory.remove(sentinelId);
            }
        }
    }
    
    /**
     * 生成哨兵Token
     * 每回合制造厂生成2个哨兵
     */
    public List<String> produceTokens(String manufactoryId, int count) {
        Lane lane = sentinelManufactoryByInstance.get(manufactoryId);
        if (lane == null) {
            return List.of();
        }
        
        List<String> tokens = new ArrayList<>();
        Set<String> sentinels = commandNodeToSentinels.get(manufactoryId);
        
        for (int i = 0; i < count; i++) {
            String tokenId = "SENTINEL-TOKEN-" + System.nanoTime();
            tokens.add(tokenId);
            
            // 建立哨兵与制造厂的关系
            sentinelToManufactory.put(tokenId, manufactoryId);
            if (sentinels != null) {
                sentinels.add(tokenId);
            }
        }
        
        return tokens;
    }
    
    /**
     * 哨兵进入修复队列
     * 当哨兵回到制造厂所在Lane时
     */
    public void queueForRepair(String sentinelInstanceId, Lane lane) {
        String manufactory = sentinelToManufactory.get(sentinelInstanceId);
        if (manufactory != null && sentinelManufactoryByInstance.get(manufactory) == lane) {
            repairQueueByLane.computeIfAbsent(lane, k -> new ArrayList<>()).add(sentinelInstanceId);
        }
    }
    
    /**
     * 回合结束时自动修复
     * @return 修复的哨兵列表
     */
    public List<String> processRepairQueue(Lane lane) {
        List<String> repaired = repairQueueByLane.getOrDefault(lane, new ArrayList<>());
        repairQueueByLane.put(lane, new ArrayList<>());
        return repaired;
    }

    /**
     * 回合结束时自动修复 (重载版本 - 基于玩家和回合)
     */
    public List<String> processRepairQueue(String playerId, int globalTurnIndex) {
        // Process all lanes for this player
        List<String> allRepaired = new ArrayList<>();
        for (Lane lane : Lane.values()) {
            allRepaired.addAll(processRepairQueue(lane));
        }
        return allRepaired;
    }
    
    /**
     * 群体智能：计算哨兵攻击力加成
     * 单个哨兵基础伤害 + (同Lane哨兵数量 * 1.5)
     */
    public int calculateSwarmDamage(int baseDamage, int sentinelCountInLane) {
        return baseDamage + (int) Math.ceil(sentinelCountInLane * 1.5);
    }
    
    /**
     * 检查哨兵是否失去指挥
     * 失去指挥的哨兵命中率下降50%
     */
    public boolean hasCommandNode(String sentinelInstanceId) {
        String manufactory = sentinelToManufactory.get(sentinelInstanceId);
        return manufactory != null && sentinelManufactoryByInstance.containsKey(manufactory);
    }
    
    /**
     * 激活抑制器领域
     * 降低该Lane敌方所有Token单位的攻击速度
     */
    public void activateSuppressorField(String playerId, Lane lane) {
        suppressorFieldActive.put(playerId + ":" + lane.name(), true);
    }
    
    /**
     * 检查抑制器领域是否激活
     */
    public boolean isSuppressorFieldActive(String playerId, Lane lane) {
        return suppressorFieldActive.getOrDefault(playerId + ":" + lane.name(), false);
    }
    
    /**
     * 计算抑制器效果
     * Token单位受到30%攻击速度减缓
     */
    public double getSuppressorDebuff() {
        return 0.7; // 攻击速度降低到70%
    }
    
    /**
     * 哨兵类型判断
     */
    public enum SentinelType {
        AGGRESSOR,  // 构造者：基础攻击单位，光束追踪
        PROTECTOR,  // 防御哨兵：提供护盾
        SUPER       // 超级哨兵：防空专用
    }
    
    /**
     * 光束追踪：攻击不会落空
     */
    public boolean hasBeamTracking(String sentinelId) {
        // 构造者哨兵拥有光束追踪
        return sentinelToManufactory.containsKey(sentinelId);
    }

    /**
     * 绑定哨兵到制造厂
     */
    public void bindSentinelToManufactory(String instanceId, String playerId, Lane lane) {
        // 查找该 lane 的制造厂
        String manufactoryId = findManufactoryInLane(lane);
        if (manufactoryId != null) {
            sentinelToManufactory.put(instanceId, manufactoryId);
            commandNodeToSentinels.computeIfAbsent(manufactoryId, k -> new HashSet<>()).add(instanceId);
        }
    }

    /**
     * 生成制造厂的哨兵 Token
     */
    public List<String> produceManufactoryTokens(String playerId, int globalTurnIndex) {
        List<String> produced = new ArrayList<>();
        for (Map.Entry<String, Lane> entry : sentinelManufactoryByInstance.entrySet()) {
            produced.addAll(produceTokens(entry.getKey(), 2));
        }
        return produced;
    }

    /**
     * 查找指定 Lane 中的制造厂
     */
    private String findManufactoryInLane(Lane lane) {
        for (Map.Entry<String, Lane> entry : sentinelManufactoryByInstance.entrySet()) {
            if (entry.getValue() == lane) {
                return entry.getKey();
            }
        }
        return null;
    }
}
