package com.haloce.tcg.game.handlers;

import com.haloce.tcg.card.loader.CardRepository;
import com.haloce.tcg.card.model.CardDef;
import com.haloce.tcg.card.model.Stats;
import com.haloce.tcg.card.runtime.CardInstance;
import com.haloce.tcg.combat.DamageContext;
import com.haloce.tcg.combat.DamageResolver;
import com.haloce.tcg.combat.DamageResult;
import com.haloce.tcg.combat.DamageType;
import com.haloce.tcg.combat.EntityCombatState;
import com.haloce.tcg.combat.InMemoryCombatStateStore;
import com.haloce.tcg.core.event.EventBus;
import com.haloce.tcg.core.event.EventType;
import com.haloce.tcg.core.event.GameEvent;
import com.haloce.tcg.game.BattlefieldState;
import com.haloce.tcg.game.GameRow;
import com.haloce.tcg.game.Lane;
import com.haloce.tcg.game.PlayerState;
import com.haloce.tcg.game.UnitPosition;
import com.haloce.tcg.game.UnitStatus;
import com.haloce.tcg.game.UnitStatusStore;
import com.haloce.tcg.game.campaign.CampaignManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Handles combat and attack logic with faction-specific integrations
 */
public class CombatHandler {
    private final EventBus eventBus;
    private final CardRepository cardRepository;
    private final BattlefieldState battlefield;
    private final InMemoryCombatStateStore combatStateStore;
    private final UnitStatusStore unitStatusStore;
    private final DamageResolver damageResolver;
    private final CampaignManager campaignManager;

    public CombatHandler(
            EventBus eventBus,
            CardRepository cardRepository,
            BattlefieldState battlefield,
            InMemoryCombatStateStore combatStateStore,
            UnitStatusStore unitStatusStore,
            DamageResolver damageResolver,
            CampaignManager campaignManager
    ) {
        this.eventBus = eventBus;
        this.cardRepository = cardRepository;
        this.battlefield = battlefield;
        this.combatStateStore = combatStateStore;
        this.unitStatusStore = unitStatusStore;
        this.damageResolver = damageResolver;
        this.campaignManager = campaignManager;
    }

