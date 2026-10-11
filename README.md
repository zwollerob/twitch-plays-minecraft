# Twitch Plays Minecraft

Een Fabric-mod voor Minecraft 26.3 waarmee je Twitch-chat je speler laat besturen: lopen, kijken, hakken, bouwen, eten en je inventory. Werkt in singleplayer.

- **Chat** typt commando's zoals `!forward 3` of `!lookleft 90`.
- **Anarchie**: elk commando wordt meteen uitgevoerd. **Democratie**: elke 5 seconden wint het commando met de meeste stemmen.
- **Bits**: een cheer met een commando (`Cheer300 !unequip`) gaat direct, zonder stemronde en zonder cooldown.
- **Kanaalpunten**: koppel een beloning aan een commando.
- Linksboven staat een **overlay** die kijkers laten zien wat er gebeurt.
- **Meldingen**: bij een sub, gift sub, follower, donatie, raid of cheer verschijnt een grote titel in beeld, met geluid en vuurwerk.

## Installatie

Je hebt nodig: Minecraft Java Edition **26.3** op Windows, de officiële Minecraft Launcher, en deze drie onderdelen:

1. **Fabric Loader**: download de installer van https://fabricmc.net/use/installer/, kies *Client*, Minecraft-versie **26.3** en Loader **0.19.5** (of nieuwer), en klik *Install*. In de launcher verschijnt een profiel "fabric-loader-26.3".
2. **Fabric API** voor 26.3 (bijv. `fabric-api-0.162.0+26.3.jar`), van https://modrinth.com/mod/fabric-api.
3. **Twitch Plays**: de jar (bijv. `twitchplays-1.1.1.jar`) uit de nieuwste release op https://github.com/zwollerob/twitch-plays-minecraft/releases. Bij een update vervang je de oude jar; laat er maar één in de map `mods` staan.

Zet de jars van stap 2 en 3 in de map `mods` van je Minecraft-map (standaard `%APPDATA%\.minecraft\mods`) en start het Fabric-profiel.

Tip: gebruik je ook mods voor andere Minecraft-versies, geef het Fabric-profiel dan een eigen speelmap. Dat doe je in de launcher via *Installaties → profiel bewerken → Speelmap*, bijvoorbeeld `%APPDATA%\.minecraft-twitchplays`. Zet de mods dan in de map `mods` daarbinnen. Zo raken je andere profielen elkaars mods niet. Een bestaande wereld kopieer je naar de map `saves` in die speelmap.

## Eerste keer

1. Start het profiel en open een wereld.
2. Typ in Minecraft: `/twitchplays channel jouwkanaalnaam`
3. Linksboven verschijnt "Twitch Plays ANARCHIE". Chat kan nu meedoen.

Chat lezen en bits werken **zonder in te loggen**. Alleen voor kanaalpunten moet je inloggen (zie hieronder).

| Sneltoets | Wat |
|---|---|
| **F8** | Twitch-besturing aan/uit (noodrem) |
| **F9** | Wissel tussen anarchie en democratie |

Je kunt deze toetsen veranderen bij Opties → Besturing → Twitch Plays.

## Commando's voor chat

Het getal is optioneel. Zonder getal geldt de standaardwaarde.

