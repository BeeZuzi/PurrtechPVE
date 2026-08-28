## Stav implementace

- **Fáze 0 hotová (2026-08-20).** Doporučené výchozí hodnoty ze 4 otázek níže zatím
  neodsouhlaseny explicitně, ale postupuju podle nich (SQLite, MythicMobs
  soft-depend, vlastní chest GUI, virtuální accessory sloty) — klidně napiš, pokud
  chceš některou jinak, přepíšu.
  - `build.gradle.kts`: shadow plugin, `io.lumine:Mythic-Dist:5.10.0` (`compileOnly`,
    repo `mvn.lumine.io`), HikariCP + sqlite-jdbc shaded (`org.sqlite` NEreloctovaný,
    stejný důvod jako u `PurrtechOrders`), JUnit 6 test deps.
  - `paper-plugin.yml`: `softdepend: [MythicMobs]`, permissions `purrtechpve.admin`
    (op) a `purrtechpve.accessory.use` (true).
  - Balíčky `db/` (`Database`+`Schema` se všemi 6 tabulkami z datového modelu níže),
    `config/` (`WorldToggleSettings`, `AccessorySettings`, `ConfigLoader`), `lang/`
    (`Messages`, stejný flatten+MiniMessage vzor jako Orders), `config.yml` +
    `lang/cs.yml`+`lang/en.yml`.
  - `PurrtechPVE.java`: `onEnable` connectne DB, načte config/lang, zaloguje jestli
    je MythicMobs nalezen (soft-depend guard přes `isPluginEnabled`), world/pvp/pve
    toggle stav a accessory sloty.
  - **Oprava scaffoldu:** `gradle/wrapper/gradle-wrapper.properties` měl vygenerovaný
    Gradle 9.4.0, se kterým `run-paper` 3.1.0 plugin nejde resolvnout (potřebuje
    `org.gradle.plugin.api-version` 9.7.0) — přepsáno na 9.7.0 stejně jako
    `PurrtechOrders`/`PurrtechWheelAddon`.
  - Ověřeno reálným `runServer` bootem (port 25567, aby to nekolidovalo s tvým živým
    serverem na 25565) - `./gradlew build` zelené, plugin se zapne/vypne bez
    výjimky, `purrtechpve.db` se vytvoří se všemi 6 tabulkami schématu.
- **Fáze 1 hotová (2026-08-20).** `DamageTypeRegistry` (in-memory, natvrdo seedlé
  všechny typy z "Výchozí seed" níže + fallback `physical`, DB-backý CRUD přijde
  později spolu s GUI). `DamagePipeline.apply(rawDamage, typeSplitPercent,
  resistPercentByType)` - čistá funkce beze stavu: rozpočítá raw damage na typové
  kbelíky (bez configu = 100 % `physical`), na každý aplikuje `1 -
  clamp(resist)/100` (clamp -200 % až 95 %), sečte zpět, floor na 0. `CombatKind`
  (PVP/PVE) + `WorldToggleEvaluator.isActive(settings, world, kind)` - globální
  `worlds.disabled` vyhrává nad vším, pak PvP/PvE má vlastní on/off + vlastní
  per-world override list. `CombatDamageListener` na `EntityDamageByEntityEvent`
  (včetně projectily se `shooter` resolvnutým na `LivingEntity`) - určí PVP/PVE
  podle toho, jestli je útočník/obránce `Player`, mimo hru nechá cokoliv, kde není
  aspoň jeden `Player` (mob-vs-mob), toggle-gated přes `WorldToggleEvaluator`,
  zapojený do pipeline. **Bez item šablon (Fáze 2/3) je split/resist zatím vždy
  prázdný** → efektivně no-op (100 % physical, 0 % resist, damage beze změny) -
  tahle fáze ověřuje zapojení/toggly, ne reálné číslo poškození; hook body na
  attacker/defender template lookup jsou označené TODO v `CombatDamageListener`.
  18 JUnit testů (`DamagePipelineTest`, `WorldToggleEvaluatorTest`,
  `DamageTypeRegistryTest`), žádné mockování Bukkitu (pipeline i evaluator jsou
  čistá logika/records). Ověřeno i reálným `runServer` bootem - v logu je vidět
  všech 19 registrovaných damage typů a listener se zaregistroval bez výjimky.
  - **Další: Fáze 2** (`ItemTemplate` CRUD přes repository + `ItemRenderer`
    šablona→lore/PDC + `/pve item give` příkazem, bez GUI zatím).

- **Fáze 2 hotová (2026-08-20).** `ItemTemplate`/`DamageContribution`/`TypeModifier`
  modely + `ItemTemplateRepository`/`DamageContributionRepository`/
  `TypeModifierRepository` (SQLite, `INSERT OR REPLACE` upsert na sub-tabulkách).
  `ItemRenderer` - šablona + její damage contributions/type modifiers → `ItemStack`
  s lokalizovaným lore (přes `Messages`, tři sekce: poškození při útoku/pasivní
  bonus/odolnosti-slabiny) a PDC tagy `template_key`+`template_version`
  (`readStamp()` je připravený hook pro Fázi 3's "je tenhle item pořád aktuální
  verze"). `ItemTemplateService` - CRUD orchestrace, **každá mutace (damage
  contribution, type modifier) bumpne šablonino `version`** - ověřeno v testu i
  živě (v1 create → v4 po 3 úpravách). `/pve item create|delete|list|give|damage
  set/remove|resist set/remove` přes Brigadier (`purrtechpve.admin`).
  - **Oprava bugu:** `Schema.java` mělo `damage_type_key` jako FK na
    `damage_type_definitions(key)`, ale ta tabulka se nikdy neplní
    (`DamageTypeRegistry` je pořád jen in-memory, Fáze 1) - každé
    `/pve item damage set`/`resist set` by na živém serveru spadlo na
    `SQLITE_CONSTRAINT_FOREIGNKEY`. FK odstraněn, validita se řeší v
    `ItemTemplateService.requireDamageType()` proti in-memory registru; FK
    integrita se může vrátit až se `DamageTypeRegistry` přesune do DB.
  - 41 JUnit testů zelených (repozitáře přes reálnou dočasnou SQLite DB, service
    layer bez mockování - `ItemRenderer` dostane `null`, protože žádný z
    testovaných methods ho nepoužívá, `renderGiveable()`/skutečné vykreslení
    `ItemStack`/`ItemMeta` nejde otestovat bez živého serveru, žádný MockBukkit
    v projektu - stejná konvence jako sourozenecké pluginy).
  - Ověřeno reálným `runServer` bootem + posloupností konzolových příkazů
    (create → 2x damage set → resist set → list → damage remove → delete →
    list) - všechny zprávy i verzování sedí přesně. **Neověřeno živě:**
    `/pve item give` (potřebuje připojeného hráče, v sandboxu není k dispozici)
    - kód prošel review a kompiluje, ale skutečné PDC tagy/lore na vydaném
    itemu chce ještě ověřit ručně s reálným klientem.
  - **Další: Fáze 3** (`ItemSyncService` - propagace do online inventářů +
    lazy-touch listenery pro offline/chunk-load cestu + verzování šablon
    využité prakticky).

- **Fáze 3 hotová (2026-08-21).** Vyřešeno přesné zadání "propsat do oběhu, nebo
  ne, admin si vybere pokaždé zvlášť" - **editace šablony (`damage set/remove`,
  `resist set/remove`) sama o sobě NIKDY nesahá na itemy v oběhu**, jen bumpne
  `version` a zapíše plný snapshot nového stavu (`item_template_snapshot`,
  ruční `key|amount|mode|context;...` serializace mezi sub-tabulkami, žádná JSON
  knihovna). Nová oddělená akce `/pve item sync <key>` je jediná věc, co
  `syncedVersion` dožene na `version` a vyvolá skutečné přerenderování - tím
  pádem "propaguj/nepropaguj" je čistě otázka toho, jestli admin `sync`
  zavolal, ne parametr u každé editace.
  - `ItemTemplate` teď nese `version` (živá editační hlava) i `syncedVersion`
    (poslední explicitně propsaná verze) - `isFullySynced()` helper.
  - `ItemTemplateSnapshotRepository`/`TemplateSnapshot` - kompletní stav
    (display name, base material, custom model data, damage contributions,
    type modifiers) při KAŽDÉ verzi, ne jen nejnovější - nutné, protože
    přerenderování musí dohnat stack přesně na `syncedVersion`, což může být
    starší než živá `item_damage_contribution`/`item_type_modifier` data,
    pokud mezitím proběhlo víc needitovaných změn.
  - `ItemRenderer` refaktorován na sdílené privátní jádro + dvě vstupní metody:
    `render(ItemTemplate, ...)` (vždy nejnovější verze, pro `/pve item give`)
    a `renderSnapshot(TemplateSnapshot)` (přesně daná historická verze, pro
    dohánění zaostalých kusů).
  - `ItemSyncService.resyncAllOnlinePlayers()` (volá command handler po
    `propagate()`) + `resyncPlayer()` sweepuje main inventář, armor sloty,
    off-hand a ender chest, porovná PDC `template_version` proti aktuální
    `syncedVersion`, a pokud je pozadu, přerenderuje stack ze snapshotu se
    zachováním počtu kusů. `ItemSyncJoinListener` volá totéž na
    `PlayerJoinEvent` - to je "offline hráč dožene verzi při přihlášení" půlka
    lazy-touch. **Chunk-load sweep pro itemy v truhlách/na zemi ve světě není
    implementovaný** - vědomě odloženo, je to samostatná (a složitější) věc
    než hráčův inventář, poznamenáno jako budoucí rozšíření.
  - **Oprava bugu:** stejná FK past jako ve Fázi 2, tentokrát na nové
    `item_template_snapshot.template_id` - zkontrolováno rovnou při psaní,
    FK jen na `item_templates(id) ON DELETE CASCADE` (validní, ta tabulka se
    plní), žádná FK na `damage_type_key`.
  - 50 JUnit testů zelených (přidáno pokrytí snapshot repository + `propagate`/
    `syncedVersion` sémantiky - nová šablona je triviálně fully-synced, editace
    samotná `syncedVersion` nikdy nehne, `propagate` ji dožene, snapshot jde
    dohledat po každém version bumpu).
  - Ověřeno živě: `runServer` boot + `create → damage set → sync → damage set →
    list → sync → sync na neexistující klíč` - verze (v1→v2→v3) i `synced`
    hláška se počtem aktualizovaných itemů (0, protože žádný hráč nebyl
    připojený) sedí přesně, chyba na neexistující šabloně se ohlásí čistě bez
    pádu. **Skutečné přerenderování stacku v inventáři online hráče
    neověřeno** (sandbox nemá připojeného klienta) - kód prošel review a
    kompiluje, ale doporučuju to při příležitosti zkusit ručně: dát si item,
    upravit šablonu, `/pve item sync`, zkontrolovat že se lore v inventáři
    opravdu přepsalo.