    public DamageResult declareAttack(
            String attackerInstanceId,
            String defenderInstanceId,
            String activePlayerId,
            int globalTurnIndex,
            int roundIndex,
            long eventSequence,
            Set<String> attackersUsedThisTurn,
            java.util.function.BiPredicate<String, String> areOpponents,
            java.util.function.Function<String, PlayerState> playerStateAccessor
    ) {
        UnitPosition attackerPos = requirePosition(attackerInstanceId, "attacker");
        UnitPosition defenderPos = requirePosition(defenderInstanceId, "defender");

        if (!attackerPos.playerId().equals(activePlayerId)) {
            throw new IllegalStateException("Only active player's units can attack");
        }
        if (!areOpponents.test(attackerPos.playerId(), defenderPos.playerId())) {
            throw new IllegalArgumentException("Target is not an opponent");
        }
        if (attackersUsedThisTurn.contains(attackerInstanceId)) {
            throw new IllegalStateException("This unit already attacked this turn");
        }
        if (attackerPos.lane() != defenderPos.lane()) {
            throw new IllegalArgumentException("Attacker and defender must be in the same lane");
        }

        ensureCanAttack(attackerPos.card(), globalTurnIndex);

        boolean attackerRanged = HandlerUtils.hasKeyword(attackerPos.card(), "RANGED");
        int defenderFrontlineCount = battlefield.lane(defenderPos.lane()).frontlineCount(defenderPos.playerId());
        if (defenderPos.row() == GameRow.BACKLINE && defenderFrontlineCount > 0 && !attackerRanged) {
            throw new IllegalStateException("Must target frontline first unless attacker has RANGED");
        }

        int baseDamage = HandlerUtils.attackValue(attackerPos.card());
        baseDamage += squadBonus(attackerPos);

        // Campaign integration: UNSC Combined Arms
        if (campaignManager.unscTactical().checkCombinedArms(attackerPos.playerId(), attackerPos.lane(), battlefield)) {
            baseDamage = (int) Math.ceil(baseDamage * 1.2);
            emit(EventType.UNSC_COMBINED_ARMS_TRIGGERED, attackerPos.playerId(), globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of(
                    "attackerInstanceId", attackerInstanceId,
                    "lane", attackerPos.lane().name(),
                    "bonusDamage", (int) (baseDamage * 0.2)
            ));
        }

        // Campaign integration: Covenant Weapon overwhelming firepower
        if (campaignManager.covenantWeapon().hasOverwhelmingFirepower(attackerPos.playerId())) {
            baseDamage = (int) Math.ceil(baseDamage * 1.25);
        }
        
        // Campaign integration: Forerunner Hardlight Weapon damage modifier (硬光武器加成)
        if (campaignManager.forerunnerHardlight().hasHardlightWeapon(attackerInstanceId)) {
            var weaponType = campaignManager.forerunnerHardlight().getWeaponType(attackerInstanceId);
            boolean isRanged = HandlerUtils.hasKeyword(attackerPos.card(), "RANGED");
            double modifier = campaignManager.forerunnerHardlight().getWeaponDamageModifier(weaponType, isRanged);
            baseDamage = (int) Math.ceil(baseDamage * modifier);
        }

        if (baseDamage <= 0) {
            throw new IllegalStateException("Attacker has no valid attack value");
        }

        emit(EventType.ATTACK_DECLARED, activePlayerId, globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of(
                "attackerInstanceId", attackerInstanceId,
                "defenderInstanceId", defenderInstanceId,
                "lane", attackerPos.lane().name()
        ));
        emit(EventType.TARGET_LOCKED, activePlayerId, globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of(
                "attackerInstanceId", attackerInstanceId,
                "defenderInstanceId", defenderInstanceId
        ));

        DamageType damageType = inferDamageType(attackerPos.card());
        int finalDamageMultiplier = 1;
        UnitStatus defenderStatus = unitStatusStore.getOrCreate(defenderInstanceId);
        EntityCombatState defenderCombatState = combatStateStore.get(defenderInstanceId);

        if (HandlerUtils.hasKeyword(attackerPos.card(), "HEADSHOT") && defenderCombatState.currentShield() <= 0) {
            finalDamageMultiplier *= 2;
            emit(EventType.HEADSHOT_TRIGGERED, activePlayerId, globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of(
                    "attackerInstanceId", attackerInstanceId,
                    "defenderInstanceId", defenderInstanceId
            ));
        }

        if (damageType == DamageType.BALLISTIC
                && defenderStatus.plasmaTaggedTurnIndex() == globalTurnIndex
                && defenderStatus.noobComboTriggeredTurnIndex() != globalTurnIndex) {
            finalDamageMultiplier *= 2;
            defenderStatus.setNoobComboTriggeredTurnIndex(globalTurnIndex);
        }

        boolean ignoreShield = HandlerUtils.hasKeyword(attackerPos.card(), "SENTINEL");

        DamageResult result = damageResolver.resolve(
                new DamageContext(attackerInstanceId, defenderInstanceId, baseDamage, damageType, false, false, finalDamageMultiplier, ignoreShield),
                globalTurnIndex,
                roundIndex,
                activePlayerId
        );

        UnitStatus attackerStatus = unitStatusStore.getOrCreate(attackerInstanceId);
        attackerStatus.setAttackedTurnIndex(globalTurnIndex);
        attackerStatus.setHasCamoThisTurn(false);

        attackersUsedThisTurn.add(attackerInstanceId);

        if (result.shieldDamage() > 0 || result.healthDamage() > 0) {
            defenderStatus.markDamaged(globalTurnIndex, activePlayerId);
            if (damageType == DamageType.PLASMA) {
                defenderStatus.setPlasmaTaggedTurnIndex(globalTurnIndex);
                emit(EventType.PLASMA_TAG_APPLIED, activePlayerId, globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of(
                        "targetInstanceId", defenderInstanceId,
                        "turnIndex", globalTurnIndex
                ));
            }
        }

        if (result.shieldDamage() > 0 || result.healthDamage() > 0) {
            applyEmpIfNeeded(attackerPos.card(), defenderPos.card(), defenderInstanceId, activePlayerId, globalTurnIndex, roundIndex, eventSequence);
        }

        if (result.lethal()) {
            handleUnitDeath(defenderInstanceId, attackerPos, defenderPos, activePlayerId, globalTurnIndex, roundIndex, eventSequence, playerStateAccessor);
        }
        
        // Campaign integration: Forerunner Hardlight Penetration (硬光穿透)
        if (!result.lethal() && defenderPos.row() == GameRow.FRONTLINE) {
            int penetrationDamage = campaignManager.forerunnerHardlight().applyPenetration(
                attackerInstanceId, result.healthDamage()
            );
            if (penetrationDamage > 0) {
                // 穿透到后排单位 - 需要找到后排目标
                emit(EventType.HARDLIGHT_PENETRATION_TRIGGERED, attackerPos.playerId(), globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of(
                    "attackerInstanceId", attackerInstanceId,
                    "penetrationDamage", penetrationDamage,
                    "lane", attackerPos.lane().name()
                ));
            }
        }

        return result;
    }

