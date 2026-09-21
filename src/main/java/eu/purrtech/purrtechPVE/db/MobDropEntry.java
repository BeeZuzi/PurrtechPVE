package eu.purrtech.purrtechPVE.db;

/** How many of a template a MythicMobs mob type drops on death, and with what % chance (0-100) it rolls at all. */
public record MobDropEntry(int amount, double chancePercent) {
}