- **Combat pipeline skutečně čte item šablony (2026-08-21, doplněk Fáze 3).**
  Než MythicMobs bridge, dřív dávalo smysl zavřít mezeru "item data existují,
  ale nic ve hře je nepoužívá" - `CombatDamageListener`'s `TODO` je pryč.
  - `DamagePipeline.apply` přepracován: `typedDamage` teď nese **absolutní**
    částky poškození za typ (ne zlomek rawDamage) - čistší dělba
    odpovědnosti, split/bonus matematika žije mimo pipeline, pipeline sama
    jen aplikuje odolnost/slabinu a sečte. (Rozbilo by to zpětnou
    kompatibilitu API, kdyby to bylo veřejné mimo tenhle plugin, ale je to
    interní - 8 testů přepsáno na nový kontrakt + 1 nový test na "bonus nad
    rámec základu, ne náhrada".)
  - `combat/EquipmentResolver.java` (nové) - čte skutečné nasazené/držené
    itemy přes `LivingEntity.getEquipment()` (funguje stejně na hráče i
    vanilla/MythicMobs moby, žádný cast na `Player` nikde) a staví: (a)
    útočníkovo odchozí typované poškození = split hlavní ruky (jen `WIELDED`
    kontribuce, fallback 100 % `physical` bez naší zbraně) + `WORN` bonusy ze
    všech nasazených kusů (helma/plate/legs/boty/off-hand/i hlavní ruka)
    sečtené do stejných kbelíků; (b) obráncova odolnost = součet
    `item_type_modifier.percent` napříč vším nasazeným.
  - `CombatDamageListener` teď volá `EquipmentResolver` místo prázdných map,
    žádný hardcoded no-op zbytek.
  - Ověřeno: 51 JUnit testů zelených, `./gradlew build` zelené, živý
    `runServer` boot bez výjimky s celou novou wiring (vytvoření šablon +
    damage/resist příkazy proběhly čistě). **Skutečný souboj s reálným
    vybaveným hráčem neověřen** - sandbox nemá připojeného klienta, takže
    jestli item split/resist/bonus matematika sedí END-TO-END ve hře (ne jen
    v unit testech na `DamagePipeline` samotné) chce ruční ověření: vzít
    fire-sword, praštit mobem, zkontrolovat že se číslo poškození změnilo
    podle nastavených typů, obléct frost-plate a zkusit, že odolnost na fire
    skutečně sníží dostávané poškození.

  - **Další: Fáze 4** (MythicMobs bridge - detekce mobů, `mob_damage_profile`,
    čtení equipmentu moba, obousměrná damage pipeline player↔mob - poznámka:
    `EquipmentResolver` už dnes funguje mob-agnosticky přes `LivingEntity`,
    takže hráč-vs-vanilla-mob a mob-vs-hráč case už teď reálně funguje;
    Fáze 4 přidává jen MythicMobs-specifickou vrstvu - `mob_damage_profile`
    podle MythicMobs typu a detekci MythicMobs skill-based damage eventů).

- **Fáze 4 hotová, s vědomě zúženým scope (2026-08-21).** Signatury ověřeny
  přímo z reálného `Mythic-Dist-5.10.0.jar` přes `javap` (ne jen z web
  javadocu, který u `MobManager` mlčel o `isActiveMob(UUID)` a u
  `MythicDamageEvent` o `getCaster()`/`getTarget()` - nespolehni se na
  scraped docs, když máš jar po ruce).
  - `mythicmobs/MythicMobsBridge.java` (nové, **jediná** třída co smí
    importovat MythicMobs API) - `isMythicMob(Entity)` přes
    `MythicBukkit.inst().getMobManager().isActiveMob(uuid)`,
    `mythicMobInternalName(Entity)` přes `getSkillCaster(uuid)` scasteno na
    `ActiveMob` → `getType().getInternalName()` (`MobManager` nemá přímé
    `getActiveMob(UUID)`, jen `isActiveMob`+`getActiveMobs()`+
    `getSkillCaster()` - tenhle obchvat je jediná cesta k jedné konkrétní
    aktivní mobě podle UUID). Instance se vytváří **jen** když
    `isPluginEnabled("MythicMobs")` je `true` (`PurrtechPVE.onEnable`) -
    jinak zůstává `null` a nikdo se jí nedotkne, takže třída se ani
    neclassloadne na serveru bez MythicMobs.
  - `db/MobDamageProfileRepository.java` - CRUD nad `mob_damage_profile`
    (stejný upsert/remove/find vzor jako `TypeModifierRepository`, teď vrací
    rovnou `Map<String,Double>` místo listu recordů, protože žádná další pole
    nejsou potřeba). `/pve mobprofile set|remove|list <mythicMobType>
    <damageType> [<percent>]` - validace typu poškození proti
    `DamageTypeRegistry`, žádná validace že `mythicMobType` reálně existuje v
    MythicMobs configu (schválně - profil se dá připravit dřív, než mob
    vznikne).
  - `EquipmentResolver.resolveResistance` teď k odolnosti z vybavení přičítá
    i `mob_damage_profile`, pokud je `mythicMobsBridge != null` A entita je
    aktivní MythicMobs mob - čistě aditivní sloučení do stejné mapy, žádné
    zdvojení výpočtu.
  - **Vědomě mimo scope: `MythicDamageEvent` (skill-based damage) hook.**
    Tohle je "damaging mechanic used" event pro MythicMobs skilly (fireball
    apod.), samostatný od vanilla `EntityDamageByEntityEvent`, který už
    `CombatDamageListener` zpracovává. Riziko: některé skill mechaniky
    (např. `damage` mechanic) aplikují poškození TAKÉ přes vanilla cestu, což
    by při naivním zdvojeném hooku počítalo poškození dvakrát - a bez reálně
    nainstalovaného MythicMobs s nakonfigurovanými skilly (sandbox to nemá)
    nejde ověřit, jestli konkrétní mechanika dvojitě spouští, nebo ne. Zkusil
    jsem najít zavedený vzor u MMOItems (podobný plugin), jejich MythicMobs
    kompatibilita ale taky nehookuje `MythicDamageEvent` pro staty - jde spíš
    přes vlastní custom mechaniky. Takže **standardní melee útok moba
    (nejčastější případ) už funguje** přes `EquipmentResolver`+vanilla event,
    ale MythicMobs skill-damage (firebally, AoE skilly) zatím naší
    pipeline neprochází - potřebuje živé otestování se skutečným MythicMobs
    a nakonfigurovaným skillem, než se do toho pustím, ať nevznikne tichý bug
    se zdvojeným poškozením.
  - 56 JUnit testů zelených (přidáno pokrytí `MobDamageProfileRepository`,
    čistá SQLite logika bez Bukkitu). `MythicMobsBridge`/`EquipmentResolver`
    samotné netestovatelné bez živého serveru (`MythicMobsBridge` navíc bez
    reálně nainstalovaného MythicMobs pluginu) - stejná konvence jako
    předešlé fáze.
  - Ověřeno živě: `runServer` boot bez MythicMobs nainstalovaného - log
    potvrzuje "MythicMobs integration: not found, running standalone" (bridge
    zůstal `null`), a celá `/pve mobprofile set/list/remove` posloupnost
    proběhla čistě včetně validace neznámého damage typu. **Neověřeno**:
    cokoliv se skutečným MythicMobs pluginem (detekce moba, mob_damage_profile
    v reálném souboji, natož skill-damage hook) - potřebuje server s
    MythicMobs nainstalovaným a nakonfigurovaným mobem.
  - **Další:** Fáze 5 (trinket/virtuální accessory sloty) nebo Fáze 6 (GUI
    editor) podle PLAN.md - nebo, pokud chceš, live test s reálným MythicMobs
    pluginem než půjdu dál, ať se potvrdí že mob detekce/profil skutečně
    fungují (ne jen že se nic nerozbilo).

- **Fáze 5 hotová (2026-08-21) + oprava důležitého designového nedostatku.**
  - **Bug objevený a opravený při psaní Fáze 5:** `EquipmentResolver` (Fáze 3.5)
    četlo damage contributions/type modifiers z **živých** `item_damage_
    contribution`/`item_type_modifier` tabulek podle šablony, ne podle verze
    nastřádané na konkrétním kusu. To znamenalo, že `/pve item sync` (Fáze 3)
    reálně řídilo jen kdy se aktualizuje **lore text**, ale samotný **herní
    efekt** poškození/odolnosti se vždycky počítal podle nejnovějších dat bez
    ohledu na to, jestli byla verze propsaná - přesně opačně, než jsi chtěl
    ("item v oběhu se změní JEN když to pošlu"). Opraveno: `EquipmentResolver`
    teď čte přes `ItemTemplateSnapshotRepository.find(templateId,
    stamp.templateVersion())` - přesně tu verzi, kterou má kus napsanou ve
    svém PDC tagu - takže needitovaná/nepropsaná změna teď má nulový herní
    dopad na existující kusy, přesně jak má. `allowed_slots`/trinket flag
    zůstává vědomě **živá** vlastnost (ne verzovaná) - je to pravidlo
    umístění, ne balance číslo, viz javadoc na `EquipmentResolver`.
  - `ItemTemplateService.setAllowedSlots(key, slotNames)` - nastaví
    `allowed_slots` + `is_trinket` (odvozený, `true` pokud seznam neprázdný),
    bez version bumpu/snapshotu (živá vlastnost). `/pve item slots <key>
    <slot1,slot2,...|none>` - jména slotů = přesně `EquipmentSlot` enum
    (`HAND`, `OFF_HAND`, `HEAD`, `CHEST`, `LEGS`, `FEET`) + jména z
    `accessory-slots` configu. Prázdný seznam = neomezeno (zpětně
    kompatibilní výchozí stav).
  - `EquipmentResolver` teď prochází sloty se jmény (ne anonymní pole) a
    každou WORN kontribuci/modifier filtruje přes `isAllowedInSlot` -
    šablona bez omezení (`allowedSlots` prázdné) funguje odkudkoliv jako
    dřív, šablona s omezením jen tam, kam patří.
  - **Virtuální accessory sloty**: `player_accessory_slots` tabulka +
    `db/AccessoryRepository.java` (`ItemStack#serializeAsBytes()`/
    `deserializeBytes()`, žádná vlastní serializace). `/pve accessory`
    (permission `purrtechpve.accessory.use`, default true) otevře
    `trinket/AccessoryMenu` - chest GUI velikosti zaokrouhlené na násobek 9,
    reálné sloty = `accessory-slots` z configu (výchozí RING_1/RING_2/AMULET/
    BELT), zbytek zamčený šedým sklem. `AccessoryMenuListener` - shift-click a
    kliky na zamčené sloty zamítnuty (žádný quick-move v v1, bezpečnější proti
    edge-case bugům), obsah se uloží při zavření GUI. `EquipmentResolver` u
    `Player` entit tyhle sloty čte stejně jako vanilla vybavení (klíčované
    jménem slotu, takže `allowedSlots` na ně platí úplně stejně).
  - **Kvůli permission struktuře jsem musel přesunout `.requires(...)`** z
    kořenového `/pve` uzlu (byl `purrtechpve.admin` na celém stromu) na
    jednotlivé podstromy (`item`, `mobprofile` = admin;
    `accessory` = `purrtechpve.accessory.use`) - jinak by běžný hráč vůbec
    nedosáhl na `/pve accessory`, protože Brigadier requires() se
    vyhodnocuje na každé úrovni cesty k uzlu.
  - 56 JUnit testů pořád zelených (beze změny počtu - `AccessoryRepository`
    nejde testovat bez živého serveru, protože `ItemStack#serializeAsBytes()`
    potřebuje skutečnou `CraftItemStack` implementaci, ne jen syrové SQL;
    stejná mez jako `ItemRenderer`/GUI kód).
  - Ověřeno živě: `runServer` boot + `create → damage set → slots set →
    slots clear → accessory (z konzole, správně odmítnuto "jen hráč")` -
    vše čistě, `player_accessory_slots` tabulka existuje, `allowed_slots`
    sloupec se správně plní/maže. **Neověřeno**: skutečné otevření/
    použití accessory GUI reálným hráčem (drag&drop, uložení při zavření,
    reálný dopad na odolnost/poškození) - sandbox bez klienta. Doporučuju
    při první příležitosti ručně: `/pve accessory`, dát si trinket s
    `allowed_slots` omezeným na `RING_1`, položit ho tam, praštit/nechat se
    praštit a zkontrolovat že bonus/odolnost funguje, pak zavřít GUI a znovu
    otevřít, ať se potvrdí perzistence.
  - **Další:** Fáze 6 (GUI editor itemů - všechny taby, rebase drag&drop,
    publish-to-circulation potvrzovací menu) podle PLAN.md.

- **Fáze 6 hotová (2026-08-21) - GUI editor itemů, poslední fáze původního
  plánu.** `/pve item edit <key>` otevře jeden 54-slotový chest inventář
  znovupoužívaný napříč přepínáním tabů (nikdy se nezavírá/neotevírá znovu,
  kromě kolem chat promptu) - GUI je čistě **druhý vstupní bod do stejné
  `ItemTemplateService`**, žádná paralelní logika:
  - **Základ** - náhled aktuálně vyrenderovaného itemu + verze/sync stav.
    Rebase = drž item v ruce a klikni na náhled (přebírá materiál + custom
    model data, damage/resist data šablony zůstávají) - stejná akce jako nový
    příkaz `/pve item setbase <key>` z ruky. **Vědomá odchylka od PLAN.md**:
    žádné skutečné drag&drop tažení itemu do slotu (`InventoryDragEvent` se
    v tomhle GUI kompletně zamítá) - "drž v ruce a klikni" dělá to samé s
    mnohem menším rizikem cursor/partial-stack edge-case bugů.
  - **Custom Damages** - mřížka všech registrovaných damage typů. Klik otevře
    chatový prompt (`<částka> <flat|percent> <wielded|worn>` jedním řádkem) -
    **vědomá odchylka od PLAN.md**: místo anvil/sign textového vstupu jsem
    zvolil chat capture (`AsyncChatEvent` + jednorázový posluchač na hráče) -
    spolehlivější pro 3-polní vstup než anvil (žádné XP/item-konzumace
    kuriozity), scéna se zavře, počká na zprávu, zpracuje a znovu otevře GUI
    na stejném tabu. Shift-klik smaže wielded i worn najednou.
  - **Odolnosti/Slabiny** - stejná mřížka, klik = chat prompt jen na
    procenta (kladné/záporné), shift-klik smaže.
  - **Trinket sloty** - mřížka 6 vanilla `EquipmentSlot` + nakonfigurovaných
    accessory slotů, klik přepíná členství (volá stejné
    `setAllowedSlots` jako `/pve item slots`).
  - **Uložit & Publikovat** - stavová informace (verze vs. propsaná verze) +
    jedno tlačítko "Propsat do oběhu teď" (`propagate()` +
    `resyncAllOnlinePlayers()`). Žádné samostatné "Uložit" tlačítko - každá
    úprava se ukládá okamžitě při kliknutí/zprávě, stejně jako u
    příkazové řádky.
  - Nové: `ItemTemplateService.rebase(key, Material, Integer)` - stejný
    version-bump+snapshot vzor jako damage/resist (needitovaný rebase nemá
    žádný efekt na kusy v oběhu, dokud se nepropíše - konzistentní s Fáze 5
    opravou). `/pve item setbase <key>` (příkazová alternativa ke GUI kliku).
  - **Bug odchycený při psaní (ne při testování - review vlastního kódu):**
    lore v Custom Damages tabu tvrdilo "Klik = wielded, Shift+klik = worn,
    Shift+pravý klik = smazat obojí", ale kód rozlišoval jen shift/ne-shift
    (žádné pravý/levý klikání) - shift vždycky mazal OBOJÍ, ne nastavoval
    worn. Ve skutečnosti `worn` šlo nastavit i tak (chat prompt se ptá na
    kontext jako 3. slovo textu bez ohledu na to, jaký klik ho otevřel), jen
    lore lhalo o tom, jak se tam dostat. Opraveno - lore teď popisuje
    skutečné chování, nepoužívaný `context` parametr u `promptDamageContribution`
    odstraněn.
  - 59 JUnit testů zelených (přidáno pokrytí `rebase()` a `setAllowedSlots()`
    including trinket-flag toggle). **GUI klikací/chatový interakční kód
    (`ItemEditorMenu`, `ItemEditorListener`) nejde jednotkově otestovat**
    (žádný MockBukkit) a **nebyl vůbec živě vyzkoušený se skutečným
    připojeným hráčem** - sandbox na to nemá klienta. Ověřil jsem jen: čistý
    boot s novým listenerem, `/pve item edit`/`/pve item setbase` z konzole
    se správně odmítnou ("jen hráč", bez pádu), a `rebase()`/`setAllowedSlots()`
    přes JUnit. **Tohle je zdaleka největší neověřená plocha v celém
    projektu** - GUI má 5 tabů, kliky na ~19+6+N ikon, chat-prompt round-trip
    a re-open-menu logiku, což je hodně nových interakčních cest, které jsem
    nikdy neviděl skutečně běžet. Než na tohle spolehneš, důrazně doporučuju
    projít ručně: `/pve item edit <key>` → proklikat všech 5 tabů, nastavit
    damage/resist přes chat prompt (i test 'zrusit' cesty), zkusit rebase
    (drž item, klikni na náhled), přepnout trinket sloty, kliknout "Propsat
    do oběhu teď" a zkontrolovat že se GUI nezasekne/nerozbije při žádné
    kombinaci kliků.
  - **Vědomě mimo scope (Fáze 6 nedodělané kusy):** Attributes tab (vanilla
    Bukkit `Attribute` enum + custom staty jako life steal/crit %) - žádný
    backend pro atributy nikdy nebyl postavený (`item_attribute_modifier`
    tabulka existuje ve schématu od Fáze 0, ale bez repository/service),
    takže by to znamenalo stavět celý nový subsystém uprostřed GUI fáze
    místo GUI nad existujícím. Nechávám jako čistý, jasně ohraničený budoucí
    krok, ne narychlo dopsaný.
  - **Zbývá z PLAN.md mimo Fázi 6:** MythicMobs `MythicDamageEvent` skill-
    damage hook (Fáze 4, vědomě odloženo kvůli riziku zdvojení poškození),
    Attributes subsystém + GUI tab (viz výše), reálné testování se skutečným
    MythicMobs pluginem a se skutečným hráčem obecně - to všechno potřebuje
    prostředí, které tenhle sandbox nemá.

- **ValhallaMMO import (2026-08-21) - mimo původní plán, na žádost.** `/pve
  item import valhalla <key> <displayName...>` (hráč, čte item z ruky).
  - **Formát ověřen ze skutečného zdrojáku** (naklonoval jsem
    `github.com/Athlaeos/ValhallaMMO` a jeho wiki repo, ne odhad z
    dokumentace): ValhallaMMO ukládá VŠECHNY custom staty itemu do
    **jednoho stringového PDC tagu** `valhallammo:default_stats` (fallback
    `valhallammo:actual_stats`), formát `ATRIBUT:hodnota:OPERACE:hidden;...`
    (`item/ItemAttributesRegistry.java`, metody `serializeStats`/`getStats`).
    Díky tomu `ValhallaMmoImporter` **nepotřebuje ValhallaMMO nainstalované
    ani jako dependency** - čte čistě vanilla Bukkit PDC string, žádný
    import jejich API/tříd, žádný `softdepend`.
  - Mapování na náš model: `EXTRA_<TYP>_DAMAGE` (flat bonus poškození na
    hit) → `DamageContribution(FLAT, WIELDED)`; `<TYP>_RESISTANCE` (uloženo
    jako zlomek, např. `0.25`) → `TypeModifier` v procentech (`×100`).
    Typy 1:1 kromě `MAGIC` (u nás chybělo - **přidáno do
    `DamageTypeRegistry` jako nový seed `magic`**) a drobných rozdílů ve
    jménech (`EXPLOSION`→`explosive`, `BLUDGEONING`→`blunt`,
    `FREEZING`→`frozen`).
  - **Vědomě přeskočeno, ne tiše zahozeno:** `DAMAGE_<TYP>` multiplikátory
    (jiná sémantika než náš model), `CRIT_CHANCE`/`LIFE_STEAL`/`BLEED_*`
    a cokoliv jiného bez obdoby v našem systému (žádný obecný attribute
    subsystém, viz Fáze 6 mezera) - hráč dostane zprávu s seznamem
    přeskočených atributů, ať ví, že import není 100% a co případně doplnit
    ručně přes GUI/příkazy.
  - 7 nových JUnit testů na `ValhallaMmoImporter.parse()` (čistý string
    parsing, žádný Bukkit) - prázdný/malformovaný vstup, mapování damage/
    resist, více atributů najednou, neediné atributy do `skipped`.
  - **Dodatek (2026-08-26) - reálně ověřeno na skutečném ValhallaMMO
    pluginu.** Stáhl jsem `ValhallaMMO_1.10.3.jar` (Modrinth, kompatibilní
    přímo s 26.2), nainstaloval vedle PurrtechPVE - oba naběhly bez
    konfliktu. Přes jejich `/valhalla drop stone_warhammer 1 <x> <y> <z>
    world` jsem vyhodil jejich reálný předdefinovaný item do světa a
    dočasným debug příkazem (smazán po ověření) spustil na něm skutečný
    `ValhallaMmoImporter`. Výsledek na reálném itemu: `raw PDC string:
    EXTRA_BLUDGEONING_DAMAGE:7.0:ADD_NUMBER:false;GENERIC_ATTACK_SPEED:1.0:...`
    → `contribution: blunt 7.0 FLAT WIELDED`, `skipped: [GENERIC_ATTACK_SPEED,
    KNOCKBACK, STUN_CHANCE]` - přesně sedí s jejich `items.json` (7.0) a
    s naší mapovací tabulkou. **Vedlejší zjištění**: v tomhle testovacím
    prostředí se nenačtené/nedržené chunky rychle odgruntí, takže vyhozené
    itemy zmizí z `World#getEntities()`, dokud chunk násilně nedržíš
    (`/forceload add`) - proto první 4 pokusy vypadaly jako selhání, i když
    `/valhalla drop` fungoval pokaždé. Import je tedy reálně ověřený, ne jen
    podle zdrojáku.

- **Ikony damage typů + action bar combat feedback (2026-08-27), na žádost.**
  - `DamageType` dostal nové pole `icon` - jeden Unicode znak z bloků, které
    Minecraftí výchozí font vykresluje spolehlivě už od starých verzí (Misc
    Symbols U+2600-26FF, Dingbats, řecká písmena, šipky) - žádný resource
    pack není potřeba, na rozdíl od plnobarevných emoji, která se spolehlivě
    nevykreslují. Všech 20 typů má vlastní odlišnou ikonu (např. ❄ mrazivé,
    ⚡ bleskové, ☠ jed, ☀ zářivé, ✝ svaté, Ψ psychické) - test
    `everySeededTypeHasADistinctNonBlankIcon` hlídá, že žádná není prázdná
    ani duplicitní. Ikony se navíc propsaly i do GUI editoru (Custom
    Damages/Odolnosti taby teď ukazují ikonu před názvem typu).
  - `DamagePipeline.applyDetailed(...)` - nová varianta vedle `apply(...)`,
    která kromě celkového čísla vrací i **rozpad podle typu PO aplikaci
    odolnosti** (`Result(total, perType)`) - přesně to, co je potřeba
    zobrazit ("kolik" reálně prošlo, ne syrové číslo před odolností).
    `apply(...)` zůstal beze změny chování, jen teď interně volá
    `applyDetailed(...).total()`.
  - `combat/DamageFeedback.java` - poskládá `ikona částka  ikona částka`
    (např. "❄ 3  ♨ 1.5") jako Adventure `Component`, nuly/záporná čísla
    (typ, co se úplně odolal) se vynechávají.
  - `CombatDamageListener` teď po každém zpracovaném zásahu pošle přes
    `Player#sendActionBar` rozpad **oběma stranám, pokud jsou hráči** -
    útočník vidí, co právě udělil, obránce vidí, co právě dostal (stejná
    čísla, jen jiná barva - červená pro dostané, žlutá pro udělené).
    Mobové (MythicMobs i vanilla) action bar logicky nedostanou.
  - 9 nových JUnit testů (`DamagePipelineTest` na `applyDetailed`,
    `DamageFeedbackTest` na renderování Component - **tohle šlo plně
    otestovat i bez živého serveru**, protože Adventure `Component`/jeho
    serializery jsou čistá knihovna nezávislá na Bukkitu, na rozdíl od
    `ItemStack`/`ItemMeta` jinde v projektu). 75 testů celkem zelených.
  - Ověřeno živě: `runServer` boot bez výjimky s novou signaturou
    `CombatDamageListener` (3 parametry místo 2), a ručně jsem zkontroloval,
    že všech 20 ikon jsou skutečně jednotlivé BMP code-pointy (žádné
    rozbité surrogate páry, žádná duplicita) přes `hex(ord(...))`.
    **Neověřeno**: jak se ikony reálně vykreslí v klientovi téhle konkrétní
    (fiktivní budoucí) verze 26.2, a jak vypadá/čte se action bar zpráva při
    skutečném souboji - obojí potřebuje připojeného hráče, což sandbox nemá.
    Použité Unicode bloky mají dlouhou historii spolehlivého vykreslení v MC
    klientech, ale 100% jistotu pro tuhle přesnou verzi to nedává - doporučuju
    zkusit `/pve item give` na sebe a nechat se/mobem praštit, ať se potvrdí
    že ikony vypadají dobře a ne jako prázdné čtverečky.

- **Item sety s tiered bonusy (2026-08-27), na žádost.** Sety jsou vlastní
  koncept oddělený od item šablon - itemy do nich jen PATŘÍ (`item_set_
  members`), samotný bonus je vlastnost setu, ne itemu.
  - **Datový model**: `item_sets` (key/displayName), `item_set_members`
    (set↔template many-to-many), `item_set_threshold_damage` a `item_set_
    threshold_modifier` (`piece_count` NENÍ předem daný enum ani sloupec s
    pevnou sadou hodnot - je to prostě INTEGER, admin přidává libovolné
    prahy podle potřeby, přesně jak jsi chtěl "nebude to tam předem dané").
    Stejně jako `mob_damage_profile`/`allowed_slots`, sety jsou **live/
    globální config, ne verzované per kus** - bonus setu je pravidlo "kolik
    kusů je zrovna nasazeno", ne statistika napečená do konkrétního itemu.
  - **Prahy jsou kumulativní**: hráč se 4 kusy setu dostane bonus za 1, 2, 3
    I 4 kusy najednou (ne jen nejvyšší dosažený) - běžná konvence u
    tiered set bonusů, řekl bych že je to i to, co jsi popisoval ("když
    bude mít 2 tak tam může být zase něco jiného" - obojí platí zároveň).
  - `ItemSetService` - CRUD setu, správa členů (`addMember`/`removeMember`
    podle template klíče), CRUD prahů poškození/odolnosti podle počtu kusů.
  - `EquipmentResolver` rozšířen - při počítání odchozího poškození i
    odolnosti teď navíc spočítá, kolik nasazených kusů (napříč vanilla sloty
    I virtuálními accessory sloty, stejně jako u WORN bonusů) patří do
    kterého setu, a přičte bonusy všech prahů, které jsou splněné.
  - **Příkazová řádka**: `/pve set create|delete|list|edit|addmember|
    removemember`, `/pve set threshold damage set/remove`, `/pve set
    threshold resist set/remove`.
  - **GUI** (`/pve set edit <key>`, `gui/SetEditorMenu.java`) - tab "Itemy v
    setu" (mřížka členů, klik=odebrat, "+ Přidat item" otevře výběr ze VŠECH
    už vytvořených šablon, které v setu ještě nejsou - přesně "otevřeti to
    menu s itemy který už jsou vytvořené" jak jsi chtěl) a tab "Prahy bonusů"
    (mřížka existujících prahů seřazená podle počtu kusů, "+ Přidat práh"
    otevře chatový prompt `<počet kusů> damage|resist <typ> <hodnota>
    [flat|percent]` - počet kusů se zadává na místě, nic není předem dané).
  - 27 nových JUnit testů (4 repository třídy + `ItemSetService` - CRUD,
    duplicitní klíč, cizí klíče, kumulativní řazení prahů, kaskádové mazání).
    102 testů celkem zelených.
  - Ověřeno živě: celá příkazová posloupnost (2× create item → create set →
    2× addmember → damage threshold → resist threshold → list ukazuje
    "dragon-set (2 itemů)" → `/pve set edit` z konzole správně odmítnuto →
    remove threshold → removemember → delete → list prázdný) proběhla
    přesně podle očekávání, žádná výjimka, všechny 4 nové tabulky v DB
    existují. **Neověřeno**: samotné GUI (`SetEditorMenu`) klikací interakce
    se skutečným hráčem - stejné omezení jako u `ItemEditorMenu`, sandbox
    nemá klienta. Doporučuju vyzkoušet `/pve set edit` naživo - proklikat
    přidání/odebrání itemů přes "+ Přidat item", přidat práh přes chat
    prompt, a hlavně reálně ověřit v souboji, že se bonus setu opravdu
    projeví v action baru (viz předchozí feature) až budeš mít nasazený
    dostatečný počet kusů.

- **Bugfix (2026-08-27): pád na `NoClassDefFoundError: io/lumine/mythic/
  bukkit/MythicBukkit` na reálném serveru uživatele.** Nahlášeno z produkce -
  `EquipmentResolver.resolveResistance` spadlo při KAŽDÉM
  `EntityDamageByEntityEvent` (hráč útočil na Wolf moba, server běžel na
  Leaf/Paper 1.21.11 s ModelEngine 4.1.0 nainstalovaným).
  - **Kořenová příčina**: `PurrtechPVE.onEnable` bral
    `isPluginEnabled("MythicMobs")` jako důkaz, že MythicMobs API (přesně
    `io.lumine:Mythic-Dist:5.10.0`, na které je `MythicMobsBridge`
    zkompilovaný) je na runtime classpath dostupné. To je špatný
    předpoklad - **plugin doslova pojmenovaný "MythicMobs" může běžet a
    projít touhle kontrolou, aniž by měl kompatibilní/vůbec žádné tyhle
    třídy** (starší/forkovaná/nekompatibilní verze) - `NoClassDefFoundError`
    pak vyletí až při PRVNÍM sáhnutí na `MythicBukkit`, tedy při prvním
    souboji, ne při startu, a to při KAŽDÉM dalším souboji znovu (event
    listener nikdy nedostal šanci to zachytit a zapamatovat si to).
  - **Oprava, dvě vrstvy obrany**:
    1. `MythicMobsBridge.probe()` (nová metoda) - vynutí rozřešení
       MythicMobs tříd hned při startu, na jednom kontrolovaném místě.
       `PurrtechPVE.onEnable` obalí konstrukci bridge + `probe()` do
       `catch (Throwable t)` (nejen `Exception` - `NoClassDefFoundError` je
       `Error`) - při neshodě to jednou nahlásí varování do konzole
       ("...jeho API neodpovídá tomu, pro co je PurrtechPVE postavený...")
       a `mythicMobsBridge` zůstane `null`, přesně jako kdyby MythicMobs
       vůbec nebyl nainstalovaný.
    2. `EquipmentResolver`'s jediné volání bridge (`mythicMobInternalName`
       v `resolveResistance`) je navíc taky obalené v `catch (Throwable)` -
       obrana do hloubky, kdyby probe() prošel, ale konkrétní volání za
       běhu selhalo z jiného důvodu (částečná nekompatibilita API).
  - **Ověřeno dvěma způsoby**:
    1. **3 nové JUnit testy** (`MythicMobsBridgeTest`) - `Mythic-Dist` je
       `compileOnly`, takže na testovacím runtime classpath chybí úplně
       stejně jako u nekompatibilní verze na produkci - `probe()`/
       `isMythicMob()`/`mythicMobInternalName()` **skutečně a deterministicky
       hodí `NoClassDefFoundError`** v testu, čímž se přesně reprodukuje
       produkční pád bez potřeby živého serveru. 105 testů celkem zelených.
    2. Živě: zkusil jsem postavit falešný plugin doslova pojmenovaný
       "MythicMobs" (žádné skutečné MythicMobs třídy) a nainstalovat ho
       vedle PurrtechPVE - narazil jsem přitom na zajímavost, že
       `paper-plugin.yml`-formátované pluginy (PurrtechPVE) se enable-ují
       v jiné (dřívější) fázi než klasické `plugin.yml` pluginy, takže
       `isPluginEnabled` u PurrtechPVE ve chvíli jeho `onEnable` byl false a
       tenhle konkrétní test tak akorát ověřil "MythicMobs není přítomen"
       větev (taky správně, bez pádu) - reálnou "isPluginEnabled=true ale
       třída chybí" cestu spolehlivě prokazují až ty JUnit testy výše.
  - Živě ověřeno i to, že souboj (`/damage ... by ...` mezi dvěma zombie
    entitami, žádný hráč potřeba) po opravě proběhne čistě bez pádu.

- **MythicMobs equipment tab, hromadný ValhallaMMO import, a menu na správu
  itemů (2026-08-27), na žádost.** Tři samostatné featury z jednoho zadání.
  - **MythicMobs "dej armor/zbraň mobovi" tab**: nová tabulka `mob_equipment`
    (`mythic_mob_internal_name`, `slot`, `template_id`, FK na
    `item_templates(id) ON DELETE CASCADE`) + `MobEquipmentRepository`.
    `MythicMobsBridge.listMobTypeInternalNames()` - vrátí všechny mob typy
    nakonfigurované na serveru (nová metoda, stejná `MythicBukkit` API
    třída jako zbytek bridge). `MythicMobEquipmentListener` na
    `MythicMobSpawnEvent` - při spawnu moba mu nasadí (na živo vyrenderované,
    ne verzované - jde o dočasný mob-held item, ne hráčův kus) cokoliv, co
    má nastavené v `mob_equipment`, obalené `catch (Throwable)` jako zbytek
    package (viz předchozí bugfix). Nový tab "MythicMobs" v
    `ItemEditorMenu` (mezi Trinket a Publikovat) - mřížka všech mob typů,
    klik = nasadí AKTUÁLNĚ EDITOVANÝ item danému mobovi (slot se odhadne
    z materiálu - `_HELMET`/`_CHESTPLATE`/.../`SHIELD` → odpovídající
    `EquipmentSlot`, jinak `HAND`), shift+klik = odebrat. Přesně "bude
    záložka která ti ukáže všechny moby... a pak jim to tam může dát ten
    armor nebo tu zbraň" jak jsi chtěl. `PurrtechPVE.mythicMobsBridge` a
    nový `mobEquipmentRepository` povýšeny na pole s gettery (dřív byl
    bridge jen lokální proměnná v `onEnable`), listener se registruje
    jen po úspěšném `probe()`, ve vlastním `catch (Throwable)`.
  - **Hromadný ValhallaMMO import** (`/pve item import valhallaall`,
    `ValhallaMmoBulkImporter`) - našel jsem přesně jejich `items.json`
    formát čtením `CustomItemRegistry`/`ItemStackGSONAdapter`/`GsonAdapter`/
    `ItemUtils` na jejich GitHubu (žádná závislost na jejich pluginu/API,
    stejná filosofie jako stávající `ValhallaMmoImporter` - jen vanilla
    Gson, bundlovaný s Paper runtime, `compileOnly`). Pole `{id, item,
    modifiers}` na top-levelu; `item` je `ItemStack` přes jejich vlastní
    `BukkitObjectOutputStream` a base64 (`Base64.getMimeDecoder()` zvládá
    jejich řádkované kódování); `modifiers` je `{MOD_TYPE: <plně
    kvalifikovaný název třídy>, DATA: {...}}` - čte se jen `DATA.attribute`/
    `DATA.value` z `DefaultAttributeAdd` položek, generickým JSON přístupem
    (nikdy se nesahá na skutečnou ValhallaMMO třídu). `ValhallaMmoImporter`
    refaktorován - `parse(String)` teď jen rozparsuje `ATTR:value:...`
    řetězec do `Map<String,Double>` a deleguje na nové `fromAttributes(Map)`,
    které sdílí obě cesty (single-item PDC string i bulk JSON) přes stejné
    damage/resist mapovací tabulky.
    - Klíč nové šablony = `"valhalla-" + <sanitized id>` - STABILNÍ přes
      opakované importy (žádné `-2`/`-3` navyšování při re-run), takže
      opakované spuštění po přidání nových itemů do ValhallaMMO správně
      naimportuje jen to nové a nahlásí zbytek jako "už existuje" - ověřeno
      živě (viz níže), tohle byl skutečný bug, který jsem při ověřování
      chytil a opravil (původní verze dedupovala klíče v rámci jednoho
      importu příponou, což tenhle idempotentní re-run rozbíjelo).
    - Hlášení: `<imported> naimportováno, <failed> přeskočeno`, seznam
      přeskočených itemů s důvodem (duplicitní klíč / nešlo dekódovat), a
      seznam atributů bez obdoby (sdílí `item.import-skipped` s existujícím
      single-item importem).
    - 2 nové JUnit testy na `fromAttributes()`. 114 testů celkem zelených.
    - **Ověřeno živě** (dočasný debug příkaz `pve debugvalhallafixture`,
      smazaný před commitem): vygenerovaná fixture se 2 unikátními a 1
      duplicitním ValhallaMMO id, jedním `null` hodnotovým atributem
      (`EXTRA_POISON_DAMAGE`) a jedním bez mapování (`CRIT_DAMAGE`) - první
      import: 2 naimportováno / 1 přeskočeno (duplicita v rámci dávky
      správně odhalena), `CRIT_DAMAGE` správně nahlášen jako přeskočený,
      `null` hodnota tiše (správně) vynechána. Druhé spuštění hned poté:
      0 naimportováno / 3 přeskočeno - všechny 3 správně nahlášené jako
      "už existuje", potvrzuje idempotenci re-importu.
  - **Menu na správu všech itemů** (`/pve item menu`, `gui/ItemListMenu.java`
    + `ItemListHolder`) - nahrazuje textový `/pve item list` (ten zůstává
    pro konzoli) klikací mřížkou se stránkováním (45 itemů/stránka, šipky
    předchozí/další, počítadlo stran). Tlačítko "+ Vytvořit item" (chatový
    prompt `<klíč> <materiál> <název>`, stejný vzor jako ostatní menu).
    Na ikoně itemu: **obyčejný klik = otevře `ItemEditorMenu`** k úpravě,
    **shift+PRAVÝ klik = smaže** šablonu, **shift+LEVÝ klik = dá kopii**
    do inventáře hráče (`renderGiveable`) - přesně "pravým shift clickem
    smazat, levým shift clickem získat do invu, klik = upravit" jak jsi
    chtěl. Rozlišení SHIFT_LEFT/SHIFT_RIGHT bylo potřeba přidat do
    `ItemEditorListener` (dřív se předávalo jen `isShiftClick()` bool,
    teď `event.getClick()` pro nový `ItemListHolder`, ostatní menu si
    pořád vystačí s boolem).
  - **Neověřeno živě**: samotné klikací chování `ItemListMenu` (stránkování,
    shift-left/shift-right rozlišení, add-item flow) - stejné omezení jako
    u `ItemEditorMenu`/`SetEditorMenu`, sandbox nemá připojeného hráče.
    Ověřeno kompilací + `runServer` bootem (command tree se zaregistruje
    bez pádu, `/pve item create`+`list` pořád fungují) a code review proti
    identickým, už production-ověřeným vzorům v `ItemEditorMenu`/
    `SetEditorMenu`. Doporučuju vyzkoušet `/pve item menu` naživo -
    projít stránkování při >45 šablonách (např. po ValhallaMMO importu),
    a proklikat všechny 3 akce na pár itemech.

- **Bugfix (2026-08-27): pád na `NoSuchMethodError:
  TextComponent$Builder.build()` na reálném serveru uživatele.** Nahlášeno
  z produkce - spadlo při KAŽDÉM souboji s zapojeným hráčem
  (`CombatDamageListener` volá `DamageFeedback.render` pro action bar).
  Server běží na "Leaf" 1.21.11 (Paper fork) + ModelEngine 4.1.0.
  - **Kořenová příčina**: stejná rodina bugů jako předchozí MythicMobs
    pád, ale tentokrát u Adventure (Kyori) - `DamageFeedback.render`
    stavěl text přes `TextComponent.Builder builder = Component.text();
    ...; builder.build();`. `TextComponent.Builder.build()` je covariantní
    override generické `Buildable.Builder<Component>.build()` metody -
    javac k tomu při kompilaci vygeneruje bridge metodu, jejíž přesná
    signatura závisí na tom, jak přesně je ta hierarchie rozhraní
    strukturovaná v KONKRÉTNÍ verzi Adventure knihovny. Tenhle plugin se
    kompiluje proti Paperu `26.2.build.+` (nejnovější dostupná verze v
    tomhle sandboxu), ale uživatelův živý server běží o dost starší/jinak
    sestavený Paper fork (Leaf na Minecraftu 1.21.11) s jinou verzí
    Adventure - ta bridge metoda, kterou zkompilovaný bytecode volá,
    na runtime prostě neexistuje → `NoSuchMethodError`.
  - **Oprava**: `DamageFeedback.render` přepsán, aby se `TextComponent.
    Builder`/`.build()` vůbec nedotkl - skládá se přes opakované
    `Component.append(Component)` na neměnném `Component` (počínaje
    `Component.empty()`), což je mnohem starší a stabilnější metoda přímo
    na `Component`, bez jakékoliv builder/bridge-metody k rozlišení.
    Jediné místo v celém projektu, které `TextComponent.Builder`
    používalo (ověřeno grepem).
  - **Ověřeno**: kompilace čistá, všech 5 existujících `DamageFeedbackTest`
    testů (čistá logika/serializace, žádný živý server potřeba) prošlo
    beze změny výstupu - stejný text/pořadí/formátování jako předtím.
    114 testů celkem zelených. `runServer` boot bez výjimky. **Nejde
    100% reprodukovat tenhle konkrétní `NoSuchMethodError` v sandboxu**,
    protože sandbox i testy běží na STEJNÉ (novější) Adventure verzi, na
    které se to kompiluje - přesně to samé zjištění platí obecně pro
    tuhle třídu bugů (verze API se liší jen na uživatelově reálném
    serveru). Oprava se ale opírá o vyhýbání se konkrétní API ploše z
    hlášeného stack trace, ne o hádání - `Component.append`/`Component.
    empty` jsou v Adventure beze změny od verze 4.0.
  - **Širší riziko, které tenhle bug odhalil**: pokud se cílová verze
    serveru (Leaf 1.21.11) dost liší od té, proti které se tenhle plugin
    kompiluje (Paper 26.2 - nejnovější v sandboxu), můžou se objevit další
    podobné `NoSuchMethodError`/`NoSuchFieldError` na jiných API plochách,
    ne jen Adventure. Zvaž mi říct přesnou verzi Paperu/Minecraftu, na
    kterou svůj live server provozuješ, ať buildujeme přímo proti tomu -
    je to spolehlivější než opravovat každý mismatch jednotlivě, jak se
    objeví.

- **Přecílení buildu na produkční verzi: Leaf 1.21.11 (2026-08-27), na
  žádost po předchozím bugfixu.** Řeší kořenovou příčinu celé třídy
  API-mismatch pádů výše, ne jen jeden konkrétní symptom.
  - `build.gradle.kts`: `compileOnly`/`testImplementation` na
    `io.papermc.paper:paper-api:26.2.build.+` nahrazeny za
    `cn.dreeam.leaf:leaf-api:1.21.11-R0.1-SNAPSHOT` (repo
    `maven.leafmc.one/snapshots`, ověřeno přes jejich `maven-metadata.xml`,
    že tahle verze fakticky existuje - odpovídá přesně tomu, co je vidět
    ve tvém stack trace). Leaf je API-kompatibilní s Paperem (stejné
    `org.bukkit`/`io.papermc.paper` třídy), takže je to jinak 1:1 náhrada.
  - `com.google.code.gson:gson` sníženo z `2.14.0` na `2.13.2` - zjištěno
    reálným stažením skutečného Paper 1.21.11 buildu (`run/libraries/...`
    po `runServer` s `minecraftVersion("1.21.11")`), ne odhadem. Stejná
    logika jako u leaf-api - novější Gson, než co je fakticky na
    runtime classpath, riskuje stejnou třídu `NoSuchMethodError`, kterou
    řešil předchozí bugfix.
  - `paper-plugin.yml`: `api-version` opraveno z `'26.2'` na `'1.21'` -
    bylo nastavené na budoucí/neexistující API verzi vzhledem k reálnému
    serveru, což je samo o sobě podezřelé z přispění k mismatch chování.
  - `runServer` task (`minecraftVersion`) přepnut z `"26.2"` na
    `"1.21.11"` - lokální dev-server teď boot­uje skutečný PaperMC build
    pro STEJNOU verzi Minecraftu jako tvůj live server (samotný Leaf jar
    tenhle Gradle plugin stáhnout neumí, ale skutečný Paper na stejné MC
    verzi je nesrovnatelně blíž realitě než nejnovější dostupný build).
  - **Ověřeno**: čistá kompilace i běh všech 114 testů proti `leaf-api`
    beze změny (žádný z nich nepoužívá nic, co by se mezi verzemi lišilo).
    Živě: reálný Paper `1.21.11-132-ver` boot čistý bez výjimky
    (`Implementing API version 1.21.11-R0.1-SNAPSHOT` - `api-version`
    teď sedí), a celá command-line posloupnost (create → damage set →
    resist set → list → set create → addmember → set list) proběhla
    přesně stejně jako předtím na 26.2. Starou `run/world` složku bylo
    potřeba smazat - nejde otevřít novější formát světa (z předchozích
    26.2 běhů) starším serverem, nesouvisí s pluginem.
  - **Nedá se ověřit v sandboxu**: skutečný Leaf jar (jen "opravdový
    Paper 1.21.11" jako náhrada) - pokud Leaf sám patchuje/mění chování
    nějaké API plochy nad rámec toho, co dělá čistý Paper, tohle by to
    nezachytilo. Nejbližší dostupná aproximace v tomhle sandboxu.

- **Import celého itemu (enchanty, custom model data) + rozšíření
  ValhallaMMO damage mapování (2026-08-27), na žádost.**
  - **Enchanty jsou nová vlastnost šablony**, ne jen importní detail -
    nová tabulka `item_template_enchantment` (template_id, enchantment_key,
    level) + `TemplateEnchantmentRepository`, verzované/snapshotované
    stejně jako damage contributions/type modifiers (`TemplateSnapshot`
    dostal nové pole `enchantments`, `ItemTemplateService.setEnchantment/
    removeEnchantment/enchantments`). `ItemRenderer` je aplikuje přes
    `meta.addEnchant(..., ignoreLevelRestriction=true)` - jde o
    adminem definované custom itemy, takže úrovně nejsou omezené
    vanilla maximem. Příkazy `/pve item enchant set|remove <key>
    <enchant> <level>` - enchant se zadává BEZ `minecraft:` prefixu
    (Brigadier neumí neuvozenou hodnotu s dvojtečkou; s prefixem jde
    zadat jen v uvozovkách, viz ověření níže).
    - **Migrace existující DB**: `item_template_snapshot` už měla
      produkční data na živém serveru, takže `CREATE TABLE IF NOT
      EXISTS` s novým sloupcem samo o sobě nestačí (no-op na existující
      tabulce) - přidán `Schema.addColumnIfMissing` helper, co pustí
      `ALTER TABLE ... ADD COLUMN enchantments TEXT NOT NULL DEFAULT
      ''` jen když sloupec ještě neexistuje. **Ověřeno** ručně
      sestavenou "pre-migrační" DB (starý formát bez sloupce, se
      skutečnými daty) - boot proběhl čistě, sloupec se přidal,
      existující řádek se dobackfilloval na `''`, nová tabulka
      `item_template_enchantment` vznikla, a `/pve item list` na
      existujícím itemu fungovalo beze změny.
  - **Custom model data teď jde nastavit i při vytvoření** (dřív jen
    přes pozdější rebase) - nový `ItemTemplateService.create(key,
    material, customModelData, displayName, createdBy)` overload
    (starý 4-argumentový beze změny, jen deleguje s `null`).
  - **Import teď přenáší CMD i enchanty**: `/pve item import valhalla`
    (jednotlivý item z ruky) i `/pve item import valhallaall` (hromadný
    z JSON) čtou `ItemMeta`/`ItemStack` drženého/dekódovaného itemu a
    volají `create(..., customModelData, ...)` + `setEnchantment` pro
    každý enchant. **Ověřeno živě**: fixture s DIAMOND_SWORD, custom
    model data 1234, sharpness 7 a unbreaking 3 - po hromadném importu
    `SELECT` přímo z DB potvrdil `custom_model_data=1234` a oba dva
    enchanty se správnými úrovněmi.
  - **Rozšíření ValhallaMMO mapování** (viz `ValhallaMmoImporter`'s
    class javadoc pro plné zdůvodnění, ověřeno čtením jejich
    `ItemAttributesRegistry`'s `StatFormat` pro každý atribut, ne
    odhadem):
    - Nové flat kontribuce: `BLEED_DAMAGE`→bleed, `ARROW_DAMAGE`→piercing.
    - `DAMAGE_ALL`→physical, taky flat i přesto, že sdílí `StatFormat`
      s procentuální rodinou níže - nemá totiž svůj vlastní flat
      protějšek (žádné `EXTRA_ALL_DAMAGE`), takže jako procento by vždy
      vyšlo 0 a bylo by k ničemu. Explicitní rozhodnutí na tvoje
      vyžádání, ne přehlédnutí.
    - Nové odolnosti: `BLEED_RESISTANCE`→bleed, `BLUDGEONING_RESISTANCE`
      →blunt, `PROJECTILE_RESISTANCE`→piercing (aproximace).
    - `DAMAGE_RESISTANCE` (odolnost vůči VŠEMU) rozprostřena na
      `TypeModifier` pro každý registrovaný damage typ najednou.
    - **Nová procentuální rodina** `DAMAGE_<TYP>` (fire/magic/poison/
      radiant/freezing/explosion/lightning/necrotic/bludgeoning):
      "zvyš poškození typu X, který item už dává, o N %" - NENÍ nová
      kontribuce, násobí existující flat hodnotu (z `EXTRA_<TYP>_
      DAMAGE`) faktorem `(1 + N)`. Pokud item žádné poškození toho typu
      nedává, není co násobit → **žádná kontribuce se nepřidá** (zůstane
      na 0), přesně jak jsi zadal.
    - `DAMAGE_MELEE`, `CRIT_DAMAGE`, `DAMAGE_PLAYER`, `VELOCITY_DAMAGE`
      **zůstávají nenamapované** i po tvém doplnění seznamu - nejsou to
      damage-type-klíčované staty jako zbytek (melee/crit/hráč/rychlost
      jsou samostatné mechaniky, který tenhle plugin vůbec nemá -
      crit systém, PvP-podmíněný bonus, útok-podle-kategorie), takže na
      ně stejné pravidlo procenta/flat nejde aplikovat (není typ, co
      by se násobil/přičítal). Nahlášené v `import-skipped` jako dřív.
  - 6 nových JUnit testů (`ValhallaMmoImporterTest`) + 1 rozšířený
    (`ItemTemplateSnapshotRepositoryTest` o enchant round-trip). 120
    testů celkem zelených.
  - **Neověřeno**: vizuální vzhled enchantů na skutečném vydaném itemu
    (lore/glint) - potřebuje připojeného hráče, stejné omezení jako
    jinde. Databázové round-tripy a `ItemRenderer`'s volání `meta.
    addEnchant` jsou ale ověřené (kompilace proti reálné 1.21.11 API +
    JUnit na repository vrstvě).

- **3 typy armoru: lehký/střední/těžký + jejich benefity (2026-08-27),
  na žádost.** Zavírá i mezeru, na kterou jsem narazil dřív (ValhallaMMO
  `LIGHT_ARMOR`/`HEAVY_ARMOR` byly na seznamu "bez obdoby" - teď obdoba
  existuje).
  - **Nový enum `ArmorClass`** (LIGHT/MEDIUM/HEAVY) - fixní, 3 hodnoty,
    ne rozšiřitelné adminem (přesně jak jsi popsal). Přidán jako nové
    pole na `ItemTemplate` (`armor_class` sloupec), stejné live/
    neverzované zacházení jako `allowedSlots`/trinket flag - přiřazení
    typu armoru itemu NEBUMPuje verzi, není to "stat", je to klasifikace
    (`ItemTemplateService.setArmorClass`). **Migrace**: `item_templates`
    už má produkční data, takže stejný `addColumnIfMissing` mechanismus
    jako u enchantů - ověřeno na ručně sestavené DB se starým schématem
    (bez `armor_class` sloupce) + reálnými daty, boot proběhl čistě,
    sloupec se přidal.
  - **Benefity typu armoru jsou oddělené od konkrétního itemu** - nová
    tabulka `armor_class_profile` (armor_class, damage_type_key, percent),
    stejný vzor jako `mob_damage_profile`: live/globální config, aplikuje
    se OKAMŽITĚ na všechny kusy daného typu armoru, i ty už vydané, beze
    změny verze. `EquipmentResolver.resolveResistance` teď ke každému
    nasazenému kusu, kromě jeho vlastních `item_type_modifier` řádků,
    přičte i profil jeho typu armoru (pokud má nějaký nastavený).
    Zatím jen odolnosti/slabiny (ne damage bonus) - řekni, pokud chceš
    i to.
  - **"Někde v menu"**: dvě místa. (1) Nový tab "Typ armoru" v
    `ItemEditorMenu` (mezi Trinket a MythicMobs) - 4 volby (Žádný/
    Lehký/Střední/Těžký), radio-select styl jako u trinket slotů, s
    tlačítkem co otevře (2) nové samostatné GUI `ArmorClassMenu`
    (`/pve armorclass menu`) - 3 taby (Lehký/Střední/Těžký), v každém
    mřížka všech damage typů s klik=nastavit procenta (odolnost/slabina)
    přes chat prompt, shift+klik=smazat - přesně stejný vzor jako RESIST
    tab v `ItemEditorMenu`, jen napojený na `armor_class_profile` místo
    na konkrétní šablonu. Taky příkazy `/pve item armor <key> <light|
    medium|heavy|none>` a `/pve armorclass set|remove|list`.
  - **"Medium = vanilla vzhled, light/heavy = custom"**: tohle nevyžaduje
    žádný nový kód - už existující nástroje (base materiál + custom
    model data, nastavitelné v Základ tabu) stačí. Medium prostě necháš
    na normálním vanilla armor materiálu (`IRON_CHESTPLATE` apod.),
    light/heavy postavíš na jiném základu + custom model data pro
    resource-pack vzhled. Info o tomhle je i přímo v novém tabu.
  - **Chyba nalezená a opravená při živém ověřování**: `Placeholder.
    unparsed("armorClass", ...)` shazovalo KAŽDÝ příkaz, co ho použil,
    na `IllegalArgumentException: Tag name must match pattern
    [!?#]?[a-z0-9_-]*` - MiniMessage tag jména musí být malými
    písmeny, `armorClass` (camelCase) není platné. Přejmenováno na
    `class` (jednoslovné, malými písmeny, stejná konvence jako `mob`/
    `type`/`key` v celém projektu) - tohle je přesně ten druh chyby,
    který jednotkové testy nechytí (nedotýká se DB/logiky, jen za běhu
    sestaveného MiniMessage template), proto to teprve živé ověření
    odhalilo.
  - 8 nových JUnit testů (`ArmorClassProfileRepositoryTest` - CRUD,
    `ItemTemplateServiceTest`/`ItemTemplateRepositoryTest` - armor
    class se ukládá, výchozí `null`, nebumpuje verzi). 128 testů
    celkem zelených.
  - **Ověřeno živě**: boot na starém schématu (migrace), `/pve item
    armor` nastavení/vyčištění, `/pve armorclass set/list`, neplatný
    typ armoru správně odmítnut, a přímá SQL kontrola potvrdila
    `armor_class` sloupec i `armor_class_profile` řádky přesně
    odpovídají tomu, co bylo zadáno přes příkazy.
  - **Neověřeno**: `EquipmentResolver`'s promítnutí profilu do
    skutečného souboje (žádný unit test pro `EquipmentResolver` v
    projektu vůbec neexistuje - stejná konvence jako u item setů/mob
    profilů, potřebuje živého hráče v souboji) a samotné GUI (nový tab
    + `ArmorClassMenu`) klikací interakce - stejné omezení jako
    všechny ostatní menu v tomhle projektu.

- **Penetrace 3 typů armoru (2026-08-27), na žádost.** Nová zbraňová
  vlastnost - `LIGHT_ARMOR_PENETRATION`/`MEDIUM_ARMOR_PENETRATION`/
  `HEAVY_ARMOR_PENETRATION`, přesně jak jsi zadal.
  - **Přesná mechanika, jak jsi popsal**: než se spočítá poškození
    útoku, sníží se cílova odolnost o zadaný počet procentních bodů,
    a TEPRVE POTOM se dopočítá damage - nic se přitom nemaže ani
    neodebírá z žádného inventáře, je to čistě výpočet pro tenhle
    jeden zásah.
  - **Klíčové designové rozhodnutí** (nebylo explicitně zadané, tak
    vysvětluju): penetrace ubírá jen z toho, co cíli dává SDÍLENÝ
    bonus jeho typu armoru (`armor_class_profile` z minulého požadavku),
    NE z vlastní/individuální odolnosti nastavené přímo na tom kusu.
    Dává to smysl navazovat na featuru, kterou jsme spolu právě
    postavili - "penetruješ typ armoru" == "obejdeš benefit, co ten typ
    armoru dává", ne "sundáš cokoliv, co má ten kus nastavené navíc".
    Řekni, pokud jsi to myslel jinak.
  - **Nová zbraňová vlastnost** (`ArmorPenetration` record, `item_
    armor_penetration` tabulka, `ArmorPenetrationRepository`) - na
    rozdíl od typu armoru (živá klasifikace) je penetrace stat jako
    damage contribution, takže JE verzovaná/snapshotovaná
    (`TemplateSnapshot` dostal pole `armorPenetration`, nová migrace
    `item_template_snapshot.armor_penetration` stejným
    `addColumnIfMissing` mechanismem).
  - **`EquipmentResolver.resolveResistance`** teď bere i útočníka (dřív
    jen obránce) - při počítání obranyho odolnosti si zvlášť eviduje,
    kolik z toho pochází konkrétně ze sdíleného profilu kterého typu
    armoru, a na konci od těchto čísel odečte útočníkovu wielded
    zbraní penetraci daného typu. Není to ošetřené na nulu - pokud
    penetruješ víc, než cíl z toho typu armoru má, přehoupne se to do
    mínusu (bonus damage), běžná konvence u penetration mechanik.
  - **GUI**: nový tab "Penetrace armoru" v `ItemEditorMenu` (3 řádky -
    Light/Medium/Heavy, klik = zadat procenta přes chat, shift+klik =
    smazat) + příkazy `/pve item penetration set|remove <key>
    <light|medium|heavy> <množství>`. Taky se zobrazuje v lore itemu
    (`ItemRenderer` - nová sekce "Penetrace armoru").
  - 8 nových JUnit testů (`ArmorPenetrationRepositoryTest` - CRUD,
    `ItemTemplateServiceTest` - bumpuje verzi na rozdíl od typu
    armoru, výchozí prázdný seznam) + rozšířený snapshot round-trip
    test. 136 testů celkem zelených.
  - **Ověřeno živě**: boot na ručně sestavené DB se starým schématem
    (bez `armor_penetration` sloupce i bez `item_armor_penetration`
    tabulky) proběhl čistě, migrace přidala obojí. Celá příkazová
    posloupnost (set light → set heavy → neplatný typ armoru správně
    odmítnut → remove light) - verze postupně 1→2→3→4, přesně podle
    počtu úspěšných mutací. Přímá SQL kontrola potvrdila, že KAŽDÁ
    verze snapshotu má přesně tu kombinaci penetrací, co v tu chvíli
    měla platit (v2: jen light, v3: light+heavy, v4: jen heavy) -
    tohle ověřuje, že se verzování/pinning penetrace chová naprosto
    stejně jako u damage contributions.
  - **Nedá se ověřit v sandboxu**: samotný efekt v souboji (žádná
    živá entita s naším custom weaponem v ruce proti cíli s armor
    class benefitem - potřebuje buď připojeného hráče, nebo MythicMobs
    s nakonfigurovaným mobem, ani jedno tu k dispozici). Logika v
    `EquipmentResolver` prošla důkladným code review a staví na už
    ověřeném vzoru (`resolvedItemOf`, stejné jako zbytek třídy), ale
    doporučuju to při první příležitosti vyzkoušet naživo - dej jeden
    hráč armor s nastaveným typem armoru + benefitem, druhý zbraň s
    penetrací proti němu, a sleduj action bar breakdown, jestli
    číslo sedí.

- **Šance/doba krvácení + kritický zásah (2026-08-27), na žádost.**
  Dvě nové zbraňové vlastnosti - první DOT (damage-over-time) mechanika
  v celém projektu (dřív bylo "bleed" jen typ poškození s
  `dot`/`dotPeriodTicks`/`dotTickPercent` poli v `DamageType`, co od
  Fáze 1 nikdo nikdy nepoužil - tahle featura je konečně zapíná).
  - **`BleedEffect`** (šance %, doba trvání v sekundách) - na úspěšný
    hod při zásahu spustí tikající "bleed" poškození po dobu trvání.
    Velikost jednoho ticku = `dotTickPercent` z "bleed" damage typu
    (0.1 = 10 %) krát SUROVÉ (před rozpočtem na typy/odolnosti)
    poškození zásahu, co krvácení spustil - využívá pole, co už na
    `DamageType` bylo, jen se nikdy nepoužilo. Nový `BleedManager`
    (běhový, nic se nepersistuje - restart serveru = konec všech
    krvácení, stejně jako vanilla potion efekty) - jeden opakovaný
    task (perioda = "bleed" typu vlastní `dotPeriodTicks`) tiká
    VŠECHNA aktivní krvácení najednou, ne časovač na každé zvlášť.
    Nová aplikace na už krvácející cíl PŘEPÍŠE tu předchozí (ne
    prodlouží/nasčítá) - nebylo zadané chování při stackování, tak
    zvolen nejjednodušší/nejbezpečnější default; řekni, pokud má
    stackovat.
  - **`CriticalEffect`** (šance %, bonus poškození %) - na úspěšný hod
    vynásobí CELÉ výsledné poškození zásahu (ne jednotlivý typový
    kbelík) faktorem `1 + bonus/100`, stejná konvence jako vanilla
    sword krit. Action bar breakdown se při kritu přepočítá stejným
    faktorem, aby čísla v součtu seděla na to, co se doopravdy stalo.
  - Obojí je zbraňová vlastnost (WIELDED, jen na drženém itemu) a
    JE verzovaná/snapshotovaná jako damage contribution (na rozdíl od
    typu armoru) - nová pole na `TemplateSnapshot` + stejný
    `addColumnIfMissing` migrační mechanismus jako u předchozích
    přídavků do už nasazené `item_template_snapshot` tabulky.
  - **`EquipmentResolver.resolveResistance`** teď umí `attacker=null`
    (pro `BleedManager`'s tiky, který nemají konkrétního útočníka -
    penetrace armoru se v tom případě prostě přeskočí). Nové
    `resolveCriticalEffect`/`resolveBleedEffect` čtou útočníkovu
    drženou zbraň stejným `resolvedItemOf` vzorem jako zbytek třídy.
  - **GUI**: nový tab "Krvácení & Krit" v `ItemEditorMenu` (4 řádky -
    šance krvácení, doba krvácení, šance kritu, bonus poškození kritu;
    klik = zadat přes chat, shift+klik = smazat CELÝ efekt). Řádek
    tabů byl už plně obsazený (0-8), tak se Zavřít tlačítko přesunulo
    na konec řádku 1 (slot 17, volný ve všech tabech) - jediná viditelná
    změna existujícího UI, jinak nic nepřesunuto. Taky příkazy
    `/pve item bleed set|remove <key> [<šance> <doba>]` a `/pve item
    crit set|remove <key> [<šance> <bonus>]`.
  - 16 nových JUnit testů (`BleedEffectRepositoryTest`,
    `CriticalEffectRepositoryTest` - CRUD, `ItemTemplateServiceTest` -
    bumpují verzi, výchozí prázdné, `DamageFeedbackTest` - nový crit
    marker "CRIT! " a že 3-arg/4-arg(false) přetížení dají stejný
    výstup). 152 testů celkem zelených.
  - **Ověřeno živě**: boot na ručně sestavené DB se starým schématem
    (bez `bleed_effect`/`critical_effect` sloupců i bez obou nových
    tabulek) proběhl čistě, migrace přidala všechno. Celá příkazová
    posloupnost (bleed set → crit set → list → bleed remove) - verze
    1→2→3→4. Přímá SQL kontrola potvrdila, že historie snapshotů
    (v1: nic, v2: jen bleed, v3: bleed+crit, v4: jen crit) přesně
    odpovídá tomu, co mělo v tu chvíli platit, a živé tabulky
    (`item_bleed_effect`/`item_critical_effect`) správně odrážejí
    finální stav. Opakovaný bleed-tick task běžel celou dobu bootu
    (~15 s, několik cyklů) bez chyby i s prázdnou množinou aktivních
    krvácení.
  - **Nedá se ověřit v sandboxu**: samotné triggerování v souboji
    (šance na krvácení/krit se hodí jen při reálném zásahu mezi
    entitami, kde je aspoň jeden hráč - žádný připojený hráč tu není)
    a tikání krvácení na živém cíli. Doporučuju při první příležitosti
    nastavit vysokou šanci (100 %) na testovací zbrani a zkusit
    reálný zásah - sleduj, jestli se objeví "CRIT! " v action baru a
    jestli cíl dostává periodické tiky poškození po zásahu.

- **DAMAGE tab: "Add" tlačítko místo výpisu všech typů (2026-08-27), na
  žádost.** Čistě GUI featura - žádná změna schématu/service/repository.
  Flat vs. procentuální poškození (`DamageMode.FLAT` /
  `PERCENT_OF_TOTAL`) přitom v projektu existovalo už dřív a bylo plně
  zapojené (`EquipmentResolver`, `ItemRenderer`, `/pve item damage set`
  konzolový příkaz) - jen se to v `ItemEditorMenu`'s DAMAGE tabu dalo
  vybrat jen napsáním "flat"/"percent" jako druhé slovo do chatového
  promptu, což bylo snadné přehlédnout. Teď je to v tom promptu
  explicitně vysvětlené (přidán řádek "flat = pevné číslo, percent = %
  z celkového poškození útoku" + příklad na obojí).
  - DAMAGE tab už nevypisuje všech ~19 registrovaných typů poškození
    najednou - zobrazí jen ty, co item už má nastavené (`wielded` a/nebo
    `worn`), a za posledním z nich zelené tlačítko "+ Přidat poškození".
  - Klik na "Add" přepne tab do režimu výběru (nové pole
    `ItemEditorHolder.damageTypePickerOpen`) - stejný tab, stejná
    inventář, jen se místo nastavených typů vypíšou ty NEnastavené;
    v PREVIEW_SLOT je "Zpět" šipka místo náhledu itemu. Klik na typ tam
    i klik na už nastavený typ v běžném zobrazení vede do stejného
    chatového promptu (`<částka> <flat|percent> <wielded|worn>`) - Add
    jen řeší VÝBĚR typu, ne flat/percent (to zůstává v chatu, jak bylo).
  - Shift+klik na už nastavený typ maže obojí (wielded i worn) stejně
    jako dřív; RESIST tab a ostatní taby beze změny (výslovně žádáno jen
    pro "damages").
  - Refaktoring: `render(...)` teď bere `ItemEditorHolder` místo
    `(templateKey, tab)` zvlášť, aby `renderDamage` měla přístup k
    picker-flagu - všech 13 volajících míst upraveno mechanicky, žádná
    změna chování mimo DAMAGE tab.
  - Žádná nová perzistence, žádná migrace, žádné nové JUnit testy (tahle
    třída nemá testy vůbec - GUI dotýkající se živého `ItemStack`/
    `Inventory` se v projektu ověřuje jen přes `runServer`, ne přes
    JUnit, stejná konvence jako u všech předchozích GUI tabů). 152 testů
    beze změny, čistý `compileJava`/`compileTestJava`/`test`/`build`.
  - **Ověřeno živě**: `runServer` boot na čerstvé DB proběhl čistě,
    plugin se povolil bez chyby (`Damage types registered: [...]`
    vypsáno správně). Konzolové ověření přes skriptovanou příkazovou
    sekvenci (`pve item create` → `pve item damage set ... flat/percent
    ...` → `pve item list`) se nepodařilo kvůli tomu, že JLine konzole
    při přijímání víc řádků najednou přes pipe zkomolí první příkaz
    (nesouvisí s touhle změnou - stejné selhání i s 15s prodlevou po
    "Done", a NPE je čistě ve vanilla `Commands.executeCommandInContext`,
    žádný `eu.purrtech` frame ve stack trace). Samotný GUI Add/picker
    flow navíc stejně jde vyzkoušet jen kliknutím připojeného hráče, což
    tenhle sandbox nikdy neměl k dispozici - **nedá se ověřit v
    sandboxu**: skutečné otevření DAMAGE tabu, klik na "+ Přidat
    poškození" a výběr typu z pickeru. Doporučuju vyzkoušet naživo
    (`/pve item edit <key>` → tab Custom Damages → Add → vyber typ →
    napiš do chatu částku/mode/context).

- **ValhallaMMO import: DAMAGE_&lt;TYPE&gt; bez EXTRA_ teď dá % místo
  ničeho (2026-08-27), na žádost.** Změna rozhodnutí z dřívějška (viz
  "Import celého itemu..." sekce výš): dřív platilo, že `DAMAGE_<TYPE>`
  (fire/magic/poison/radiant/freezing/explosion/lightning/necrotic/
  bludgeoning - procentuální "zvyš to, co item už dává za tenhle typ, o
  N %") se, když item neměl odpovídající flat `EXTRA_<TYPE>_DAMAGE`
  (nic k vynásobení), prostě zahodilo (žádný contribution, "zůstane na
  0"). Teď se v tom případě místo zahození uloží rovnou jako vlastní
  `DamageContribution` s `DamageMode.PERCENT_OF_TOTAL` (hodnota =
  ValhallaMMO zlomek × 100, stejně jako u odolností) - využívá stejný
  mód, co je teď vidět i v GUI (viz předchozí sekce).
  - Pokud flat protějšek EXISTUJE, chování beze změny (vynásobí ho
    `1+N`, uloží jako FLAT - jako dřív).
  - `ValhallaMmoImporter.fromAttributes` teď má vedle `flatByType` i
    `percentByType` mapu pro tenhle případ; obě se na konci sloučí do
    jednoho seznamu `DamageContribution` (FLAT i PERCENT_OF_TOTAL
    zároveň, podle toho, co který typ potřeboval). `ValhallaMmoBulkImporter`
    a `/pve item import` sdílí stejnou metodu, takže je to zapojené
    všude automaticky (obě už předávaly `c.mode()`/`c.context()`
    generickty, žádná další úprava).
  - Upraven test `damagePercentMultiplierWithNoMatchingFlatContributionAddsNothing`
    (přejmenován na `...BecomesPercentOfTotal`, teď ověřuje
    `PERCENT_OF_TOTAL` + hodnotu 50.0 pro `DAMAGE_FIRE:0.5` bez flat
    protějšku) + nový test na smíšený případ (jeden typ s flat
    protějškem → FLAT, druhý bez něj → PERCENT_OF_TOTAL, v jednom
    importu zároveň). 153 testů celkem (bylo 152, jeden přejmenován,
    jeden nový).
  - Čistě logická změna v `fromAttributes`/mapovacích tabulkách - žádná
    nová perzistence ani migrace (ukládá se přes existující
    `DamageContribution`/`setDamageContribution`, co to umí odjakživa).
    Čistý `compileJava`/`compileTestJava`/`test`/`build`.
  - **Nedá se ověřit v sandboxu**: reálný import z ValhallaMMO itemu
    (žádný takový plugin/item tu není) - jen unit testy na `fromAttributes`
    s ručně sestavenými attribute mapami, stejně jako celý zbytek
    importeru odjakživa.

- **"Add" tlačítko rozšířeno na RESIST a MOBS tab (2026-08-27), na
  žádost ("udělej to přidávání pro všechny ty věci co se tam
  přidává").** Stejný vzor jako u DAMAGE tabu výš (jen konfigurované +
  Add tlačítko → picker nenastavených), teď i na:
  - **RESIST tab** - beze změny chování, jen vizuálně: místo všech ~19
    typů poškození ukazuje jen ty, co item má nastavené jako
    odolnost/slabinu, + "+ Přidat odolnost/slabinu" tlačítko. Klik na
    typ v pickeru vede do stejného chatového promptu na procenta jako
    dřív klik na kterýkoli typ v plném výpisu.
  - **MOBS tab** - stejný vzor, ale klíčovaný stringem (mob type z
    MythicMobs), ne `DamageType` - tady to dává asi největší smysl,
    protože seznam typů mobů může být mnohem delší než ~19 typů
    poškození. Ukazuje jen moby, kterým je item už nasazený, + "+
    Nasadit mobovi" tlačítko; picker nabídne jen moby, kde item ještě
    nasazený není. Klik na moba v pickeru rovnou nasadí (žádný chat
    prompt netřeba, je to jen akce, ne zadávání hodnoty) a vrátí zpět
    na běžný seznam.
  - **Refaktoring**: `ItemEditorHolder`'s `damageTypePickerOpen` flag
    přejmenován na obecné `pickerOpen` - sdílí ho všechny tři taby
    (v danou chvíli je vidět jen jeden tab a `switchTab` ho stejně vždy
    resetuje, takže jeden flag stačí). Vytažené sdílené helpery
    `addButton(String label)` a `renderTypePicker(Inventory, List<DamageType>)`
    pro DAMAGE/RESIST (MOBS má vlastní verzi kvůli jinému typu klíče -
    String místo DamageType). Smazána mrtvá `damageTypeAt(...)` metoda
    (po přechodu DAMAGE i RESIST na configured/unconfigured indexování
    už ji nikdo nevolal).
  - `render(...)` teď volá `renderResist`/`renderMobs` s `holder`
    místo `templateKey` (stejná úprava jako u DAMAGE minule).
  - Žádná nová perzistence/migrace/JUnit testy (čistě GUI, stejná
    konvence jako předtím). 153 testů beze změny, čistý
    `compileJava`/`compileTestJava`/`test`/`build`.
  - **Ověřeno živě**: `runServer` boot proběhl čistě, plugin se povolil
    bez chyby. Samotné proklikání Add/picker na RESIST i MOBS tabu
    **nedá se ověřit v sandboxu** (potřeba připojený hráč, stejné
    omezení jako u DAMAGE tabu a všech ostatních GUI screenů v tomhle
    projektu). TRINKET, ARMOR_CLASS, ARMOR_PENETRATION a SPECIAL_EFFECTS
    tahle úprava záměrně nedostaly - jsou to buď pevné/malé výčty
    (3-6 položek, nemá smysl je schovávat za Add) nebo single-select
    volby, ne rostoucí seznam "věcí, co se přidávají"; řekni, pokud má
    jít i tam.

- **`/pve item replace`, reálné Minecraft atributy + trinket propojení,
  1:1 import atributů (2026-08-28), na žádost.** Tři samostatné věci v
  jedné zprávě:
  1. **`/pve item replace <key> <material>`** - psaný ekvivalent už
     existujícího `setbase`/GUI rebase (ten vyžaduje držet item v ruce);
     `material` argument teď má i tab-completion (našeptávač) přes nový
     `SuggestionProvider` - přidáno i k existujícímu `create`. Zachovává
     stávající `customModelData` šablony (mění se jen materiál).
  2. **Reálné vanilla Minecraft atributy** (`AttributeModifierEntry` -
     `Attribute`, částka, `AttributeModifier.Operation`, `slot`) - na
     rozdíl od `DamageContribution`/`TypeModifier` (naše vlastní virtuální
     combat-math, počítaná znovu při každém zásahu) se tohle napojuje
     přímo na skutečný Bukkit attribute systém, takže to funguje úplně
     stejně jako jakýkoli vanilla item s atributy a nepotřebuje žádný
     vlastní combat kód.
     - `slot` je buď název vanilla `EquipmentSlotGroup`
       (mainhand/offhand/hand/feet/legs/chest/head/armor/body/any/saddle,
       case-insensitive vstup, kanonicky lowercase) - `ItemRenderer` ho
       napeče přímo do `ItemMeta` při renderu (`meta.addAttributeModifier`),
       takže se aplikuje/odebírá automaticky, jakmile item je/není v tom
       slotu, přesně jako u jakéhokoli vanilla itemu s atributy.
     - NEBO jeden z nastavených trinket slotů tohohle serveru (viz
       `AccessorySettings`) - "propojit s trinketama", jak žádáno. Tyhle
       sloty nejsou skutečné Bukkit `EquipmentSlot`, takže vanilla
       equip/unequip detekce je nikdy neuvidí - `ItemRenderer` je
       záměrně NEpeče do `ItemMeta` (nedávalo by to smysl), ale pořád je
       ukazuje v lore. Nový **`TrinketAttributeListener`** (`trinket`
       balíček) je aplikuje/odebírá na skutečnou `AttributeInstance`
       hráče přes `addTransientModifier` (nikdy `addModifier` - schválně,
       aby se nic neukládalo do vanilla NBT a nebylo co řešit při
       reconcile) - vždycky kompletní remove-then-reapply přes
       (nastavený trinket slot × každý `Attribute`) při zavření
       accessory menu (po uložení, `EventPriority.MONITOR`) a při
       přihlášení hráče. **Neošetřeno**: úprava atributu šablony, zatímco
       item už sedí v trinket slotu online hráče, se neprojeví hned -
       až při dalším otevření/zavření accessory menu nebo relogu (na
       rozdíl od damage/resist statů, které `EquipmentResolver` počítá
       znovu při každém zásahu).
     - Nová **`item_attribute_modifier`** tabulka - existovala už od
       "Fáze 1" scaffoldingu (vedle `item_damage_contribution`/
       `item_type_modifier`), ale nic do ní nikdy nezapisovalo (feature
       nebyla implementovaná). Bezpečně přetvořena (starý `context`
       sloupec → `slot_name`, protože se mění klíč z "wielded/worn" na
       "konkrétní slot") - `DROP TABLE` + znovu-`CREATE`, ne
       `ALTER TABLE` + backfill jako u ostatních migrací v `Schema.java`,
       protože v tabulce nikdy nebyla žádná data k zachování.
     - **GUI**: nová sekce v tabu Základ (na žádost - "do záložky
       základ") pod preview/rebase tlačítkem - stejný Add/picker vzor
       jako DAMAGE/RESIST/MOBS, jen picker vždy nabízí VŠECH ~40
       atributů (na rozdíl od DAMAGE/RESIST), protože stejný atribut
       může mít víc záznamů (různé sloty).
     - Nové příkazy `/pve item attribute set|remove <key> <attribute>
       <slot> <amount> [<operation>]` s tab-completion na atribut/slot/
       operaci.
  3. **Import 1:1** - `/pve item import valhalla` teď kromě enchantů
     (už dřív) kopíruje i skutečné vanilla `ItemMeta` attribute modifiery
     drženého itemu přesně tak, jak jsou (žádný překlad přes ValhallaMMO
     mapovací tabulky) - slot group se bere přímo z
     `AttributeModifier.getSlotGroup()`.
  - `TemplateSnapshot`/`ItemTemplateSnapshotRepository` rozšířeny o
    `attributeModifiers` (nový sloupec `attribute_modifiers` přes
    `addColumnIfMissing`, stejný encode/decode vzor jako u ostatních
    seznamů). `MythicMobEquipmentListener` taky prochází novým
    repository (moby dostávají atributy stejně jako všechno ostatní).
  - **Nejde jednotkově otestovat**: `org.bukkit.attribute.Attribute`
    konstanty (na rozdíl od `DamageMode`/`ArmorClass`, což jsou NAŠE
    vlastní enumy) jsou při statické inicializaci vázané na živý Bukkit
    registry (`Attribute.<clinit>` volá `RegistryAccess.registryAccess()`)
    - i jen ODKAZ na `Attribute.ATTACK_DAMAGE` v čistém JUnitu (bez
      serveru/MockBukkit) hodí `ExceptionInInitializerError` ještě před
      tělem testu. Stejný důvod, proč `EquipmentResolver` nemá v projektu
      vůbec žádné testy. `AttributeModifierRepository`/`TrinketAttributeListener`/
      `ItemTemplateService`'s nové metody tedy nemají JUnit testy - ověřeno
      jen živě. `EquipmentSlotGroup` naproti tomu žádný registry nepotřebuje
      (ověřeno živě i testem) - `AttributeSlots.parse(...)` tedy MÁ 4 nové
      unit testy (case-insensitivitu na obě strany, neznámý slot, trinket
      slot co tenhle server nemá nastavený).
  - 157 testů celkem (153 + 4 nové), čistý
    `compileJava`/`compileTestJava`/`test`/`build`.
  - **Ověřeno živě** (`runServer`, ruční sqlite3 kontrola): boot na
    ručně sestavené DB se starým `item_attribute_modifier` schématem
    (sloupec `context`, žádný `slot_name`, žádná `attribute_modifiers`
    ve snapshotu) proběhl čistě - migrace tabulku zahodila a znovu
    vytvořila ve správném tvaru. Celá příkazová posloupnost (`create` →
    `attribute set` mainhand ATTACK_DAMAGE → `attribute set` AMULET
    LUCK → `replace` na DIAMOND_SWORD → `list` → `attribute remove`
    LUCK) proběhla bez jediné chyby, verze postupně 1→5. Přímá SQL
    kontrola potvrdila přesnou historii snapshotů na každé verzi (v1
    prázdno, v2 jen ATTACK_DAMAGE, v3 oba atributy, v4 oba atributy +
    nový materiál po `replace`, v5 zůstal jen ATTACK_DAMAGE po
    odebrání LUCK) a živou `item_attribute_modifier` tabulku přesně
    odpovídající finálnímu stavu (`ATTACK_DAMAGE|4.0|ADD_NUMBER|mainhand`).
    Cestou jsem si taky ověřil (třikrát omylem), že `/pve item create`
    potřebuje i `displayName` argument - "Unknown or incomplete command"
    hlášky ve starších pokusech byly moje vlastní neúplné testovací
    příkazy, ne bug v konzoli ani v kódu.
  - **Nedá se ověřit v sandboxu**: samotné GUI proklikání nové sekce v
    tabu Základ (Add/picker), skutečné baked-in chování vanilla atributu
    na itemu z `/pve item give` (žádný připojený hráč, na kom by se dal
    zkusit), a `TrinketAttributeListener`'s reálné grant/revoke chování
    při zavření accessory menu / přihlášení (taky potřeba připojený
    hráč). Doporučuju vyzkoušet naživo: dej si item s mainhand atributem
    do ruky a zkontroluj tooltip/`/attribute get`, dej item s trinket
    atributem do accessory slotu a zkontroluj `/attribute get` po
    zavření menu.

# PurrtechPVE — analýza a implementační plán

Paper plugin (`/Users/Zuzka/IdeaProjects/PurrtechPVE`, balíček `eu.purrtech.purrtechpve`,
zatím čistý scaffold — `build.gradle.kts` má Java 25 / `paper-api:26.2.build.+`, žádné
další závislosti). Cíl: systém **custom damage types** na itemech, hluboko propojený s
MythicMobs, s GUI editorem itemů a šablonovou DB, kde se změna šablony dá volitelně
propsat do všech itemů v oběhu. Inspirace ValhallaMMO, ale flexibilnější (damage types
definovatelné za běhu přes GUI/config, ne pevný enum) a bez závislosti na cizím
stat-frameworku pro trinkety.

## Otázky k potvrzení

Tyhle 4 rozhodnutí zásadně mění zbytek plánu, chci je mít odsouhlasené než začnu Fázi 0:

1. **DB engine:** SQLite (jako `PurrtechOrders`, žádná instalace navíc, HikariCP +
   shaded sqlite-jdbc) vs MySQL (pokud plánuješ multi-server síť sdílející šablony
   itemů napříč servery). Doporučuju SQLite pro v1, MySQL jako budoucí volitelná
   konfigurace stejně jako u Orders.
2. **MythicMobs — hard nebo soft depend:** Doporučuju **soft-depend** (`softdepend` v
   `paper-plugin.yml`) — damage types, trinkety, resistance/weakness a GUI editor
   fungují úplně samostatně i bez MythicMobs nainstalovaného; jen "mob z MythicMobs
   nosí/dává custom damage" hooky se zapnou navíc, pokud MythicMobs na serveru běží.
   Bezpečnější než hard depend, nezablokuje to použití pluginu jen na hráč-vs-hráč / hráč-vs-vanilla-mob.
3. **GUI:** Vlastní vanilla chest `Inventory` GUI (žádná závislost na tvém
   `PurrTechDisplayGUI`). Editor potřebuje běžné drag&drop chování slotů (přetažení
   itemu na "base" slot, anvil/sign text input pro čísla) — to je přesně to, co dělá
   vanilla `Inventory` click handling přirozeně. `DisplayGUI` je naproti tomu postavený
   na world-anchored display-entity tlačítkách (viz `PurrtechWheelAddon`), což je skvělé
   pro hráčská "wheel of fortune" menu, ale ne pro admin editor s přetahováním itemů do
   slotů. Souhlasíš, ať jde samostatně, bez závislosti na DisplayGUI?
4. **Trinket sloty:** Vanilla equipment sloty (`HAND`, `OFF_HAND`, `HEAD`, `CHEST`,
   `LEGS`, `FEET`) nestačí na "prsten/amulet/opasek" styl doplňků, který popisuješ.
   Navrhuju přidat **vlastní virtuální accessory sloty** (např. `RING_1`, `RING_2`,
   `AMULET`, `BELT` — počet a názvy nastavitelné v configu), s vlastním
   per-hráč GUI ("otevři si doplňky" přes příkaz/item), uloženým v DB/PDC na hráči.
   Tohle je bez závislosti na žádném externím Trinkets/Curios-style pluginu (na Paperu
   nic takového standardně není). Chceš virtuální sloty, nebo jen vanilla equipment sloty?

Níže píšu plán s doporučenými výchozími hodnotami (SQLite, soft-depend, vlastní GUI,
virtuální accessory sloty) — pokud chceš jinak, řekni a přepíšu.

## Odlišení od ValhallaMMO

- Damage types nejsou pevný enum v kódu, ale **záznamy v DB/configu editovatelné přes
  GUI** (`damage_type_definitions`) — server admin si může za běhu přidat vlastní typ
  bez update pluginu.
- Damage se nepřepisuje jedním číslem/typem, ale **rozpočítává se na více typů
  současně** (meč může dávat 60 % sečné + 40 % ohnivé) — viz "Výpočet poškození" níže.
- **Živá synchronizace šablon**: změna šablony itemu se dá volitelně propsat do všech
  kopií v oběhu (online i offline hráči), s volbou "poslat na všechny" / "jen nové kusy"
  za každou úpravu zvlášť — ne globální nastavení, admin si vybírá pokaždé.
- **Rebase základu itemu** přetažením v GUI nebo příkazem z ruky — šablona itemu
  (Material, custom model data, jméno) se dá kdykoliv vyměnit, aniž by se ztratily
  navázané damage types/atributy/trinket data.
- Trinket sloty jsou vlastní virtuální systém, ne závislost na cizím stat frameworku.

## Architektura & tech stack

- Java 25, Paper API `26.2.build.+` (`compileOnly`), `paper-plugin.yml` (`POSTWORLD`).
- `softdepend: [MythicMobs]` v `paper-plugin.yml`; `compileOnly` na MythicMobs API,
  veškerý kód co na ni sahá izolovaný v `mythicmobs/` balíčku s runtime `Bukkit.getPluginManager().isPluginEnabled("MythicMobs")`
  guardem — zbytek pluginu se na ni nesmí odkazovat přímo, ať jde vypnout bez `NoClassDefFoundError`.
- HikariCP + shaded `sqlite-jdbc` (shadow plugin), stejný vzor jako `PurrtechOrders` —
  `Database` + `Schema` třídy, migrace při startu.
- Vlastní `Inventory`-based GUI framework (menu abstrakce + click routing), žádná
  externí GUI knihovna.
- Adventure (součást Paperu) pro text/lore komponenty.

## Balíčková struktura (návrh)

```
eu.purrtech.purrtechpve
├── PurrtechPVE.java                 (onEnable/onDisable, DI wiring)
├── config/                          (ConfigLoader, WorldToggleConfig, Messages/lang)
├── db/                              (Database, Schema, migrace)
├── damage/
│   ├── DamageType.java              (registry entry: key, display, DoT flag, ...)
│   ├── DamageTypeRegistry.java
│   ├── DamagePipeline.java          (výpočet: split → resist/weakness → sum)
│   └── DotTask.java                 (bleed/poison-like tick damage)
├── item/
│   ├── ItemTemplate.java, ItemTemplateRepository.java
│   ├── ItemTemplateService.java     (create/edit/rebase/publish-to-circulation)
│   ├── ItemRenderer.java            (šablona+verze → skutečný ItemStack/lore)
│   └── ItemSyncService.java         (propagace do online inventářů + lazy-touch pro offline)
├── trinket/
│   ├── AccessorySlot.java, PlayerAccessoryInventory.java, AccessoryService.java
├── mob/
│   └── MobDamageProfileRepository.java  (resist/weakness podle MythicMobs typu moba)
├── mythicmobs/
│   └── MythicMobsBridge.java        (jen tahle třída smí importovat MythicMobs API)
├── listener/
│   ├── PlayerCombatListener.java, MobCombatListener.java, EquipmentChangeListener.java
├── gui/
│   ├── ItemEditorMenu.java + tabs (BaseTab, DamageTypesTab, AttributesTab, TrinketTab, ResistTab)
│   └── common/ (Menu, MenuButton, TextInputPrompt přes anvil/sign)
└── command/                         (/pve item …, /pve damagetype …, /pve world …)
```

## Datový model

**damage_type_definitions**
`key (PK, TEXT, např. "frozen"), display_name, icon_material, color,`
`is_dot (BOOL), dot_period_ticks, dot_tick_percent, sort_order, description`

Výchozí seed (kromě těch, co jsi jmenoval, navrhuju doplnit — klidně některé smaž):
`fyzické`: tupý (blunt), bodný (piercing), sečný (slashing)
`živlové`: ohnivé (fire), mrazivé (frozen), bleskové (lightning), kyselinové (acid)
`temné/světlé`: temné/stínové (shadow), duchovní (spirit), zářivé (radiant), svaté (holy — pokud chceš radiant a holy oddělené)
`DoT/status`: krvácení (bleed), jed (poison — vlastní, nezávislé na vanilla Poison efektu), výbušné (explosive)
`ostatní`: psychické (psychic), zvukové (sonic), gravitační (gravity), nekrotické (necrotic)

**item_templates**
`id (UUID PK), key (TEXT UNIQUE, admin slug), display_name, base_material,`
`base_item_snapshot (BLOB — serializovaný ItemStack přetaženého/nahraného base itemu),`
`custom_model_data, is_trinket (BOOL), allowed_slots (TEXT, čárkou oddělené — vanilla i virtuální),`
`version (INT, inkrementuje se při každé uložené změně), created_at, updated_at, created_by`

**item_damage_contribution** — kolik a jakého typu poškození item dává/přidává
`template_id, damage_type_key, amount, mode (FLAT | PERCENT_OF_TOTAL),`
`context (WHEN_WIELDED | WHEN_WORN)` — `WHEN_WIELDED` = počítá se při útoku touhle
zbraní; `WHEN_WORN` = pasivní bonus, když je item nasazený jako armor/trinket
(řeší zároveň zadání "trinket může přidávat i damage types").

**item_type_modifier** — odolnost/slabina vůči typu, jen relevantní pro armor/trinket
`template_id, damage_type_key, percent` (kladné = odolnost, záporné = slabina,
clampnuté na rozumný rozsah např. -200 % až 95 %, aby nešlo dostat item nesmrtelnosti)

**item_attribute_modifier** — vanilla Bukkit atributy i vlastní staty (life steal %, crit % …)
`template_id, attribute_key, amount, operation (ADD_NUMBER|ADD_PERCENT|MULTIPLY_PERCENT),`
`context (WHEN_WIELDED | WHEN_WORN)`

**mob_damage_profile** — odolnosti/slabiny podle MythicMobs typu moba (ne podle konkrétního itemu)
`mythic_mob_internal_name, damage_type_key, percent`

Instance itemů v oběhu se **needuchovávají jako řádek v DB za kus** (neškáluje se to a
stejně nejde force-updatovat item v neloadnutém chunku/offline hráči synchronně).
Místo toho každý vydaný `ItemStack` nese v `PersistentDataContainer`
`{templateKey, templateVersion}`. `ItemRenderer` z nich dopočítá lore/atributy.
Když admin uloží změnu šablony a zvolí "propsat do oběhu":
- online hráči → okamžitě přepočítat inventář/enderchest/equipment (`ItemSyncService`)
- offline hráči / itemy v chestech v neloadnutých chunkách → **lazy touch**: při
  `InventoryOpenEvent`/`PlayerJoinEvent`/`ChunkLoadEvent` se porovná uložená
  `templateVersion` s aktuální a item se přerenderuje, pokud publikace zněla "na
  všechny". Pokud admin zvolí "jen nové kusy", stará verze zůstává beze změny navždy
  (`ItemRenderer` bere verzi zaznamenanou na itemu, ne nutně nejnovější).

## Výpočet poškození (pipeline)

1. Vanilla/MythicMobs damage event nastaví "surové" číslo (po enchantech, potion
   efektech, MythicMobs skillu atd. — tohle číslo neřešíme, necháváme vanille/MM).
2. Zjistíme šablonu držené zbraně (PDC tag) → `item_damage_contribution` s
   `context=WHEN_WIELDED` řekne, jak surové číslo rozpočítat na typy (v % nebo flat
   navíc). Bez custom itemu = 100 % "fyzické" (fallback typ, aby se resist systém dal
   použít i na vanilla zbraně přes `mob_damage_profile`/armor odolnosti).
3. Posbíráme odolnosti/slabiny obránce: součet `item_type_modifier` ze všech nasazených
   armor+trinket kusů (`context` u modifieru se neřeší, odolnost platí vždy když je
   kus nasazený) + pokud obránce je MythicMobs mob, přičte se `mob_damage_profile`.
4. Na každý typový "kbelík" aplikujeme `1 - clamp(percent)/100`, kbelíky sečteme zpátky
   na finální číslo, to se zapíše do damage eventu (`event.setDamage(...)`), stejný
   princip pro DoT tiky.
5. Vystřelí se vlastní `PurrtechDamageAppliedEvent` (typ→množství breakdown) — MythicMobs
   skill podmínky / jiné pluginy si na to můžou navázat vlastní efekty.
6. Přetrvávající typy (bleed, poison, …) se implementují jako repeating task tagovaný
   damage typem, který prochází stejnou pipeline (odolnost se přepočítá znovu při
   každém ticku, kdyby si obránce mezitím sundal armor).

## MythicMobs integrace

- `MythicMobsBridge` (jediná třída importující MythicMobs API): detekce, jestli entita
  je MythicMobs mob (`MythicBukkit.inst().getMobManager().isActiveMob(entity)`),
  přečtení jejího MythicMobs typu (pro `mob_damage_profile`) a equipmentu (pokud mob
  nosí náš item přes MythicMobs `Equipment:` config, poznáme to po stejném PDC tagu).
- Player → Mythic mob: pipeline výše, `mob_damage_profile` + moba nasazený plugin-item
  (pokud má) se sečtou pro obranu.
- Mythic mob → player: pokud mob útočí MythicMobs skillem, hookneme se na
  `MythicMobs`' damage event (ne jen vanilla `EntityDamageByEntityEvent`, aby to
  fungovalo i pro skill-based damage bez fyzického zásahu) a stejně tak zjistíme, jestli
  mob "drží" náš custom item pro určení typu.
- Volitelně: `MythicCondition`/`MythicMechanic` registrace, aby MythicMobs skilly mohly
  přímo cílit/podmiňovat na "hráč má aktivní bleed" apod. (Fáze 6+, není nutné pro v1.)

## Config (`config.yml`)

```yaml
worlds:
  disabled: []          # admin doplní světy, kde je celý systém vypnutý
pvp:
  enabled: true
  disabled-worlds: []
pve:
  enabled: true
  disabled-worlds: []
accessory-slots:         # jen pokud potvrdíš virtuální trinket sloty
  - RING_1
  - RING_2
  - AMULET
  - BELT
database:
  type: sqlite           # sqlite | mysql
```
Vyhodnocení "je systém aktivní tady" = world není v `worlds.disabled` AND (útok je
PvP → `pvp.enabled` a world není v `pvp.disabled-worlds`) OR (PvE → obdobně). Pokud
vypnuté, damage pipeline se přeskočí úplně (vanilla damage number projde beze změny).

## GUI editor itemů

`/pve item edit <key>` otevře chest menu:
- **Náhledový slot** vlevo nahoře = aktuální renderovaný item. Přetažení jiného itemu
  sem = rebase (base_material/custom_model_data/jméno se převezme z taženého itemu,
  damage/attribute/resist data šablony zůstávají). Stejná akce příkazem
  `/pve item setbase <key>` — vezme item z hráčovy ruky.
- **Tab lišta** (spodní řádek, named items): Základ | Custom Damages | Attributes |
  Trinket | Odolnosti/Slabiny | Uložit&Publikovat
- **Custom Damages tab**: mřížka ikon = všechny `damage_type_definitions`. Klik = anvil
  text prompt na `amount` + toggle `FLAT/PERCENT` + toggle `WHEN_WIELDED/WHEN_WORN`.
  Shift-klik = smazat řádek.
- **Attributes tab**: stejný vzor nad vanilla `Attribute` enum + custom staty (life
  steal %, crit chance % — vlastní seznam v `AttributeRegistry`, rozšiřitelný).
- **Trinket tab**: toggle "je trinket", multi-select `allowed_slots` (vanilla i
  virtuální), zbytek dat se bere ze stejných Damage/Attributes tabů s
  `context=WHEN_WORN`.
- **Odolnosti/Slabiny tab**: mřížka damage typů, klik = anvil prompt na `percent`
  (záporné = slabina). Zobrazeno vždy, i na ne-armor itemech (validace/varování při
  publikaci, pokud item není armor/trinket a má nastavené resisty — asi k ničemu).
- **Uložit & Publikovat**: potvrzovací pod-menu — `[Jen nové kusy]` / `[Všechny v
  oběhu]` / `[Zrušit]`, přesně jak jsi popsal (volba za každou úpravu zvlášť).

## Příkazy (návrh)

`/pve item create <key>`, `/pve item edit <key>`, `/pve item delete <key>`,
`/pve item give <player> <key>`, `/pve item setbase <key>` (z ruky), `/pve item list`,
`/pve damagetype create|edit|delete|list`, `/pve mobprofile edit <mythicMobType>`,
`/pve world disable|enable <world>`, `/pve toggle pvp|pve on|off [world]`, `/pve reload`.

Permission `purrtechpve.admin` na vše výše, `purrtechpve.accessory.use` pro hráče na
otevření vlastního accessory inventáře (pokud potvrdíš virtuální sloty).

## Fázový plán

- **Fáze 0** — scaffolding: `build.gradle.kts` (shadow, MythicMobs API compileOnly,
  HikariCP+sqlite-jdbc shaded, jako `PurrtechOrders`), `paper-plugin.yml`
  (`softdepend: MythicMobs`), balíčková struktura výše, `Database`+`Schema`
  (všechny tabulky nad), `config.yml`+lang, ověřit `runServer` boot bez výjimky.
- **Fáze 1** — Damage type registry + `DamagePipeline` (bez GUI/DB CRUD, natvrdo pár
  testovacích typů) + world/pvp/pve toggle vyhodnocení + listener na vanilla
  `EntityDamageByEntityEvent` mezi hráči a vanilla moby. JUnit na `DamagePipeline`
  (split → resist → sum), žádné mockování Bukkitu kde to jde obejít čistou logikou.
- **Fáze 2** — `ItemTemplate` CRUD přes repository + `ItemRenderer` (šablona→lore/PDC)
  + `/pve item give` příkazem (bez GUI). Ověřit, že vydaný item nese správný PDC tag a
  lore odpovídá datům v DB.
- **Fáze 3** — `ItemSyncService` (propagace do online inventářů + lazy-touch listenery
  pro offline/chunk-load cestu) + verzování šablon.
- **Fáze 4** — MythicMobs bridge (soft-depend guard, detekce mobů, `mob_damage_profile`,
  čtení equipmentu moba) + obousměrná pipeline player↔mob.
- **Fáze 5** — Trinket/virtuální accessory sloty (pokud potvrzeno) + `WHEN_WORN`
  aplikace atributů/damage bonusů.
- **Fáze 6** — GUI editor (všechny taby výše) včetně rebase drag&drop a
  publish-to-circulation potvrzovacího menu.
- **Fáze 7** — polish: permissions, `/pve reload`, lang soubory, MythicMobs
  condition/mechanic hooky (volitelné), end-to-end test na živém `runServer`.

Řekni k otázkám nahoře a jdu na Fázi 0.