| Commando | Ook | Wat | Getal |
|---|---|---|---|
| `!forward` | `!w` `!vooruit` | vooruit lopen | seconden (1, max 5) |
| `!back` | `!s` `!achteruit` | achteruit | seconden |
| `!left` / `!right` | `!a` `!d` `!links` `!rechts` | opzij stappen | seconden |
| `!jump` | `!j` `!spring` | springen | seconden ingedrukt (0.3) |
| `!sneak` | `!sluip` | sluipen | seconden (2) |
| `!sprint` | `!ren` | sprinten (vooruit) | seconden (2) |
| `!lookleft` / `!lookright` | `!ll` `!lr` `!kijk links` | draaien | graden (45, max 180) |
| `!lookup` / `!lookdown` | `!lu` `!ld` `!kijk omhoog` | omhoog/omlaag kijken | graden (30, max 90) |
| `!turn` | `!draai` | 180° omdraaien | |
| `!noord` / `!oost` / `!zuid` / `!west` | `!north` `!east` `!south` `!kijk noord` | precies die windrichting op kijken, recht vooruit | |
| `!stap` | `!step` `!walk` | precies zoveel blokken vooruit lopen, midden op het blok stoppen | aantal blokken (1, max 20) |
| `!jumpforward` | `!jf` `!hop` `!spring vooruit` | springend naar voren, bijv. een blok op | aantal sprongen (1, max 5) |
| `!sprintjump` | `!sj` | sprintend springen, over een gat van 2–3 blokken | aantal sprongen (1, max 5) |
| `!pillar` | `!toren` `!bouwop` | jezelf omhoog bouwen: springen en een blok onder je zetten | aantal blokken (1, max 5) |
| `!bridge` | `!brug` | een brug recht vooruit bouwen en eroverheen sluipen | aantal blokken (3, max 10) |
| `!attack` | `!hit` `!sla` | klikken/slaan | aantal (1, max 10) |
| `!mine` | `!dig` `!hak` | linkermuis vasthouden (blok hakken) | seconden (2, max 10) |
| `!use` | `!place` `!plaats` | rechtsklik (blok plaatsen, deur openen) | aantal |
| `!holduse` | `!hold` | rechtermuis vasthouden (boog, schild) | seconden (1) |
| `!eat` | `!eet` | eten uit de hotbar pakken en opeten | |
| `!1` … `!9` | `!slot 3` | hotbar-slot kiezen | |
| `!next` / `!prev` | `!volgende` `!vorige` | volgend/vorig slot | |
| `!pak <item>` | `!grab` `!take` `!neem` | item uit je hele inventory in je hand nemen, bijv. `!pak diamond pickaxe` of `!pak brood` | itemnaam |
| `!inv` | `!inventory` | inventory 3 seconden openen, zodat chat ziet wat je hebt | |
| `!drop` | `!q` `!gooi` | item weggooien | aantal |
| `!swap` | `!offhand` `!wissel` | wisselen met de andere hand | |
| `!dropstack` | `!gooialles` | hele stack weggooien | **betaald** |
| `!shuffle` | `!hussel` | inventory door elkaar | **betaald** |
| `!unequip` | `!armor` `!uitkleden` | armor uittrekken | **betaald** |
| `!stop` | | alles loslaten | |

Moderators en jijzelf kunnen in chat `!anarchie` of `!democratie` typen, en een overname beëindigen met `!takeover stop`.

### Windrichting en precies lopen (bijv. bomen hakken)

In de overlay staat altijd welke kant je op kijkt ("Kijkt naar: oost"), zodat chat weet waar noord is.

- **`!noord`**, **`!oost`**, **`!zuid`**, **`!west`** (ook `!kijk noord` of `!north`): je kijkt precies die kant op, recht vooruit, dus niet omhoog of omlaag. Dan staat je richtkruis op ooghoogte, recht voor je.
- **`!stap 3`**: loopt precies 3 blokken vooruit en stopt midden op het blok. De richting is noord, oost, zuid of west, net als bij `!bridge`. Staat er iets in de weg, zoals een boomstam of een muur, dan stop je ervoor.

Een boom omhakken gaat bijvoorbeeld zo:

1. `!oost`: kijk naar de boom.
2. `!stap 10`: loop ernaartoe. Je stopt vanzelf vóór de stam.
3. `!mine 5`: hak het blok op ooghoogte weg. Met de hand duurt een blok hout 3 seconden; met een bijl gaat het sneller, die pak je eerst met `!pak bijl`.
4. `!kijk omlaag 60` en dan `!mine 5` voor het onderste blok, of `!lookup 60` en `!mine 5` voor het blok erboven. Daarna kijk je met `!oost` weer recht vooruit.

`!w`, `!s` en dergelijke blijven korte versies van lopen, daarom zijn er geen afkortingen als `!n` of `!z`.

### Springen en omhoog bouwen

Chat stuurt één commando tegelijk. Daarom zijn er combinaties waarbij de mod de timing regelt:

- **`!jumpforward`**: lopen en springen tegelijk. Gebruik het als het blok **direct voor je** staat. Staat het verder weg, dan land je na één sprong nog vóór het blok; gebruik dan `!jumpforward 2` (twee sprongen achter elkaar).
- **`!sprintjump`**: hetzelfde, maar sprintend. Daarmee spring je over een gat van 2–3 blokken. Neem een aanloop met `!sprint` als het gat groter is.
- **`!pillar 3`**: bouwt je 3 blokken omhoog, bijvoorbeeld als je in een kuil bent gevallen. De mod kijkt recht naar beneden en zet bij elke sprong een blok onder je voeten. Daarna kijk je weer zoals ervoor.
  - Heb je geen blok in je hand, dan pakt de mod zelf een vol blok (zoals dirt of cobblestone) uit je hotbar of inventory. Fakkels en halve platen slaat hij over.
  - Heb je geen blokken, dan staat er "geen blokken" in de overlay.

- **`!bridge 5`**: bouwt een brug van 5 blokken recht vooruit, bijvoorbeeld vanaf de top van een pilaar of over een ravijn.
  - De richting is noord, oost, zuid of west: de richting waar je het meest naartoe kijkt. Draai eerst met `!lookleft` / `!lookright` als dat nodig is.
  - Je sluipt de hele tijd, dus je valt niet van de rand. Ook niet als de blokken onderweg op raken: dan blijf je gewoon aan het eind staan.
  - Staat er al een blok waar de brug moet komen, dan loop je daar gewoon overheen.
  - Net als bij `!pillar` pakt de mod zelf blokken. Na afloop kijk je weer zoals ervoor.

Deze commando's werken ook in democratie: `!pillar 3` is gewoon één stem.

### Items uit je hele inventory: `!pak`

Met `!1`–`!9` kiest chat alleen uit de hotbar. Met `!pak <item>` kan chat alles uit je inventory pakken (het scherm dat je met E opent):

- Zit het item al in de hotbar, dan selecteert de mod dat slot.
- Zit het in je inventory, dan wisselt het met het item dat je nu vasthoudt. Dat gaat net zoals wanneer je met de muis boven een item 1–9 drukt.

Chat mag de Engelse naam typen (`!pak diamond pickaxe`, `!pak bread`) of de naam die jij in het spel ziet (`!pak diamanten houweel`, `!pak brood`). Een deel van de naam is ook genoeg: `!pak houweel` pakt het beste houweel dat gevonden wordt. Hoofdletters doen er niet toe. Heb je het item niet, dan staat er "niet gevonden" in de overlay.

Met `!inv` gaat je inventory 3 seconden open, zodat kijkers kunnen zien wat je bij je hebt. Daarna sluit het vanzelf. Commando's die chat in die 3 seconden stuurt, worden overgeslagen; in de overlay staat dan "menu open".

### Handleidingen voor Discord

Er zijn twee kant-en-klare afbeeldingen die je in Discord kunt zetten:

- [Kijkershandleiding](docs/kijkers-handleiding.png): alle commando's voor kijkers, bits, kanaalpunten en de overname.
- [Mod-handleiding](docs/mod-handleiding.png): de commando's voor moderators, en wat ze doen bij trollen of chaos.

De prijzen en beloningen op de kijkershandleiding zijn de standaardwaarden. Pas je die aan in de config, bewerk dan `docs/kijkers-handleiding.html` en maak er een nieuwe afbeelding van.

### Tekst voor je Twitch-panel

> **Bestuur mijn Minecraft!** Typ in chat: `!forward 3`, `!back`, `!left`, `!right`, `!jump`, `!jumpforward`, `!sprintjump`, `!pillar 3`, `!bridge 5`, `!noord`/`!oost`/`!zuid`/`!west`, `!stap 3`, `!lookleft 90`, `!lookup`, `!mine 3`, `!attack`, `!use`, `!eat`, `!1`–`!9`, `!pak <item>` (bijv. `!pak houweel`), `!inv`, `!drop`, `!swap`. In democratie-modus stem je: elke 5 seconden wint het populairste commando. Cheer bits met een commando (bijv. `Cheer300 !unequip`) en het gebeurt meteen!

## Bits

