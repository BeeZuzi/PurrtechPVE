package eu.purrtech.purrtechPVE.config;

import java.util.List;

public record AccessorySettings(List<String> slots) {

    public static AccessorySettings defaults() {
        return new AccessorySettings(List.of("RING_1", "RING_2", "AMULET", "BELT"));
    }
}
