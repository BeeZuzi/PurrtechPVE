package eu.purrtech.purrtechPVE.damage;

public record DamageType(
        String key,
        String displayName,
        boolean dot,
        int dotPeriodTicks,
        double dotTickPercent
) {

    public static DamageType instant(String key, String displayName) {
        return new DamageType(key, displayName, false, 0, 0.0);
    }

    public static DamageType dot(String key, String displayName, int periodTicks, double tickPercent) {
        return new DamageType(key, displayName, true, periodTicks, tickPercent);
    }
}