    public void attackBase(
            String attackerInstanceId,
            String targetPlayerId,
            String activePlayerId,
            int globalTurnIndex,
            int roundIndex,
            long eventSequence,
            Set<String> attackersUsedThisTurn,
            java.util.function.BiPredicate<String, String> areOpponents,
            java.util.function.Function<String, List<String>> alliesOfFunc,
            java.util.function.BiConsumer<String, Integer> damageBaseFunc
    ) {
        UnitPosition attackerPos = requirePosition(attackerInstanceId, "attacker");
        if (!attackerPos.playerId().equals(activePlayerId)) {
            throw new IllegalStateException("Only active player's units can attack");
        }
        if (!areOpponents.test(attackerPos.playerId(), targetPlayerId)) {
            throw new IllegalArgumentException("Target player is not an opponent");
        }
        if (attackersUsedThisTurn.contains(attackerInstanceId)) {
            throw new IllegalStateException("This unit already attacked this turn");
        }

        ensureCanAttack(attackerPos.card(), globalTurnIndex);

        List<String> defenderSide = alliesOfFunc.apply(targetPlayerId);
        int blockers = battlefield.laneUnitCount(attackerPos.lane(), defenderSide);
        if (blockers > 0) {
            throw new IllegalStateException("Cannot attack base while defenders remain in lane");
        }

        int damage = HandlerUtils.attackValue(attackerPos.card());

        // Campaign integration: UNSC Combined Arms
        if (campaignManager.unscTactical().checkCombinedArms(attackerPos.playerId(), attackerPos.lane(), battlefield)) {
            damage = (int) Math.ceil(damage * 1.2);
        }

        if (damage <= 0) {
            throw new IllegalStateException("Attacker has no valid attack value");
        }

        emit(EventType.ATTACK_DECLARED, activePlayerId, globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of(
                "attackerInstanceId", attackerInstanceId,
                "targetPlayerId", targetPlayerId,
                "target", "BASE"
        ));

        damageBaseFunc.accept(targetPlayerId, damage);
        UnitStatus attackerStatus = unitStatusStore.getOrCreate(attackerInstanceId);
        attackerStatus.setAttackedTurnIndex(globalTurnIndex);
        attackerStatus.setHasCamoThisTurn(false);
        attackersUsedThisTurn.add(attackerInstanceId);
    }

