package com.haloce.tcg.game.campaign;

import com.haloce.tcg.game.Lane;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 先行者普罗米修斯系统 - 数字化战士
 * Forerunner Promethean Management - Digital Warriors
 * 
 * 核心机制：
 * 1. 数据重组：死亡时掉落数据残影，可被观察者复活
 * 2. 普罗米修斯骑士：携带观察者，可切换形态
 * 3. 爬行者：攀爬关键字，跳过前排
 * 4. 观察者：拦截投射物，复活骑士（每3回合）
 */
public class ForerunnerPrometheanManager {
    
    // 数据残影：instanceId -> (Lane, turnIndex)
    private final Map<String, DataRemnant> dataRemnantByInstance = new HashMap<>();
    
    // 观察者与其守护的骑士的关系
    private final Map<String, String> watcherToKnight = new HashMap<>();
    
    // 骑士携带的观察者
    private final Map<String, String> knightCarriedWatcher = new HashMap<>();
    
    // 观察者复活冷却时间
    private final Map<String, Integer> watcherReviveCooldown = new HashMap<>();
    
    // 爬行者：可以跳过前排的单位
    private final Set<String> crawlerInstances = new HashSet<>();
    
    /**
     * 数据残影记录
     */
    public static record DataRemnant(Lane lane, int deathTurnIndex, String originalUnitId, String playerId) {}
    
    /**
     * 普罗米修斯单位类型
     */
    public enum PrometheanType {
        KNIGHT,   // 骑士：重装单位，可携带观察者
        CRAWLER,  // 爬行者：低级单位，可攀爬
        WATCHER   // 观察者：支援单位，拦截+复活
    }
    
    /**
     * 记录普罗米修斯单位死亡，生成数据残影
     */
    public void recordDeath(String instanceId, Lane lane, int turnIndex, String unitId, String playerId) {
        dataRemnantByInstance.put(instanceId, new DataRemnant(lane, turnIndex, unitId, playerId));
    }
    
    /**
     * 检查是否存在数据残影
     */
    public boolean hasDataRemnant(String instanceId) {
        return dataRemnantByInstance.containsKey(instanceId);
    }
    
    /**
     * 获取数据残影信息
     */
    public DataRemnant getDataRemnant(String instanceId) {
        return dataRemnantByInstance.get(instanceId);
    }
    
    /**
     * 观察者复活序列
     * 每3回合可以重构一个被摧毁的骑士
     */
    public boolean canReviveKnight(String watcherId, int currentTurn) {
        Integer lastReviveTurn = watcherReviveCooldown.get(watcherId);
        if (lastReviveTurn == null) {
            return true;
        }
        return currentTurn - lastReviveTurn >= 3;
    }
    
    /**
     * 执行复活
     */
    public String reviveKnight(String watcherId, String deadKnightId, int currentTurn) {
        DataRemnant remnant = dataRemnantByInstance.remove(deadKnightId);
        if (remnant == null) {
            return null;
        }
        
        watcherReviveCooldown.put(watcherId, currentTurn);
        return remnant.originalUnitId(); // 返回需要重生的单位ID
    }
    
    /**
     * 骑士携带观察者
     */
    public void attachWatcherToKnight(String knightId, String watcherId) {
        knightCarriedWatcher.put(knightId, watcherId);
        watcherToKnight.put(watcherId, knightId);
    }
    
    /**
     * 骑士受损时，观察者弹出维修
     */
    public String ejectWatcher(String knightId) {
        return knightCarriedWatcher.remove(knightId);
    }
    
    /**
     * 检查骑士是否携带观察者
     */
    public boolean hasWatcher(String knightId) {
        return knightCarriedWatcher.containsKey(knightId);
    }
    
    /**
     * 注册爬行者
     */
    public void registerCrawler(String instanceId) {
        crawlerInstances.add(instanceId);
    }
    
    /**
     * 爬行者攀爬能力：可以跳过前排
     */
    public boolean canBypassFrontline(String instanceId) {
        return crawlerInstances.contains(instanceId);
    }
    
    /**
     * 观察者战术拦截
     * 唯一能拦截手雷和实体炮弹的单位
     */
    public boolean canInterceptProjectile(String watcherId) {
        return watcherToKnight.containsKey(watcherId) || knightCarriedWatcher.containsValue(watcherId);
    }
    
    /**
     * 骑士形态切换
     * 重装形态 <-> 机动形态
     */
    public enum KnightStance {
        HEAVY_ASSAULT,  // 重装：高伤害，光刃攻击
        MOBILE         // 机动：高速度，远程
    }
    
    private final Map<String, KnightStance> knightStance = new HashMap<>();
    
    /**
     * 切换骑士形态（无费用）
     */
    public void switchKnightStance(String knightId, KnightStance newStance) {
        knightStance.put(knightId, newStance);
    }
    
    /**
     * 获取当前形态
     */
    public KnightStance getKnightStance(String knightId) {
        return knightStance.getOrDefault(knightId, KnightStance.HEAVY_ASSAULT);
    }
    
    /**
     * 根据形态计算伤害加成
     */
    public double getStanceDamageModifier(KnightStance stance) {
        return switch (stance) {
            case HEAVY_ASSAULT -> 1.3;  // 重装：+30%伤害
            case MOBILE -> 1.0;         // 机动：标准伤害
        };
    }
}
