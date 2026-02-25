# Halo CE TCG (Java Skeleton)

This workspace contains design docs under `docs/` and a Java skeleton implementation for:
- Event bus and listener pipeline
- Damage resolution pipeline
- Modifier stack support for temporary/permanent effects
- Game engine bootstrap for 1v1 / FFA / 2v2 match state
- Global game state manager (turn/round/phase/player/battlefield/win check)
- Player state management (base HP, deck/hand/discard, supply/battery)
- TCP network game server/client for remote multiplayer sync

## Build

```bash
mvn -q -DskipTests package
```

## Package Root

`com.haloce.tcg`

## New Game Modules

- `com.haloce.tcg.game.GameEngine`
	- Initializes DUEL/FFA/TEAM_2V2 match from validated deck definitions
	- Builds runtime deck instances and shuffles deterministically (seeded random)
- `com.haloce.tcg.game.GameStateManager`
	- Owns global turn/round/phase state and active player cursor
	- Drives full cycle: `DRAW_RECHARGE -> DEPLOYMENT -> SKIRMISH -> ENDSTEP`
	- Publishes lifecycle/resource events through `EventBus`
	- Tracks lane control and checks win conditions
- `com.haloce.tcg.game.TurnExecutor`
	- Exposes `advancePhase()` for explicit phase progression
- `com.haloce.tcg.game.PlayerState`
	- Manages base HP, deck/hand/discard, supply cap/current supply, battery
	- Supports draw, spend resources, and once-per-turn battery conversion
- `com.haloce.tcg.game.BattlefieldState`
	- 3 lanes (`ALPHA/BRAVO/CHARLIE`)
	- Per-player frontline/backline row capacity (`2 + 2`)
- `com.haloce.tcg.net.NetworkGameServer`
	- JSON-over-TCP command server
	- Room-based routing (`CREATE_ROOM/LIST_ROOMS/JOIN_ROOM`) and state broadcast
- `com.haloce.tcg.net.RoomManager`
	- Multi-room lifecycle and room summaries
- `com.haloce.tcg.game.UnitStatusStore`
	- Unit status markers for summoning sickness and combo tagging
- `com.haloce.tcg.net.NetworkGameClient`
	- Lightweight Java client SDK for integration test or bot

## Quick Run

```bash
mvn -q -DskipTests package
java -cp target/classes com.haloce.tcg.App
```

`App` currently demonstrates:
- card/deck loading and validation
- creating a DUEL match
- entering the first turn with initialized game state and phase

Start network server:

```bash
java -cp target/classes com.haloce.tcg.App --server 19110
```

## Testing Gameplay

Since there's no frontend UI yet, you can test gameplay in several ways:

### 1. Local Game Test
Run the built-in gameplay test to see basic game mechanics:

```bash
java -cp target/classes com.haloce.tcg.test.GamePlayTest
```

This demonstrates:
- Card loading and deck validation
- Game initialization
- Turn phases (Draw/Recharge → Deployment → Skirmish → Endstep)
- Unit deployment
- Combat resolution
- Turn transitions

### 2. Network Multiplayer Test
Start the server first, then run the network test:

```bash
# Terminal 1: Start server
java -cp target/classes com.haloce.tcg.App --server 19110

# Terminal 2: Run network test
java -cp target/classes com.haloce.tcg.test.NetworkGameTest
```

This demonstrates:
- Client-server connection
- Room creation and joining
- Basic command sending
- State synchronization

### 3. Manual Network Testing with Telnet
You can manually send JSON commands using telnet:

```bash
telnet localhost 19110
```

Then send JSON commands like:
```json
{"type":"LIST_ROOMS","payload":{}}
{"type":"CREATE_ROOM","payload":{"roomId":"my-room","mode":"DUEL_1V1","playerIds":["P1","P2"]}}
{"type":"JOIN_ROOM","payload":{"roomId":"my-room","playerId":"P1"}}
{"type":"STATE","payload":{}}
```

### 4. Custom Test Scripts
Create your own test classes in `src/main/java/com/haloce/tcg/test/` to test specific mechanics like:
- EMP effects on vehicles
- HIJACK vehicle capture
- INFECT token spawning
- HEADSHOT damage doubling
- SQUAD unit bonuses

## Command Protocol

JSON-over-TCP commands:
- `LIST_ROOMS`
- `CREATE_ROOM { roomId, mode, playerIds, teamByPlayer }`
- `JOIN_ROOM { roomId, playerId }`
- `RECONNECT { roomId, playerId }` (alias of JOIN_ROOM for reconnect flow)
- `LEAVE_ROOM`
- `JOIN { playerId }` (join first room for compatibility)
- `STATE` (for bound room)
- `ADVANCE_PHASE`
- `DEPLOY { cardInstanceId, lane, row }`
- `CONVERT_BATTERY { cardInstanceId }`
- `ATTACK { attackerInstanceId, defenderInstanceId }`
- `ATTACK_BASE { attackerInstanceId, targetPlayerId }`
- `HIJACK { hijackerInstanceId, targetVehicleInstanceId }`
- `END_TURN`

Mutating commands support optional `seq` at command root for idempotency replay.

Detailed design notes: `docs/Halo_CE_TCG_游戏引擎与状态管理设计_V1.0.md`
Rules and networking assessment: `docs/Halo_CE_TCG_规则实现与网络支持现状评估_V1.0.md`