Een cheer met een commando erin wordt meteen uitgevoerd, ook in democratie en zonder cooldown. Hoeveel bits een commando kost, staat in `bitsCost` in de config. Standaard is dat:

- `dropstack`: 100
- `unequip`: 300
- `shuffle`: 500
- alle andere commando's: 1 bit (`bitsDefaultCost`)

Is de cheer te laag, dan telt het commando als gewoon chatbericht. Betaalde commando's worden dan genegeerd.

## Overname: 1000 bits = jij bestuurt alleen

Een kijker die in **één cheer** minstens 1000 bits geeft, krijgt een tijdje alleen de controle.

- **Tijd:** 1 minuut per 100 bits, dus 1000 bits = 10 minuten, 2000 = 20 minuten. Een overname duurt maximaal 30 minuten.
- **Alleen die kijker:** zolang de overname loopt, voert de mod alleen de commando's van die kijker uit. Zonder cooldown, zonder stemronde, en ook de betaalde commando's (`!shuffle`, `!unequip`, `!dropstack`) zijn gratis.
- **Anderen:** cheers met een commando en kanaalpunt-beloningen van andere kijkers werken gewoon. Losse chatcommando's van anderen tellen even niet.
- **Wachtrij:** cheert iemand anders 1000+ bits terwijl er al een overname loopt, dan komt die kijker in de wachtrij en is daarna aan de beurt.
- **Verlengen:** cheert dezelfde kijker nog eens, dan komt de tijd erbij, tot maximaal 30 minuten.
- **Eerlijke klok:** de tijd loopt alleen als er gespeeld wordt. Staat de besturing uit (F8), staat er een menu open of is het spel gepauzeerd, dan staat de klok stil.
- **Na een herstart:** crasht Minecraft of herstart je het, dan loopt de overname daarna verder met de resterende tijd.
- **Ingrijpen:** moderators en jij typen `!takeover stop` (of `!overname stop`) om de huidige overname te beëindigen; de volgende in de wachtrij is dan aan de beurt. Krijgt de kijker een timeout of ban, dan stopt zijn overname vanzelf. F8 blijft de noodrem.
- **Te zien:** bij de start verschijnt "OVERNAME!" groot in beeld met vuurwerk. In de overlay staan de naam, een aftelklok en de wachtrij.

Met de Twitch-login (zie hieronder) komen er twee dingen bij:

- **Power-ups tellen mee.** Bits voor Power-ups (zoals een reuze-emote of een berichteffect) komen niet als cheer in de chat. Met de login ziet de mod ze toch, en telt een Power-up van 1000+ bits ook als overname. **Combos tellen niet mee**: Twitch geeft die niet door aan de mod.
- **Bevestiging in je Twitch-chat.** De mod zet vanaf jouw account een bericht in de chat, bijvoorbeeld "👑 @Naam heeft nu 10 minuten alleen de controle over Minecraft!". Dat gebeurt ook bij een plek in de wachtrij, bij extra tijd en aan het eind.

Uitproberen zonder bits: `/twitchplays takeover test` (1000 bits) of `/twitchplays takeover test 2500`. Stoppen: `/twitchplays takeover stop`.

## Kanaalpunten instellen (eenmalig)

Hiervoor moet je Affiliate of Partner zijn. Twitch vraagt dat je een eigen "app" registreert. Dat is gratis en kost 2 minuten.

1. Ga naar https://dev.twitch.tv/console/apps en klik **Register Your Application**. Twitch vereist dat je tweestapsverificatie aan hebt staan.
2. Vul in:
   - **Name**: iets unieks, bijv. `TwitchPlays-jouwnaam`
   - **OAuth Redirect URLs**: `http://localhost`
   - **Category**: Game Integration
   - **Client Type**: **Public** (belangrijk!)
3. Klik **Create**, open de app en kopieer de **Client ID**.
4. Typ in Minecraft: `/twitchplays clientid <jouw client id>`
5. Typ `/twitchplays login`. In de chat verschijnt een link en een code; de code staat ook al op je klembord. Open de link, log in met het account van je kanaal en keur goed.
   Twitch vraagt toestemming voor drie dingen:
   - kanaalpunt-inwisselingen lezen;
   - bits lezen, zodat Power-ups meetellen voor een overname;
   - berichten in je chat zetten, voor de bevestiging van een overname.
