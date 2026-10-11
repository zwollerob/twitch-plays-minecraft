# Changelog

## 1.0.0 (2026-10-11)

Eerste uitgave, voor Minecraft 26.3 met Fabric.

### Chat bestuurt de speler
- Chatcommando's voor lopen, springen, sluipen, sprinten, rondkijken, slaan, hakken, plaatsen, eten, hotbar-slots, droppen en wisselen met de andere hand. Bij de meeste hoort een optioneel getal (`!forward 3`, `!lookleft 90`).
- Elk commando werkt met Engelse en Nederlandse woorden (`!forward`, `!w`, `!vooruit`).
- **Anarchie**: elk commando gaat direct, met een cooldown per kijker. **Democratie**: elke 5 seconden wint het populairste commando. Wisselen met F9, of moderators typen `!anarchie` / `!democratie`.
- F8 zet de besturing aan of uit (noodrem).
- Overlay linksboven: de modus, de stemronde met aftelling en de laatste commando's met de naam van de kijker.
- `/twitchplays`-commando's om het kanaal in te stellen, te testen (`/twitchplays test forward 2`) en de config te herladen.
- Chat wordt anoniem gelezen; voor chat en bits hoef je niet in te loggen.

### Bits en kanaalpunten
- Een cheer met een commando (`Cheer300 !unequip`) gaat direct, zonder stemronde en zonder cooldown, als het aantal bits hoog genoeg is.
- `dropstack`, `shuffle` en `unequip` zijn standaard alleen via bits of kanaalpunten te gebruiken.
- Kanaalpunt-beloningen koppel je op titel aan een commando. Met `*` typt de kijker zelf het commando. Inloggen gaat via een code op twitch.tv/activate.

### Meldingen
- Grote titel, geluid en vuurwerk bij subs, resubs, gift subs, gift bombs (één melding), raids en cheers vanaf 100 bits. Deze komen uit de Twitch-chat.
- Follows en donaties via StreamElements: kopieer je JWT-token en typ `/twitchplays streamelements`.
- Het vuurwerk bestaat alleen uit deeltjes op je eigen scherm, doet geen schade en verandert niets aan de wereld. Het gaat af voor je speler, zodat het op stream te zien is.
- Meldingen komen na elkaar in beeld en wachten als er een menu open staat. Per soort aan/uit te zetten; `/twitchplays alert <soort>` toont een voorbeeld.

### Veiligheid
- De Twitch-login en het StreamElements-token worden versleuteld opgeslagen met Windows DPAPI. Oude bestanden met platte tekst worden automatisch versleuteld.

### Je hele inventory
- `!pak <item>` legt een item uit je hele inventory in je hand, op Engelse of Nederlandse naam (`!pak diamond pickaxe`, `!pak brood`).
- `!inv` opent je inventory 3 seconden, zodat chat ziet wat je bij je hebt.

### Springen, omhoog bouwen en bruggen
- `!jumpforward` en `!sprintjump`: lopend of sprintend springen, een blok op of over een gat.
- `!pillar 3`: jezelf omhoog bouwen; pakt zelf een vol blok uit je inventory.
- `!bridge 5`: een brug recht vooruit bouwen en er sluipend overheen lopen, zonder van de rand te vallen.

### Windrichting en precies lopen
- `!noord`, `!oost`, `!zuid`, `!west`: precies die kant op kijken, recht vooruit.
- `!stap 3`: precies zoveel blokken lopen en midden op het blok stoppen; stopt voor obstakels zoals een boomstam.
- De overlay toont welke kant je op kijkt.