    public void hijackVehicle(
            String hijackerInstanceId,
            String targetVehicleInstanceId,
            String activePlayerId,
            int globalTurnIndex,
            int roundIndex,
            long eventSequence,
            PlayerState activePlayer,
            java.util.function.BiPredicate<String, String> areOpponents
    ) {
        UnitPosition hijackerPos = requirePosition(hijackerInstanceId, "hijacker");
        UnitPosition targetPos = requirePosition(targetVehicleInstanceId, "targetVehicle");

        if (!hijackerPos.playerId().equals(activePlayerId)) {
            throw new IllegalStateException("Only active player's units can execute hijack");
        }
        if (!areOpponents.test(hijackerPos.playerId(), targetPos.playerId())) {
            throw new IllegalArgumentException("Target is not an opponent vehicle");
        }
        if (hijackerPos.lane() != targetPos.lane()) {
            throw new IllegalArgumentException("Hijacker and target vehicle must be in the same lane");
        }
        if (!HandlerUtils.hasKeyword(hijackerPos.card(), "HIJACK")) {
            throw new IllegalStateException("Hijacker does not have HIJACK keyword");
        }
        if (!HandlerUtils.isVehicle(targetPos.card())) {
            throw new IllegalArgumentException("Target is not a vehicle");
        }

        if (!battlefield.hasSpace(hijackerPos.lane(), hijackerPos.playerId(), targetPos.row())) {
            throw new IllegalStateException("No space to seize target vehicle at this row");
        }

        // Campaign integration: Spartan hijack probability
        boolean succeeded = campaignManager.spartanHero().attemptHijack(hijackerInstanceId, targetVehicleInstanceId);
        if (!succeeded) {
            emit(EventType.SPARTAN_HIJACK_ATTEMPTED, activePlayerId, globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of(
                    "hijackerInstanceId", hijackerInstanceId,
                    "targetVehicleInstanceId", targetVehicleInstanceId,
                    "result", "FAILED"
            ));
            return;
        }

        if (!activePlayer.spendResources(2, 0)) {
            throw new IllegalStateException("Insufficient supply to execute hijack");
        }

        CardInstance removed = battlefield.removeUnit(targetVehicleInstanceId)
                .orElseThrow(() -> new IllegalStateException("Failed to remove target vehicle for hijack"));
        CardInstance seized = new CardInstance(
                removed.instanceId(),
                removed.definition(),
                hijackerPos.playerId(),
                removed.sourceEventSequence(),
                removed.sourceCardId()
        );
        battlefield.deploy(hijackerPos.playerId(), hijackerPos.lane(), targetPos.row(), seized);

        emit(EventType.HIJACK_EXECUTED, hijackerPos.playerId(), globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of(
                "hijackerInstanceId", hijackerInstanceId,
                "targetVehicleInstanceId", targetVehicleInstanceId,
                "lane", hijackerPos.lane().name(),
                "row", targetPos.row().name()
        ));
        emit(EventType.SPARTAN_HIJACK_SUCCEEDED, activePlayerId, globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of(
                "hijackerInstanceId", hijackerInstanceId,
                "targetVehicleInstanceId", targetVehicleInstanceId
        ));
    }

