# Poškození, brnění a odolnosti

Tahle stránka vysvětluje, jak server počítá, kolik zranění dostaneš a kolik uděláš ty. Není potřeba umět matematiku. Stačí si projít **Rychlý přehled** a **Příklad**.

---

## Rychlý přehled

Každý zásah se počítá ve stejném pořadí:

1. **Zbraň (nebo útok moba) určí, kolik poškození a jakého druhu se pošle.** Například 10 sečného a 4 ohnivého.
2. **Cíl má odolnosti nebo slabosti na jednotlivé druhy.** Každý druh se upraví zvlášť.
3. **Brnění ubere z fyzického poškození.** Z ohně, jedu, magie a podobných druhů neubírá nic. Zbraň s **pronikáním brnění** ale část brnění cíle na ten zásah ignoruje.
4. **Kritický zásah** (pokud padne) všechno vynásobí.
5. **Vanilla Minecraft** na konci přidá svoje vlastní úpravy (brnění z atributů, efekty).

> **Hlavní pravidlo:** odolnost se počítá po druzích. Odolnost na oheň ti nepomůže proti meči a brnění nepomůže proti ohni.

---

## Druhy poškození

Každý zásah se skládá z jednoho nebo více druhů poškození. V liště nad hotbarem (action bar) uvidíš u každého číslo a symbol.

| Symbol | Druh | Poznámka |
|---|---|---|
| ⚒ | Tupé (blunt) | fyzické |
| † | Bodné (piercing) | fyzické |
| ‡ | Sečné (slashing) | fyzické |
| ⚔ | Fyzické (physical) | zkratka: dává všechny tři fyzické druhy najednou |
| ♨ | Ohnivé (fire) | |
| ❄ | Mrazivé (frozen) | |
| ⚡ | Bleskové (lightning) | |
| ☣ | Kyselinové (acid) | |
| ☾ | Temné (shadow) | |
| ☯ | Duchovní (spirit) | |
| ☀ | Zářivé (radiant) | |
| ✝ | Svaté (holy) | |
| ✦ | Magické (magic) | |
| ☄ | Výbušné (explosive) | |
| Ψ | Psychické (psychic) | |
| ♪ | Zvukové (sonic) | |
| ↓ | Gravitační (gravity) | |
| ⚰ | Nekrotické (necrotic) | |
| ⚠ | Kousnutí (bite) | základní útok moba bez zbraně |
| ⚕ | Krvácení (bleed) | poškození v čase, viz níže |
| ☠ | Jed (poison) | poškození v čase |

**Fyzické** druhy jsou tupé, bodné a sečné (a zkratka „fyzické“). Jen na ně platí **brnění** (viz dál). Všechno ostatní brnění obchází a brání se tomu jen odolnostmi.

---

## Odkud se bere poškození

### 1. Základ zbraně

Co držíš v ruce, určuje druh základního zásahu:

| Držíš | Základní druh |
|---|---|
| Meč nebo sekera | sečné |
| Luk, kuše, trojzubec | bodné |
| Cokoliv jiného (hůl, nástroj, pěst, blok) | tupé |
| Moby bez zbraně v ruce | kousnutí |

### 2. Přidané poškození na itemu

Předmět může přidat další druhy poškození. Dvě věci je dobré znát:

- **Plochá hodnota** (např. „+5 ohnivé“) přidá vždy stejné číslo.
- **Procento** (např. „+20 % tupého“) přidá podíl ze základního zásahu, takže roste spolu s ním.
- **Držené vs. nošené.** Poškození označené jako *při držení* se přičte, jen když předmět držíš v ruce. Poškození označené jako *při nošení* se přičítá vždy, když předmět nosíš (brnění, prsteny, amulety).

Penalizace (záporné číslo) ubírá **jen svůj druh**. „−80 % sečného“ sníží sečné poškození, ale nesníží tvoje ohnivé.

### 3. Bonusy ze setů

Když nosíš dost kusů z jedné sady, přidají se bonusy (poškození i odolnosti). Prahy se sčítají: s pěti kusy máš bonusy za 2, 3 i 5 kusů, ne jen ten nejvyšší.