6. Je ziet "Kanaalpunten actief" en "Bits van Power-ups actief". De login wordt onthouden.

Had je al ingelogd met versie 1.0.0? Log dan nog één keer opnieuw in met `/twitchplays login`, zodat de mod ook de twee nieuwe toestemmingen krijgt.

### Beloningen koppelen aan commando's

De mod herkent een beloning aan de **titel**. Een kijker koopt bijvoorbeeld "Hussel mijn inventory"; de mod zoekt die titel op in de config en voert het bijbehorende commando uit (`shuffle`). De titel in Twitch en de titel in de config moeten dus hetzelfde zijn.

**Stap 1: bekijk welke titels in de config staan**

De config is het bestand:

```
%APPDATA%\.minecraft\config\twitchplays.json
```

Heeft je profiel een eigen speelmap, dan staat het in de map `config` daarvan, bijvoorbeeld `%APPDATA%\.minecraft-twitchplays\config\twitchplays.json`.

Plak dat pad in de adresbalk van de Verkenner (of druk Windows-toets + R) en open `twitchplays.json` met Kladblok. Zoek naar het stuk `rewards`. Standaard staat er:

```json
"rewards": {
  "Twitch bestuurt mij": "*",
  "Hussel mijn inventory": "shuffle",
  "Trek mijn armor uit": "unequip"
}
```

Links staat de titel van de beloning, rechts wat er gebeurt:

| Titel (links) | Commando (rechts) | Wat er gebeurt |
|---|---|---|
| Twitch bestuurt mij | `*` | De kijker typt zelf een commando bij het inwisselen, bijv. `lookup 45` of `jump 2` |
| Hussel mijn inventory | `shuffle` | Je inventory wordt door elkaar gegooid |
| Trek mijn armor uit | `unequip` | Je armor gaat uit |

**Stap 2: maak dezelfde beloningen aan op Twitch**

1. Ga naar https://dashboard.twitch.tv en open **Kijkersbeloningen → Kanaalpunten → Beloningen en uitdagingen beheren** (Engels: *Viewer Rewards → Channel Points → Manage Rewards & Challenges*).
2. Klik **Nieuwe aangepaste beloning toevoegen** (*Add New Custom Reward*).
3. Vul bij de naam van de beloning (*Reward Name*) exact de titel uit de config in, bijv. `Hussel mijn inventory`. Hoofdletters maken niet uit, maar spelling en spaties wel.
4. Kies de kosten in kanaalpunten en sla op.
5. Alleen voor **Twitch bestuurt mij**: zet **Kijker moet tekst invoeren** aan (*Require Viewer to Enter Text*). Wat de kijker daar typt, wordt het commando.

Herhaal dit voor elke beloning die je wilt gebruiken. Beloningen die niet in de config staan, negeert de mod; die kun je dus gewoon voor andere dingen houden.

**Zelf beloningen toevoegen of aanpassen**

Voeg in `rewards` een regel toe met jouw titel en een commando uit de lijst hierboven. Na elke regel behalve de laatste staat een komma:

```json
"rewards": {
  "Twitch bestuurt mij": "*",
  "Hussel mijn inventory": "shuffle",
  "Trek mijn armor uit": "unequip",
  "Kijk omhoog!": "lookup 90",
  "Draai je om": "turn"
}
```

Sla het bestand op en typ in Minecraft `/twitchplays reload`. Maak daarna op Twitch een beloning met precies die titel, zoals in stap 2. Kanaalpunt-beloningen worden meteen uitgevoerd, ook in democratie-modus en ook als het commando in `paidOnly` staat. Alleen als de besturing uit staat (F8), of het commando in `disabled` staat, gebeurt er niets; de kijker houdt dan wel zijn ingewisselde punten kwijt.

## Meldingen: subs, follows, donaties, raids en bits

Bij elke melding verschijnt een grote titel in beeld ("NIEUWE SUB!" met de naam eronder), met een feestgeluid en een vuurwerkshow vóór je speler. Hoe groter de steun, hoe meer vuurwerk: een gift bomb van 20 subs geeft een flinke show.