    private void handleUnitDeath(
            String defenderInstanceId,
            UnitPosition attackerPos,
            UnitPosition defenderPos,
            String activePlayerId,
            int globalTurnIndex,
            int roundIndex,
            long eventSequence,
            java.util.function.Function<String, PlayerState> playerStateAccessor
    ) {
        CardInstance dead = battlefield.removeUnit(defenderInstanceId)
                .orElseThrow(() -> new IllegalStateException("Failed to remove defeated unit"));
        combatStateStore.remove(defenderInstanceId);
        unitStatusStore.remove(defenderInstanceId);

        // Campaign integration: Spartan MIA system
        if (HandlerUtils.hasTag(dead, "SPARTAN")) {
            campaignManager.spartanHero().enterMIA(dead.ownerPlayerId(), dead.instanceId(), globalTurnIndex);
            emit(EventType.SPARTAN_MIA_ENTERED, dead.ownerPlayerId(), globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of(
                    "spartanInstanceId", dead.instanceId(),
                    "recoveryTurn", globalTurnIndex + 4
            ));
        } else {
            // Normal death - to discard
            PlayerState owner = playerStateAccessor.apply(dead.ownerPlayerId());
            owner.putToDiscard(dead);
        }

        // Campaign integration: Covenant Faith points
        campaignManager.covenantFaith().recordKill(attackerPos.playerId(), dead.definition().id());

        // Campaign integration: Flood Infection
        resolveOnKillTriggers(attackerPos.card(), dead, attackerPos.lane(), activePlayerId, globalTurnIndex, roundIndex, eventSequence);

        // Campaign integration: UNSC Salvage
        if (HandlerUtils.hasTag(dead, "VEHICLE")) {
            campaignManager.unscSalvage().recordVehicleDestruction(attackerPos.playerId(), dead.instanceId());
        }
        
        // Campaign integration: Forerunner Matter Reconfiguration (物质重组)
        if (HandlerUtils.hasTag(dead, "FORERUNNER")) {
            int refund = campaignManager.forerunnerVacuumEnergy().reconfigureMatter(
                dead.ownerPlayerId(), dead.instanceId()
            );
            if (refund > 0) {
                emit(EventType.FORERUNNER_MATTER_RECONFIGURED, dead.ownerPlayerId(), globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of(
                    "instanceId", dead.instanceId(),
                    "energyRefund", refund
                ));
            }
        }
        
        // Campaign integration: Forerunner Promethean Data Remnant (数据残影)
        if (HandlerUtils.hasTag(dead, "PROMETHEAN")) {
            campaignManager.forerunnerPromethean().recordDeath(
                dead.instanceId(), defenderPos.lane(), globalTurnIndex,
                dead.definition().id(), dead.ownerPlayerId()
            );
            emit(EventType.PROMETHEAN_DATA_REMNANT_CREATED, dead.ownerPlayerId(), globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of(
                "instanceId", dead.instanceId(),
                "lane", defenderPos.lane().name()
            ));
        }
        
        // Campaign integration: Forerunner Composer - Collect biological data (合成器收集数据)
        if (!HandlerUtils.hasTag(dead, "FORERUNNER") && !HandlerUtils.hasTag(dead, "VEHICLE")) {
            // 攻击方如果是先行者，收集生物数据
            if (HandlerUtils.hasTag(attackerPos.card(), "FORERUNNER")) {
                campaignManager.forerunnerComposer().collectBiologicalData(
                    attackerPos.playerId(), dead.definition().id()
                );
            }
        }
    }

    private void resolveOnKillTriggers(CardInstance attacker, CardInstance deadDefender, Lane lane, String activePlayerId, int globalTurnIndex, int roundIndex, long eventSequence) {
        if (!HandlerUtils.hasKeyword(attacker, "INFECT") || HandlerUtils.isVehicle(deadDefender)) {
            return;
        }

        if (!battlefield.hasSpace(lane, attacker.ownerPlayerId(), GameRow.BACKLINE)) {
            return;
        }

        Optional<CardDef> tokenOpt = Optional.ofNullable(cardRepository.get("TOKEN-COMBAT-FORM"));
        if (tokenOpt.isEmpty()) {
            return;
        }

        CardDef tokenDef = tokenOpt.get();
        CardInstance token = new CardInstance(
                UUID.randomUUID().toString(),
                tokenDef,
                attacker.ownerPlayerId(),
                eventSequence,
                attacker.definition().id()
        );
        battlefield.deploy(attacker.ownerPlayerId(), lane, GameRow.BACKLINE, token);

        Stats stats = token.definition().stats();
        if (stats != null) {
            combatStateStore.put(token.instanceId(), new EntityCombatState(stats.shieldCap(), stats.healthCap()));
        }
        UnitStatus status = unitStatusStore.getOrCreate(token.instanceId());
        status.setSummonedTurnIndex(globalTurnIndex);
        status.setHasCamoThisTurn(HandlerUtils.hasKeyword(token, "CAMO"));

        emit(EventType.INFECT_TRIGGERED, attacker.ownerPlayerId(), globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of(
                "sourceInstanceId", attacker.instanceId(),
                "tokenId", token.definition().id(),
                "tokenInstanceId", token.instanceId(),
                "lane", lane.name(),
                "row", GameRow.BACKLINE.name()
        ));

        // Campaign integration: Flood Biomass
        campaignManager.covenantFaith().recordKill(attacker.ownerPlayerId(), deadDefender.definition().id());
    }

