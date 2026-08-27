package eu.purrtech.purrtechPVE.itemset;

import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import eu.purrtech.purrtechPVE.db.ItemSetDamageThresholdRepository;
import eu.purrtech.purrtechPVE.db.ItemSetMemberRepository;
import eu.purrtech.purrtechPVE.db.ItemSetModifierThresholdRepository;
import eu.purrtech.purrtechPVE.db.ItemSetRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateRepository;
import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import eu.purrtech.purrtechPVE.item.TemplateNotFoundException;
import eu.purrtech.purrtechPVE.item.UnknownDamageTypeException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates set CRUD, membership, and its tiered damage/resistance
 * thresholds. Unlike {@code ItemTemplateService}, nothing here bumps a
 * version or writes a snapshot - a set's bonuses are live/global config
 * (see {@code Schema}'s comment on {@code item_sets}), evaluated fresh at
 * combat time from however many set pieces are currently worn.
 */
public final class ItemSetService {

    private final ItemSetRepository setRepository;
    private final ItemSetMemberRepository memberRepository;
    private final ItemSetDamageThresholdRepository damageThresholdRepository;
    private final ItemSetModifierThresholdRepository modifierThresholdRepository;
    private final ItemTemplateRepository templateRepository;
    private final DamageTypeRegistry damageTypeRegistry;

    public ItemSetService(ItemSetRepository setRepository,
                           ItemSetMemberRepository memberRepository,
                           ItemSetDamageThresholdRepository damageThresholdRepository,
                           ItemSetModifierThresholdRepository modifierThresholdRepository,
                           ItemTemplateRepository templateRepository,
                           DamageTypeRegistry damageTypeRegistry) {
        this.setRepository = setRepository;
        this.memberRepository = memberRepository;
        this.damageThresholdRepository = damageThresholdRepository;
        this.modifierThresholdRepository = modifierThresholdRepository;
        this.templateRepository = templateRepository;
        this.damageTypeRegistry = damageTypeRegistry;
    }

    public ItemSet create(String key, String displayName) {
        if (setRepository.findByKey(key).isPresent()) {
            throw new DuplicateSetKeyException(key);
        }
        long now = System.currentTimeMillis();
        ItemSet set = new ItemSet(UUID.randomUUID(), key, displayName, now, now);
        setRepository.insert(set);
        return set;
    }

    public boolean delete(String key) {
        return setRepository.delete(key);
    }

    public Optional<ItemSet> findByKey(String key) {
        return setRepository.findByKey(key);
    }

    public List<ItemSet> listAll() {
        return setRepository.findAll();
    }

    public List<ItemTemplate> members(String setKey) {
        ItemSet set = requireSet(setKey);
        return memberRepository.findTemplateIdsOfSet(set.id()).stream()
                .map(templateRepository::findById)
                .flatMap(Optional::stream)
                .toList();
    }

    public void addMember(String setKey, String templateKey) {
        ItemSet set = requireSet(setKey);
        ItemTemplate template = requireTemplate(templateKey);
        memberRepository.add(set.id(), template.id());
    }

    public void removeMember(String setKey, String templateKey) {
        ItemSet set = requireSet(setKey);
        ItemTemplate template = requireTemplate(templateKey);
        memberRepository.remove(set.id(), template.id());
    }

    public List<SetThresholdDamage> damageThresholds(String setKey) {
        return damageThresholdRepository.findBySet(requireSet(setKey).id());
    }

    public List<SetThresholdModifier> modifierThresholds(String setKey) {
        return modifierThresholdRepository.findBySet(requireSet(setKey).id());
    }

    public void setDamageThreshold(String setKey, int pieceCount, String damageTypeKey, double amount, DamageMode mode) {
        ItemSet set = requireSet(setKey);
        requireDamageType(damageTypeKey);
        damageThresholdRepository.upsert(set.id(), new SetThresholdDamage(pieceCount, damageTypeKey, amount, mode));
    }

    public boolean removeDamageThreshold(String setKey, int pieceCount, String damageTypeKey) {
        return damageThresholdRepository.remove(requireSet(setKey).id(), pieceCount, damageTypeKey);
    }

    public void setModifierThreshold(String setKey, int pieceCount, String damageTypeKey, double percent) {
        ItemSet set = requireSet(setKey);
        requireDamageType(damageTypeKey);
        modifierThresholdRepository.upsert(set.id(), new SetThresholdModifier(pieceCount, damageTypeKey, percent));
    }

    public boolean removeModifierThreshold(String setKey, int pieceCount, String damageTypeKey) {
        return modifierThresholdRepository.remove(requireSet(setKey).id(), pieceCount, damageTypeKey);
    }

    private ItemSet requireSet(String key) {
        return setRepository.findByKey(key).orElseThrow(() -> new ItemSetNotFoundException(key));
    }

    private ItemTemplate requireTemplate(String key) {
        return templateRepository.findByKey(key).orElseThrow(() -> new TemplateNotFoundException(key));
    }

    private void requireDamageType(String damageTypeKey) {
        if (damageTypeRegistry.find(damageTypeKey).isEmpty()) {
            throw new UnknownDamageTypeException(damageTypeKey);
        }
    }
}
