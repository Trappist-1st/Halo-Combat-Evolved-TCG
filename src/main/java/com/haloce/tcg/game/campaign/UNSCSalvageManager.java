package com.haloce.tcg.game.campaign;

import java.util.HashMap;
import java.util.Map;

public class UNSCSalvageManager {
    private final Map<String, Integer> recoveredCoreByVehicleType = new HashMap<>();

    public void recoverVehicleCore(String vehicleCardId) {
        if (vehicleCardId == null || vehicleCardId.isBlank()) {
            return;
        }
        recoveredCoreByVehicleType.merge(vehicleCardId, 1, Integer::sum);
    }

    public int consumeRefitReduction(String vehicleCardId) {
        int current = recoveredCoreByVehicleType.getOrDefault(vehicleCardId, 0);
        if (current <= 0) {
            return 0;
        }
        recoveredCoreByVehicleType.put(vehicleCardId, current - 1);
        return 1;
    }

    public int pendingCore(String vehicleCardId) {
        return recoveredCoreByVehicleType.getOrDefault(vehicleCardId, 0);
    }
}