    private void applyEmpIfNeeded(CardInstance attacker, CardInstance defender, String defenderInstanceId, String activePlayerId, int globalTurnIndex, int roundIndex, long eventSequence) {
        if (!HandlerUtils.hasKeyword(attacker, "EMP") || !HandlerUtils.isVehicle(defender)) {
            return;
        }
        UnitStatus defenderStatus = unitStatusStore.getOrCreate(defenderInstanceId);
        defenderStatus.setCannotAttackUntilTurn(globalTurnIndex + 1);
        defenderStatus.setCannotMoveUntilTurn(globalTurnIndex + 1);

        emit(EventType.EMP_APPLIED, activePlayerId, globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of(
                "targetInstanceId", defenderInstanceId,
                "cannotAttackUntilTurn", defenderStatus.cannotAttackUntilTurn(),
                "cannotMoveUntilTurn", defenderStatus.cannotMoveUntilTurn()
        ));
    }

    private void ensureCanAttack(CardInstance attacker, int globalTurnIndex) {
        UnitStatus status = unitStatusStore.getOrCreate(attacker.instanceId());
        if (status.summonedTurnIndex() == globalTurnIndex && !HandlerUtils.hasKeyword(attacker, "DROP_POD")) {
            throw new IllegalStateException("Unit has summoning sickness this turn");
        }
        if (status.cannotAttackUntilTurn() >= globalTurnIndex) {
            throw new IllegalStateException("Unit cannot attack at this turn");
        }
    }

    private int squadBonus(UnitPosition attackerPos) {
        if (!HandlerUtils.hasKeyword(attackerPos.card(), "SQUAD") || !HandlerUtils.isInfantry(attackerPos.card())) {
            return 0;
        }
        List<CardInstance> alliesInLane = battlefield.lane(attackerPos.lane()).unitsOf(attackerPos.playerId());
        long otherInfantry = alliesInLane.stream()
                .filter(unit -> !unit.instanceId().equals(attackerPos.card().instanceId()))
                .filter(HandlerUtils::isInfantry)
                .count();
        return Math.min(2, (int) otherInfantry);
    }

    private DamageType inferDamageType(CardInstance card) {
        if (HandlerUtils.hasKeyword(card, "PLASMA")) {
            return DamageType.PLASMA;
        }
        if (HandlerUtils.hasKeyword(card, "BALLISTIC")) {
            return DamageType.BALLISTIC;
        }
        return DamageType.TRUE;
    }

    private UnitPosition requirePosition(String instanceId, String role) {
        UnitPosition position = battlefield.locateUnit(instanceId);
        if (position == null) {
            throw new IllegalArgumentException("Cannot find " + role + " unit: " + instanceId);
        }
        return position;
    }

    private void emit(EventType type, String sourcePlayerId, int globalTurnIndex, int roundIndex, String activePlayerId, long eventSequence, Map<String, Object> payload) {
        GameEvent event = new GameEvent(
                eventSequence,
                type,
                globalTurnIndex,
                roundIndex,
                activePlayerId,
                sourcePlayerId,
                null,
                null,
                null,
                null,
                null,
                payload
        );
        eventBus.publish(event);
        eventBus.processQueue();
    }

}
