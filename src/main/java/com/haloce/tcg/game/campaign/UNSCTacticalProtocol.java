package com.haloce.tcg.game.campaign;

import com.haloce.tcg.card.runtime.CardInstance;
import com.haloce.tcg.game.Lane;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UNSCTacticalProtocol {
    private final Map<String, Integer> macChargeByEntity = new HashMap<>();
    private final Map<String, Boolean> coleProtocolBurnedLane = new HashMap<>();

    public SynergyResult checkCombinedArms(CardInstance attacker, List<CardInstance> alliesInLane) {
        if (attacker == null || alliesInLane == null || alliesInLane.isEmpty()) {
            return new SynergyResult(1.0, false);
        }
        UNSCUnitRole attackerRole = roleOf(attacker);
        boolean artilleryOrVessel = attackerRole == UNSCUnitRole.MOBILE_ARTILLERY || attackerRole == UNSCUnitRole.VESSEL;
        boolean hasInfantrySpotter = alliesInLane.stream().anyMatch(unit -> roleOf(unit) == UNSCUnitRole.INFANTRY);
        if (artilleryOrVessel && hasInfantrySpotter) {
            return new SynergyResult(1.2, true);
        }
        return new SynergyResult(1.0, false);
    }

    public boolean checkCombinedArms(String playerId, Lane lane, Object battlefield) {
        // 检查指定Lane中是否有协同作战组合（炮兵/舰船 + 步兵侦察）
        if (!(battlefield instanceof com.haloce.tcg.game.BattlefieldState)) {
            return false;
        }
        
        com.haloce.tcg.game.BattlefieldState bf = (com.haloce.tcg.game.BattlefieldState) battlefield;
        List<CardInstance> unitsInLane = bf.lane(lane).unitsOf(playerId);
        
        if (unitsInLane.isEmpty()) {
            return false;
        }
        
        // 检查是否有炮兵或舰船单位
        boolean hasArtilleryOrVessel = unitsInLane.stream()
            .anyMatch(unit -> {
                UNSCUnitRole role = roleOf(unit);
                return role == UNSCUnitRole.MOBILE_ARTILLERY || role == UNSCUnitRole.VESSEL;
            });
        
        // 检查是否有步兵作为侦察单位
        boolean hasInfantrySpotter = unitsInLane.stream()
            .anyMatch(unit -> roleOf(unit) == UNSCUnitRole.INFANTRY);
        
        return hasArtilleryOrVessel && hasInfantrySpotter;
    }

    public int moraleRechargeMultiplier(List<CardInstance> backlineAllies) {
        if (backlineAllies == null || backlineAllies.isEmpty()) {
            return 1;
        }
        boolean hasCommander = backlineAllies.stream()
                .map(this::roleOf)
                .anyMatch(role -> role == UNSCUnitRole.HERO || role == UNSCUnitRole.OFFICER || role == UNSCUnitRole.SPARTAN);
        return hasCommander ? 2 : 1;
    }

    public int computeMacDamage(int baseDamage, MacTier tier, boolean ignoreShieldTarget) {
        int value = switch (tier) {
            case LIGHT -> (int) Math.ceil(baseDamage * 1.10);
            case HEAVY -> (int) Math.ceil(baseDamage * 1.60);
            case SUPER_HEAVY -> (int) Math.ceil(baseDamage * 2.50);
        };
        if (tier == MacTier.SUPER_HEAVY && ignoreShieldTarget) {
            value += 6;
        }
        return value;
    }

    public boolean chargeMac(String entityId, MacTier tier) {
        if (entityId == null || tier != MacTier.SUPER_HEAVY) {
            return false;
        }
        int turn = macChargeByEntity.getOrDefault(entityId, 0) + 1;
        macChargeByEntity.put(entityId, turn);
        return turn >= 2;
    }

    public void resetMacCharge(String entityId) {
        if (entityId == null) {
            return;
        }
        macChargeByEntity.remove(entityId);
    }

    public boolean applyColeProtocol(String playerId, Lane lane) {
        if (playerId == null || lane == null) {
            return false;
        }
        String key = laneKey(playerId, lane);
        if (coleProtocolBurnedLane.getOrDefault(key, false)) {
            return false;
        }
        coleProtocolBurnedLane.put(key, true);
        return true;
    }

    public boolean isColeProtocolLaneBurned(String playerId, Lane lane) {
        return coleProtocolBurnedLane.getOrDefault(laneKey(playerId, lane), false);
    }

    public UNSCUnitRole roleOf(CardInstance unit) {
        if (unit == null || unit.definition() == null) {
            return UNSCUnitRole.UNKNOWN;
        }
        List<String> tags = unit.definition().tags();
        if (tags == null) {
            return UNSCUnitRole.UNKNOWN;
        }
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String normalized = tag.toUpperCase(Locale.ROOT);
            if (normalized.contains("SPARTAN")) {
                return UNSCUnitRole.SPARTAN;
            }
            if (normalized.contains("HERO")) {
                return UNSCUnitRole.HERO;
            }
            if (normalized.contains("OFFICER")) {
                return UNSCUnitRole.OFFICER;
            }
            if (normalized.contains("INFANTRY") || normalized.contains("MARINE") || normalized.contains("ODST")) {
                return UNSCUnitRole.INFANTRY;
            }
            if (normalized.contains("ARTILLERY") || normalized.contains("SCORPION") || normalized.contains("MOBILE_ARTILLERY")) {
                return UNSCUnitRole.MOBILE_ARTILLERY;
            }
            if (normalized.contains("VESSEL") || normalized.contains("SHIP")) {
                return UNSCUnitRole.VESSEL;
            }
            if (normalized.contains("VEHICLE")) {
                return UNSCUnitRole.VEHICLE;
            }
            if (normalized.contains("SUPPLY_CRATE")) {
                return UNSCUnitRole.SUPPLY_CRATE;
            }
            if (normalized.contains("REPAIR_BAY")) {
                return UNSCUnitRole.REPAIR_BAY;
            }
        }
        return UNSCUnitRole.UNKNOWN;
    }

    private String laneKey(String playerId, Lane lane) {
        return playerId + "@" + lane.name();
    }

    public double getMoraleRechargeMultiplier(String playerId) {
        // Placeholder - default morale recharge rate
        return 1.0;
    }

    public record SynergyResult(double attackMultiplier, boolean ignoreCover) {
    }
}