Het vuurwerk bestaat alleen uit deeltjes en geluid op je eigen scherm. Er wordt geen echte raket afgeschoten, dus het doet **nooit schade** en verandert niets aan je wereld. Komen er meerdere meldingen tegelijk binnen, dan komen ze netjes na elkaar in beeld. Staat er een menu open, dan wacht de melding tot je terug in het spel bent.

| Melding | Bron | Nodig |
|---|---|---|
| Sub, resub, gift sub | Twitch-chat | niets |
| Gift bomb (bijv. 10 gift subs) | Twitch-chat | niets; telt als één melding, niet tien |
| Raid | Twitch-chat | niets |
| Bits (vanaf 100) | Twitch-chat | niets |
| Follower | StreamElements | StreamElements koppelen |
| Donatie | StreamElements | StreamElements koppelen |

Meldingen werken ook als de besturing uit staat (F8).

### StreamElements koppelen (eenmalig)

1. Ga naar https://streamelements.com/dashboard/account/channels.
2. Klik bij **JWT Token** op *Show secrets* en kopieer het token.
3. Typ in Minecraft: `/twitchplays streamelements`. Het token is te lang om in de chat te plakken, daarom leest de mod het van je klembord. Daarna wordt het van je klembord gewist.
4. Je ziet "StreamElements verbonden: donaties en follows actief".

Het token staat daarna versleuteld in `config\twitchplays-streamelements.txt`. Ontkoppelen doe je met `/twitchplays streamelements off`.

### Uitproberen

Met `/twitchplays alert <soort>` zie je hoe een melding eruitziet. De soorten zijn `sub`, `resub`, `gift`, `giftbomb`, `follow`, `donation`, `raid` en `bits`.

## Alle `/twitchplays`-commando's (in Minecraft)

| Commando | Wat |
|---|---|
| `/twitchplays` | Status |
| `/twitchplays channel <naam>` | Kanaal instellen en verbinden |
| `/twitchplays on` / `off` | Besturing aan/uit (zelfde als F8) |
| `/twitchplays mode anarchy` / `democracy` | Modus kiezen (zelfde als F9) |
| `/twitchplays test <commando>` | Doe alsof een kijker iets typt, bijv. `/twitchplays test forward 2` |
| `/twitchplays commands` | Lijst met alle commando's |
| `/twitchplays overlay` | Overlay tonen/verbergen |
| `/twitchplays clientid <id>`, `login`, `logout` | Kanaalpunten-login |
| `/twitchplays streamelements` / `streamelements off` | StreamElements koppelen (token van klembord) / ontkoppelen |
| `/twitchplays alerts on` / `off` | Alle meldingen aan/uit |
| `/twitchplays alert <soort>` | Voorbeeldmelding tonen (ook `takeover`) |
| `/twitchplays takeover stop` | Huidige overname beëindigen |
| `/twitchplays takeover test [bits]` | Doen alsof een kijker 1000 (of meer) bits cheert |
| `/twitchplays reload` | Config opnieuw inlezen na handmatig aanpassen |

## Config

Het bestand is `config\twitchplays.json` in je Minecraft-map (standaard `%APPDATA%\.minecraft\config\twitchplays.json`). Na aanpassen: `/twitchplays reload`.

