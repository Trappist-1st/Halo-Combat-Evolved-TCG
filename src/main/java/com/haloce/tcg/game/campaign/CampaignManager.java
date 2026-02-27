package com.haloce.tcg.game.campaign;

import com.haloce.tcg.game.GameStateManager;
import com.haloce.tcg.game.Lane;

import java.util.HashMap;
import java.util.Map;

/**
 * High-level orchestration for Campaign scenarios (e.g. Battle of Reach).
 * Tracks Story Objectives, Turn Limits, and Scripted Events.
 */
public class CampaignManager {
    private final GameStateManager game;
    private final PopulationManager p1Pop;
    @SuppressWarnings("unused")
    private final PopulationManager p2Pop;
    private final Map<String, FactoryLogic> factories = new HashMap<>(); // Key: sourceInstanceId
    @SuppressWarnings("unused")
    private final OrbitalManager orbital = new OrbitalManager();
    private final CovenantDeployManager covenantDeployManager = new CovenantDeployManager();
    private final CovenantZealotryManager covenantZealotryManager = new CovenantZealotryManager();
    private final CovenantFaithManager covenantFaithManager = new CovenantFaithManager();
    private final CovenantWeaponManager covenantWeaponManager = new CovenantWeaponManager();
    private final CovenantOrbitalDominanceManager covenantOrbitalDominanceManager = new CovenantOrbitalDominanceManager();
    private final UNSCTacticalProtocol unscTacticalProtocol = new UNSCTacticalProtocol();
    private final UNSCDropPodManager unscDropPodManager = new UNSCDropPodManager();
    private final UNSCSalvageManager unscSalvageManager = new UNSCSalvageManager();
    private final SpartanHeroManager spartanHeroManager = new SpartanHeroManager();
    private final UNSCTacticalProtocolExecutor unscProtocolExecutor = new UNSCTacticalProtocolExecutor();
    
    // Forerunner Campaign Managers - 先行者系统
    private final ForerunnerVacuumEnergyManager forerunnerVacuumEnergy = new ForerunnerVacuumEnergyManager();
    private final ForerunnerSentinelNetworkManager forerunnerSentinelNetwork = new ForerunnerSentinelNetworkManager();
    private final ForerunnerPrometheanManager forerunnerPromethean = new ForerunnerPrometheanManager();
    private final ForerunnerHaloArrayManager forerunnerHaloArray = new ForerunnerHaloArrayManager();
    private final ForerunnerSlipspaceManager forerunnerSlipspace = new ForerunnerSlipspaceManager();
    private final ForerunnerHardlightWeaponManager forerunnerHardlight = new ForerunnerHardlightWeaponManager();
    @SuppressWarnings("unused")
    private final ForerunnerComposerManager forerunnerComposer = new ForerunnerComposerManager();

    private int turnLimit;
    private String scenarioName;
    private int currentScenarioTurn = 0;

    public CampaignManager(GameStateManager game) {
        this.game = game;
        this.scenarioName = "SAMPLE_SCENARIO";
        this.turnLimit = 20;
        
        // Assume default max pop of 20 for campaign
        this.p1Pop = new PopulationManager(20);
        this.p2Pop = new PopulationManager(20);
    }
    
    // Called by GameStateManager on ROUND_STARTED
    public void onTurnStart(String activePlayerId) {
        currentScenarioTurn = game.globalTurnIndex();

        // 1. Process Factories
        for (FactoryLogic factory : factories.values()) {
            // Check if factory belongs to active player
            // Simplified: tick cooldown for all factories
            factory.onTurnStart();
            
            if (factory.isReady()) {
                String spawnId = factory.produce();
                spawnToken(spawnId, factory);
            }
        }
        
        // 2. Scripted Events (e.g. "Long Night of Solace" logic)
        checkScriptedEvents();
    }

    private void spawnToken(String spawnId, FactoryLogic source) {
        // Find suitable Lane + Slot
        // Assume default spawn on Source's Lane
        Lane lane = Lane.ALPHA; // Placeholder: locate source lane
        boolean isOrbital = false; // Placeholder
        
        PopulationManager pop = getPopFor(source); 
        
        // Interception Check BEFORE spawn
        // List<CardInstance> enemyOrbit = ...;
        // if (orbital.checkInterception(lane, owner, enemyOrbit)) { 
        //    log("Interception! Token destroyed in transit.");
        //    return; 
        // }
        
        if (pop.canAddUnit(1, lane, isOrbital)) {
             // game.deployToken(owner, lane, spawnId);
             // pop.addUnit(1, lane, isOrbital);
        }
    }

    private void checkScriptedEvents() {
        if (scenarioName.equals("BATTLE_OF_REACH")) {
            if (currentScenarioTurn % 10 == 0) {
                 // Reinforce Covenant fleet
                 // spawnEntity("VES-COV-CSO", Lane.ORBIT, "P2");
            }
        }
    }
    
    private PopulationManager getPopFor(FactoryLogic factory) {
        // owner logic
        return p1Pop; 
    }

    public CovenantDeployManager covenantDeploy() {
        return covenantDeployManager;
    }

    public CovenantZealotryManager covenantZealotry() {
        return covenantZealotryManager;
    }

    public CovenantFaithManager covenantFaith() {
        return covenantFaithManager;
    }

    public CovenantWeaponManager covenantWeapon() {
        return covenantWeaponManager;
    }

    public CovenantOrbitalDominanceManager covenantOrbital() {
        return covenantOrbitalDominanceManager;
    }

    public UNSCTacticalProtocol unscTactical() {
        return unscTacticalProtocol;
    }

    public UNSCDropPodManager unscDropPod() {
        return unscDropPodManager;
    }

    public UNSCSalvageManager unscSalvage() {
        return unscSalvageManager;
    }

    public SpartanHeroManager spartanHero() {
        return spartanHeroManager;
    }

    public UNSCTacticalProtocolExecutor unscProtocolExecutor() {
        return unscProtocolExecutor;
    }
    
    // Forerunner accessors - 先行者访问器
    public ForerunnerVacuumEnergyManager forerunnerVacuumEnergy() {
        return forerunnerVacuumEnergy;
    }
    
    public ForerunnerSentinelNetworkManager forerunnerSentinelNetwork() {
        return forerunnerSentinelNetwork;
    }
    
    public ForerunnerPrometheanManager forerunnerPromethean() {
        return forerunnerPromethean;
    }
    
    public ForerunnerHaloArrayManager forerunnerHaloArray() {
        return forerunnerHaloArray;
    }
    
    public ForerunnerSlipspaceManager forerunnerSlipspace() {
        return forerunnerSlipspace;
    }
    
    public ForerunnerHardlightWeaponManager forerunnerHardlight() {
        return forerunnerHardlight;
    }
    
    public ForerunnerComposerManager forerunnerComposer() {
        return forerunnerComposer;
    }
}
