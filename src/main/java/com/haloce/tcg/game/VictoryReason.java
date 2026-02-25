package com.haloce.tcg.game;

/**
 * Reason for game victory. Used in WIN_CONDITION_MET / GAME_ENDED events and for stable protocol.
 *
 * <p><b>Victory priority (evaluated in this order):</b></p>
 * <ol>
 *   <li><b>Elimination</b> — last player/team standing (base health 0 for all opponents).</li>
 *   <li><b>Full lane control</b> — controlling all 3 lanes for 2 consecutive end-steps.</li>
 * </ol>
 * When multiple conditions could be satisfied in the same evaluation, only the first in the above
 * order is applied (e.g. if the last opponent is eliminated and you also have full control streak,
 * victory is declared as elimination).
 */
public enum VictoryReason {
    /** 1v1 / FFA: only one player remains (others eliminated by base health 0). */
    LAST_PLAYER_STANDING,

    /** 2v2: only one team remains (all opponents eliminated). */
    LAST_TEAM_STANDING,

    /** 1v1 / FFA: current player controls all 3 lanes for 2 consecutive end-steps. */
    FULL_CONTROL_STREAK,

    /** 2v2: current team controls all 3 lanes for 2 consecutive end-steps. */
    TEAM_FULL_CONTROL_STREAK,

    /** No player is alive (e.g. simultaneous base destruction). Game ends with no winner. */
    NO_ALIVE_PLAYERS
}
