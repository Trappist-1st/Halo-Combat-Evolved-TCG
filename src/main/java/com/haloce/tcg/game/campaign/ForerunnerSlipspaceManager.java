package com.haloce.tcg.game.campaign;

import com.haloce.tcg.game.Lane;

import java.util.HashMap;
import java.util.Map;

/**
 * 先行者空间折叠系统
 * Forerunner Slipspace Technology - Spatial Manipulation
 * 
 * 核心机制：
 * 1. 空间传送：瞬间将单位从一个Lane传送到另一个Lane
 * 2. 重力井：拦截敌方空投，弹回手牌
 * 3. 跃迁门：建立Lane之间的传送门
 */
public class ForerunnerSlipspaceManager {
    
    // 跃迁门网络 (Lane -> 连接的Lane)
    private final Map<Lane, Lane> slipspaceGateNetwork = new HashMap<>();
    
    // 重力井激活状态 (playerId + Lane -> 是否激活)
    private final Map<String, Boolean> gravityWellActive = new HashMap<>();
    
    // 空间传送冷却时间 (玩家 -> 最后使用回合)
    private final Map<String, Integer> teleportCooldown = new HashMap<>();
    
    // 传送冷却回合数
    private static final int TELEPORT_COOLDOWN_TURNS = 2;
    
    /**
     * 建立跃迁门
     * 连接两个Lane，单位可以快速传送
     */
    public void establishSlipspaceGate(Lane fromLane, Lane toLane) {
        slipspaceGateNetwork.put(fromLane, toLane);
        slipspaceGateNetwork.put(toLane, fromLane); // 双向连接
    }
    
    /**
     * 关闭跃迁门
     */
    public void closeSlipspaceGate(Lane lane) {
        Lane connected = slipspaceGateNetwork.remove(lane);
        if (connected != null) {
            slipspaceGateNetwork.remove(connected);
        }
    }
    
    /**
     * 检查两个Lane是否有跃迁门连接
     */
    public boolean hasGateConnection(Lane from, Lane to) {
        return slipspaceGateNetwork.get(from) == to;
    }
    
    /**
     * 执行空间传送
     * 将单位从一个Lane瞬间传送到另一个Lane
     * 
     * @return 是否成功传送
     */
    public boolean teleportUnit(String playerId, String instanceId, Lane fromLane, Lane toLane, int currentTurn) {
        // 检查冷却
        Integer lastUseTurn = teleportCooldown.get(playerId);
        if (lastUseTurn != null && currentTurn - lastUseTurn < TELEPORT_COOLDOWN_TURNS) {
            return false;
        }
        
        // 执行传送
        teleportCooldown.put(playerId, currentTurn);
        return true;
    }
    
    /**
     * 获取传送冷却剩余回合
     */
    public int getTeleportCooldownRemaining(String playerId, int currentTurn) {
        Integer lastUseTurn = teleportCooldown.get(playerId);
        if (lastUseTurn == null) {
            return 0;
        }
        int remaining = TELEPORT_COOLDOWN_TURNS - (currentTurn - lastUseTurn);
        return Math.max(0, remaining);
    }
    
    /**
     * 激活重力井
     * 拦截该Lane的敌方空投
     */
    public void activateGravityWell(String playerId, Lane lane) {
        gravityWellActive.put(playerId + ":" + lane.name(), true);
    }
    
    /**
     * 关闭重力井
     */
    public void deactivateGravityWell(String playerId, Lane lane) {
        gravityWellActive.remove(playerId + ":" + lane.name());
    }
    
    /**
     * 检查重力井是否激活
     */
    public boolean isGravityWellActive(String playerId, Lane lane) {
        return gravityWellActive.getOrDefault(playerId + ":" + lane.name(), false);
    }
    
    /**
     * 拦截空投单位
     * 不会摧毁，而是弹回对方手牌
     * 
     * @return 是否成功拦截
     */
    public boolean interceptAirdrop(String defendingPlayerId, Lane lane, String airdropInstanceId) {
        if (!isGravityWellActive(defendingPlayerId, lane)) {
            return false;
        }
        
        // 拦截逻辑：单位返回手牌
        // 实际实现需要GameStateManager配合
        return true;
    }
    
    /**
     * 空间折叠效果类型
     */
    public enum SlipspaceEffectType {
        INSTANT_TELEPORT,   // 即时传送
        DELAYED_WARP,       // 延迟跃迁（下回合到达）
        MASS_RELOCATION     // 群体重定位（传送多个单位）
    }
    
    /**
     * 计算传送能耗
     * 根据距离和单位数量
     */
    public int calculateTeleportCost(Lane from, Lane to, int unitCount) {
        int distance = Math.abs(from.ordinal() - to.ordinal());
        int baseCost = 2 + distance;
        return baseCost * unitCount;
    }
    
    /**
     * 群体传送
     * 传送整个Lane的所有己方单位
     */
    public boolean canMassRelocate(String playerId, int powerLevel) {
        // 需要至少10能级
        return powerLevel >= 10;
    }
    
    /**
     * 空间干扰
     * 阻止敌方在该Lane进行传送
     */
    private final Map<Lane, Boolean> spatialJamming = new HashMap<>();
    
    public void activateSpatialJamming(Lane lane) {
        spatialJamming.put(lane, true);
    }
    
    public boolean isLaneJammed(Lane lane) {
        return spatialJamming.getOrDefault(lane, false);
    }
}
