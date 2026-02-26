package com.haloce.tcg.game.campaign;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 先行者硬光武器系统
 * Forerunner Hardlight Weaponry - Penetrating Light Constructs
 * 
 * 核心机制：
 * 1. 穿透攻击：对前排单位攻击时，30%余威伤害穿透到后排
 * 2. 硬光护盾：可叠加的能量护盾
 * 3. 光刃：近战范围的高伤害武器
 */
public class ForerunnerHardlightWeaponManager {
    
    // 配备硬光武器的单位
    private final Set<String> hardlightEquippedUnits = new HashSet<>();
    
    // 硬光护盾层数 (instanceId -> 护盾层数)
    private final Map<String, Integer> hardlightShieldLayers = new HashMap<>();
    
    // 光刃充能状态
    private final Map<String, Boolean> lightbladeCharged = new HashMap<>();
    
    // 穿透伤害比例
    private static final double PENETRATION_RATE = 0.3;
    
    // 最大护盾层数
    private static final int MAX_SHIELD_LAYERS = 3;
    
    /**
     * 装备硬光武器
     */
    public void equipHardlightWeapon(String instanceId) {
        hardlightEquippedUnits.add(instanceId);
    }
    
    /**
     * 检查是否配备硬光武器
     */
    public boolean hasHardlightWeapon(String instanceId) {
        return hardlightEquippedUnits.contains(instanceId);
    }
    
    /**
     * 计算穿透伤害
     * 攻击前排时，部分伤害穿透到后排
     * 
     * @param damage 对前排造成的伤害
     * @return 穿透到后排的伤害
     */
    public int calculatePenetrationDamage(int damage) {
        return (int) Math.ceil(damage * PENETRATION_RATE);
    }
    
    /**
     * 应用穿透效果
     * @return 穿透伤害值
     */
    public int applyPenetration(String attackerInstanceId, int frontlineDamage) {
        if (!hasHardlightWeapon(attackerInstanceId)) {
            return 0;
        }
        return calculatePenetrationDamage(frontlineDamage);
    }
    
    /**
     * 添加硬光护盾层
     */
    public boolean addShieldLayer(String instanceId) {
        int current = hardlightShieldLayers.getOrDefault(instanceId, 0);
        if (current >= MAX_SHIELD_LAYERS) {
            return false;
        }
        hardlightShieldLayers.put(instanceId, current + 1);
        return true;
    }
    
    /**
     * 移除护盾层（受到攻击时）
     */
    public boolean removeShieldLayer(String instanceId) {
        Integer layers = hardlightShieldLayers.get(instanceId);
        if (layers == null || layers <= 0) {
            return false;
        }
        hardlightShieldLayers.put(instanceId, layers - 1);
        return true;
    }
    
    /**
     * 获取当前护盾层数
     */
    public int getShieldLayers(String instanceId) {
        return hardlightShieldLayers.getOrDefault(instanceId, 0);
    }
    
    /**
     * 护盾是否激活
     */
    public boolean hasActiveShield(String instanceId) {
        return getShieldLayers(instanceId) > 0;
    }
    
    /**
     * 光刃充能
     */
    public void chargeLightblade(String instanceId) {
        lightbladeCharged.put(instanceId, true);
    }
    
    /**
     * 光刃放电（攻击后）
     */
    public void dischargeLightblade(String instanceId) {
        lightbladeCharged.put(instanceId, false);
    }
    
    /**
     * 检查光刃是否充能
     */
    public boolean isLightbladeCharged(String instanceId) {
        return lightbladeCharged.getOrDefault(instanceId, false);
    }
    
    /**
     * 光刃伤害加成
     * 充能状态下的近战攻击伤害翻倍
     */
    public double getLightbladeDamageMultiplier(String instanceId) {
        return isLightbladeCharged(instanceId) ? 2.0 : 1.0;
    }
    
    /**
     * 硬光武器类型
     */
    public enum HardlightWeaponType {
        SUPPRESSOR,      // 压制者：连射武器，低伤害高射速
        BOLTSHOT,        // 脉冲枪：中距离精准打击
        LIGHTRIFLE,      // 光束步枪：高精度，可切换模式
        SCATTERSHOT,     // 散射枪：近距离高伤害
        INCINERATION_CANNON  // 焚化炮：重型武器，爆炸伤害
    }
    
    private final Map<String, HardlightWeaponType> weaponTypeByInstance = new HashMap<>();
    
    /**
     * 设置武器类型
     */
    public void setWeaponType(String instanceId, HardlightWeaponType type) {
        weaponTypeByInstance.put(instanceId, type);
    }
    
    /**
     * 获取武器类型
     */
    public HardlightWeaponType getWeaponType(String instanceId) {
        return weaponTypeByInstance.getOrDefault(instanceId, HardlightWeaponType.SUPPRESSOR);
    }
    
    /**
     * 根据武器类型计算伤害修正
     */
    public double getWeaponDamageModifier(HardlightWeaponType type, boolean isRangedAttack) {
        return switch (type) {
            case SUPPRESSOR -> isRangedAttack ? 0.8 : 0.6;
            case BOLTSHOT -> isRangedAttack ? 1.2 : 1.5;
            case LIGHTRIFLE -> isRangedAttack ? 1.3 : 0.9;
            case SCATTERSHOT -> isRangedAttack ? 0.7 : 1.8;
            case INCINERATION_CANNON -> 2.0;
        };
    }
    
    /**
     * 硬光构造物稳定性
     * 护盾在受到EMP攻击时会额外损失1层
     */
    public boolean isVulnerableToEMP(String instanceId) {
        return hasActiveShield(instanceId);
    }
}