---

## Odolnosti a slabosti

Odolnost je **procento**, o které se sníží poškození daného druhu.

- **Kladné číslo = odolnost.** 25 % ohnivé odolnosti znamená, že oheň ubližuje o čtvrtinu méně.
- **Záporné číslo = slabost.** −50 % ohnivé odolnosti znamená, že oheň ubližuje o polovinu víc.

### Odkud se berou

Všechno se **sčítá**:

- odolnosti napsané přímo na tvých kusech vybavení (včetně prstenů, amuletů, opasku),
- bonus **třídy brnění** (lehké, střední, těžké), viz další sekce,
- bonusy ze setů,
- u mobů jejich vlastní profil odolností.

### Limity

Žádná kombinace vybavení nemůže být nekonečná ani nekonečně slabá:

- největší odolnost je **95 %**,
- největší slabost je **−200 %** (poškození se ztrojnásobí).

Admin tyhle hranice může na serveru změnit.

### Barvy v liště

Číslo u každého druhu poškození v liště má barvu podle účinnosti:

| Barva | Význam |
|---|---|
| žlutá | cíl je na tenhle druh **slabý** (děláš víc, než je napsáno) |
| bílá | normální |
| šedá | cíl je **odolný** (děláš méně, než je napsáno) |

---

## Brnění

Brnění je **druhá, samostatná vrstva** vedle odolností. Funguje jinak:

- Každý kus vybavení může mít **body brnění**. Body ze všech nošených kusů se sečtou.
- **Každý bod ubírá 4 % fyzického poškození.**
- Maximum je **20 bodů = 80 %**. Víc už nepomůže.
- Brnění **neplatí na nefyzické druhy** (oheň, mráz, blesk, magie, jed…). Ty řeší jen odolnosti.

| Body brnění | Sníží fyzické poškození o |
|---|---|
| 5 | 20 % |
| 10 | 40 % |
| 14 | 56 % |
| 20 a víc | 80 % |

> **Pozor:** hodnoty v tabulce platí, jen když útočník nemá **pronikání brnění**. Zbraň s pronikáním z tvých bodů brnění pro ten zásah část odečte (viz sekce *Pronikání brnění* níže). 10 bodů brnění proti zbrani s 50% pronikáním funguje jako 5 bodů.

### Třídy brnění: lehké, střední, těžké

Každý kus vybavení patří do jedné ze tří tříd. Třída s sebou nese **společné odolnosti nebo slabosti**, které si nastavuje admin pro celý server. Příklad: těžké brnění může být odolné proti sečnému a slabé proti blesku.

- Bonus třídy se počítá **jednou za třídu**. Nosit čtyři kusy těžkého brnění dá stejný bonus třídy jako nosit jeden, nenásobí se čtyřikrát.
- Bonus třídy se na itemu v lore **neukazuje**. Zeptej se admina nebo se podívej na wiki serveru, co která třída dává.
- Odolnosti napsané přímo na kusu se naopak sčítají normálně.

---

## Pronikání brnění

Zbraň může mít **pronikání brnění**. Když s ní udeříš, na ten jeden zásah se sníží:

- bonus třídy brnění, která na cíli je,
- body brnění cíle.

Nesníží se odolnosti napsané přímo na kusech vybavení cíle. Pronikání se nikdy trvale nedotkne ničího vybavení, týká se jen toho jednoho zásahu.

Pronikání je dvojího druhu:

- **Plochá hodnota:** odečte pevný počet. „10 pronikání“ odečte 10 bodů procent nebo brnění bez ohledu na to, kolik jich cíl má.
- **Procento:** odečte podíl ze zbývající hodnoty. „50 % pronikání“ rozpůlí odolnost nebo brnění cíle.

Pronikání může odolnost i **dostat pod nulu** (cíl se pak stane slabým na ten druh). Na serveru může být nastaveno, jak moc pronikání proti jedné třídě platí i proti ostatním.

---

## Kritické zásahy

Dva druhy kritů se dají kombinovat:

### Kritický zásah z vybavení

