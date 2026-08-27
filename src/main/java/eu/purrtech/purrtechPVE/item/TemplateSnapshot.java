package eu.purrtech.purrtechPVE.item;

import org.bukkit.Material;

import java.util.List;
import java.util.UUID;

/**
 * Full computed state of a template at one specific version - captured
 * every time a mutation bumps {@link ItemTemplate#version()}, so a stack
 * pinned at an older synced version can still be re-rendered exactly as it
 * looked back then, not with whatever the live (possibly newer, possibly
 * un-pushed) template data says now.
 */
public record TemplateSnapshot(
        UUID templateId,
        String templateKey,
        int version,
        String displayName,
        Material baseMaterial,
        Integer customModelData,
        List<DamageContribution> damageContributions,
        List<TypeModifier> typeModifiers,
        List<TemplateEnchantment> enchantments,
        List<ArmorPenetration> armorPenetration,
        long createdAt
) {
}