| Instelling | Standaard | Betekenis |
|---|---|---|
| `prefix` | `"!"` | Waar commando's mee beginnen. Leeg (`""`) = kale woorden zoals `forward` |
| `voteSeconds` | `5` | Duur van een stemronde in democratie |
| `userCooldownMs` | `1500` | Hoe lang één kijker moet wachten tussen commando's (anarchie) |
| `maxHoldSeconds` | `5` | Maximale duur voor lopen/springen etc. |
| `modsCanSwitchMode` | `true` | Mods mogen `!anarchie`/`!democratie` typen |
| `paidOnly` | dropstack, shuffle, unequip | Alleen via bits of kanaalpunten |
| `bitsCost` / `bitsDefaultCost` | zie boven | Bits per commando |
| `rewards` | zie boven | Titel kanaalpunt-beloning → commando |
| `aliases` | `{}` | Eigen woorden, bijv. `{"loop": "forward"}` |
| `disabled` | `[]` | Commando's die helemaal uit staan, bijv. `["drop", "dropstack"]` |
| `alerts.enabled` | `true` | Alle meldingen aan/uit |
| `alerts.subs`, `gifts`, `follows`, `donations`, `raids`, `bits` | `true` | Per soort aan/uit |
| `alerts.bitsMinimum` | `100` | Kleinere cheers krijgen geen melding |
| `alerts.title`, `sound`, `fireworks` | `true` | Titel, geluid en vuurwerk los aan/uit |
| `alerts.takeover` | `true` | Melding "OVERNAME!" aan/uit |
| `takeover.enabled` | `true` | Overname met bits aan/uit |
| `takeover.minBits` | `1000` | Kleinste cheer die een overname geeft |
| `takeover.bitsPerMinute` | `100` | Bits per minuut controle |
| `takeover.maxMinutes` | `30` | Maximale duur, ook na verlengen |
| `takeover.chatMessages` | `true` | Bevestiging in de Twitch-chat (vereist login) |

### Tokens en veiligheid

De login voor kanaalpunten (`config\twitchplays-login.json`) en het StreamElements-token (`config\twitchplays-streamelements.txt`) worden **versleuteld** opgeslagen met Windows DPAPI. Alleen jouw Windows-account op deze pc kan ze ontsleutelen; je hoeft geen wachtwoord te onthouden.

- Belandt het bestand ergens anders, bijvoorbeeld in een back-up, een gedeelde map of een zip die je naar iemand stuurt, dan is het daar onleesbaar.
- Het beschermt niet tegen schadelijke software die als jouw Windows-gebruiker op deze pc draait. Zo'n programma kan het token net zo goed ontsleutelen als de mod. Deel de bestanden dus nog steeds niet bewust.
- Nieuwe pc of ander Windows-account? Dan kan de mod de bestanden niet meer lezen. Koppel opnieuw met `/twitchplays streamelements` en `/twitchplays login`.
- Oude bestanden met platte tekst worden bij de eerste start automatisch versleuteld.

## Goed om te weten

- Zet "pauzeren bij focusverlies" uit: druk in het spel op **F3 + P**. Anders pauzeert het spel zodra je naar OBS klikt, en reageert het niet meer op chat. Staat er een menu open (Esc, inventory, chat), dan doet chat even niets; de overlay toont dan "gepauzeerd".
- Houd jij zelf een toets op het toetsenbord ingedrukt terwijl chat hem loslaat, dan blijft jouw toets gewoon ingedrukt.
- Staat "sluipen" bij jou op *wisselen* in plaats van *vasthouden*, dan blijft `!sneak` aan tot het volgende `!sneak`.

## Voor ontwikkelaars

- **Bouwen**: `gradlew build` met JDK 25. De jar komt in `build\libs\`. Op Windows regelt `build.cmd` dat: het gebruikt `%USERPROFILE%\.jdks\jdk-25.0.4.1+1` als die bestaat, anders `JAVA_HOME`.
- **Tests**:
  - `gradlew test` draait de unit tests (parser, stemmen, Twitch-berichten, meldingen, StreamElements, versleuteling).
  - `gradlew runClientGameTest` start Minecraft, maakt een wereld en speelt alle commando's na, met screenshots in `build\run\clientGameTest\screenshots`.
  - `gradlew runProductionClientGameTest` doet hetzelfde met de gebouwde jar. Op de ontwikkel-pc crashte dat harnas af en toe native (0xC0000005 / 0xC0000374), ook vóór de mod iets deed; `runClientGameTest` slaagde elke keer.
- **Netwerkschijven**: Fabric Loom kan niet bouwen vanaf een netwerkpad met spaties (`\\server\map met spatie`). `build.cmd` omzeilt dat met een lokale projectcache. De game tests moeten vanaf een lokale schijf draaien.
- **Versies**: Minecraft 26.3, Fabric Loader 0.19.5 en Fabric API 0.162.0+26.3, ingesteld in `gradle.properties`.