- **Šance** se sčítá ze všech tvých kusů vybavení (zbraň, brnění, prsteny…).
- **Bonus** (např. +50 %) se taky sčítá. Při kritu se výsledné poškození vynásobí `1 + bonus / 100`.
- Šance i bonus musí být větší než nula, jinak kritický zásah nikdy nepadne.
- Cíl se může kritům bránit **odolností proti kritům**. Ta šanci zmenšuje (kladné číslo) nebo zvětšuje (záporné).

### Kritický zásah z vanilla Minecraftu

Když jako hráč zaútočíš ze skoku, vanilla ti dá krit. Server ho zachová a přidá k výpočtu jako ×1,5 na hotové poškození.

Kritické zásahy se počítají **po** odolnostech a brnění, na hotové číslo.

---

## Speciální efekty

### Krvácení

- Při zásahu je **šance**, že cíl začne krvácet.
- Krvácení je **poškození v čase**: celková hodnota se rozdělí rovnoměrně po sekundách po dobu trvání.
- Proti krvácení pomáhá odolnost na druh „krvácení“. Počítá se **při každém ticku zvlášť**.
- Krvácení není běžné poškození itemu, nastavuje se zvlášť (šance, délka, poškození).

### Omráčení

- Při zásahu je šance, že cíl omráčíš. Cíl může mít **odolnost proti omráčení**, která šanci zmenšuje.
- Omráčený cíl **nemůže útočit**, je **zpomalený** a **slepý** po dobu omráčení.
- Když je cíl omráčený a dostane další omráčení, platí delší z obou časů. Omráčení se nikdy nezkrátí.

### Odražení

- Odražení se řeší na straně **toho, kdo je zasažen**. Funguje, ať nosíš předmět v ruce, nebo na sobě.
- Každý kus s odražením má šanci, že část zásahu (třeba 20 %) vrátí útočníkovi.
- **Trvalé odražení** vrací podíl ze **každého** zásahu, bez házení kostkou.
- Všechno odražené se sečte a útočník to dostane zpátky jako jedno poškození.

---

## Příklad zásahu krok za krokem

**Útočník** má meč: 10 sečného + 4 ohnivého. Má 25% šanci na kritický zásah za +50 %.

**Cíl** má:

- 20 % odolnosti proti sečnému,
- −50 % odolnosti proti ohni (slabost),
- 10 bodů brnění.

| Krok | Sečné | Ohnivé |
|---|---|---|
| Co zbraň posílá | 10 | 4 |
| Odolnost cíle | 10 × 0,8 = **8** | 4 × 1,5 = **6** |
| Brnění (10 bodů = −40 % fyzického) | 8 × 0,6 = **4,8** | 6 (oheň není fyzický) |

**Celkem: 4,8 + 6 = 10,8**

Pokud padne kritický zásah (25 %): 10,8 × 1,5 = **16,2**.

Všimni si, že oheň prošel plnou silou i přes brnění, a že slabost na oheň ho ještě zvýšila.

### Stejný zásah se zbraní s pronikáním brnění

Dejme tomu, že meč má **50% pronikání brnění**. Cíl má pořád 10 bodů brnění, ale pro tenhle zásah se z nich odečte polovina, takže platí jen **5 bodů = −20 % fyzického**.

| Krok | Sečné | Ohnivé |
|---|---|---|
| Co zbraň posílá | 10 | 4 |
| Odolnost cíle | 10 × 0,8 = **8** | 4 × 1,5 = **6** |
| Brnění (5 bodů po pronikání = −20 %) | 8 × 0,8 = **6,4** | 6 (oheň není fyzický) |

**Celkem: 6,4 + 6 = 12,4** (místo 10,8).

Pronikání zvedlo sečnou část z 4,8 na 6,4. Na oheň nemá žádný vliv, protože ten brnění obchází tak jako tak.

---

## Co se děje navíc (vanilla Minecraft)

Po výpočtu výše přidává ještě samotná hra svoje věci:

- **Vanilla brnění** z atributů kusů (zobrazuje se v hotbaru jako brnění) pořád ubírá. Počítá se **po** našem výpočtu, takže hodně vanilla brnění může zásahy dál zmenšovat.
- **Efekt Odolnost** (potion) a **Absorpce** fungují jako vždy.
- **Zaklínadla ochrany** (Protection a podobná) se pro zásahy z tohoto systému **ignorují**. O tom, kolik poškození projde, rozhodují odolnosti a brnění tohoto systému, ne zaklínadla.
- **Nezranitelnost po zásahu:** krátce po zásahu je cíl nezranitelný (zhruba půl vteřiny). Zásahy v té době se zahazují, pokud nejsou silnější než ten předchozí. Nejde o chybu.

---

## Příslušenství (prsteny, amulet, opasek)

Server má navíc speciální sloty: **prsten 1, prsten 2, amulet a opasek**. Otevřeš je příkazem `/pve accessory`.

- Itemy v těchto slotech se počítají do všeho stejně jako nošené brnění: odolnosti, poškození při nošení, kritické zásahy, odražení i bonusy ze setů.
- Některé itemy se dají nosit jen v konkrétním slotu. Když ho vložíš jinam, nebude fungovat.

---

## Moby

- **Každý typ moba může mít vlastní profil odolností.** Například kostlivec může být slabý na tupé a odolný na bodné. Profil se přičítá k odolnostem z jeho vybavení.
- **Moby se zbraní** (nebo brněním) počítají vybavení stejně jako hráč.
- **Speciální útoky bossů** (skilly) mají vlastní nastavení poškození podle druhu, které admin nastavuje pro konkrétního moba a konkrétní útok. Projdou stejně přes tvoje odolnosti a brnění.
- Jedovaté, ohnivé a mrazivé **vanilla efekty** respektují tvoje odolnosti na jed, oheň a mráz. Odolnost na oheň zkrátí hoření, odolnost na jed zeslabí jed a podobně.

---

## Časté otázky

**Proč jsem dostal míň, než je napsáno na útoku?**
Pravděpodobně máš odolnost na ten druh, brnění (pokud je poškození fyzické) nebo vanilla brnění z kusů vybavení.

**Proč mi brnění pomáhá míň, než jsem čekal?**
Útočník může mít zbraň s **pronikáním brnění**. Ta na jeden zásah sníží tvoje body brnění i bonus třídy brnění. Na tvém vybavení se přitom nic nezmění, jen ten jeden zásah se počítá, jako bys měl míň brnění.

**Proč mi brnění nepomáhá proti ohni?**
Brnění ubírá jen fyzické poškození (tupé, bodné, sečné). Proti ohni, ledu, blesku a podobným druhům potřebuješ odolnosti.

**Dá se mít odolnost vyšší než 95 %?**
Ne. 95 % je strop (pokud ho admin nezměnil).

**Co když mám záporné číslo odolnosti?**
Je to slabost. Cíl dostane víc poškození, než je napsáno.

**Proč nepomáhají zaklínadla ochrany?**
Systém má vlastní odolnosti a brnění, které jsou pro tenhle server důležité. Zaklínadla ochrany se u zásahů z tohoto systému nepočítají, aby odolnosti určoval jen výběr vybavení.

**Vidím své poškození za vteřinu?**
Ano: `/pve dps` zapne a vypne zobrazení DPS v liště.

**Proč mi někdy ubírá téměř nic?**
Kombinace odolností, brnění a vanilla brnění se násobí. Každá vrstva ubere svůj díl z toho, co zbyde po té předchozí.

---

## Pro adminy

- `/pve debug` (jen pro oprávněné) vypíše u každého zásahu rozpis: druhy poškození, odolnosti cíle, brnění, vanilla úpravy a kolik skutečně ubylo.
- `/pve armorclass` upravuje bonusy tříd brnění.
- `/pve mobs` nastavuje vybavení mobů a poškození jejich útoků (skillů). Profil odolností moba se nastavuje příkazem `/pve mobprofile`.
- Limity odolností jsou v `config.yml` pod `combat.resistance-percent-min` a `-max`.
- Pronikání brnění proti jiné třídě se nastavuje v `config.yml` pod `armor-penetration-conversion`.
