# Bedrock-Kompatibilität

BTB Skin Studio übernimmt **lokal vorhandene, unverschlüsselte** Bedrock-Ressourcen als tragbare Cosmetics.
Grundlage ist eine Analyse des Open-Source-Projekts [Bedrock Skins](https://github.com/BrandonItaly/Bedrock-Skins)
(MIT-Lizenz, Stand November 2026) und der Bedrock-Dateiformate. Es wurde kein Code übernommen; die
Zuordnung der Persona-Körpertexturen folgt dessen dokumentiertem Layout (siehe [THIRD_PARTY.md](THIRD_PARTY.md)).

## Was importiert werden kann

| Quelle | Erkennung | Ergebnis |
|---|---|---|
| **Character-Creator-Teil (Persona)** | Ordner mit `*.meta.json` (`piece_type: persona_*`) | Ein Cosmetic pro Teil. Geometriequellen (`geometry_sources`, mittlere Körpergröße, breite Arme bevorzugt) werden mit ihren Texturen zu einem Atlas zusammengeführt; Kleidung aus `texture_sources` (128×128-Körperbogen oder 64×64) wird auf die äußeren Hautebenen gelegt; `use_face_uv`-Texturen auf Kopf und Hut. Tint-Maps werden zur Färbemaske, `tint_color` zur Grundfarbe. PNG und TGA (unkomprimiert/RLE, 24/32 Bit). |
| **Skin-Pack** | `skins.json` + `geometry.json` | Zusatzteile jedes Skins werden zu einem Cosmetic „… – Accessoire“: eigene Bones (Hörner, Schwänze, Flügel …) **und** zusätzliche Würfel in Standard-Bones (die übliche Art, wie Skin-Packs Kapuzen, Ohren oder Taschen modellieren). Der Skin selbst wird nicht übernommen. Namen kommen aus `texts/*.lang`. |
| **Blockbench / Geometrie** | `*.geo.json`, `*.geometry.json` mit PNG | Ein Cosmetic pro Geometrie; Animationen aus `*animation*.json` im selben Ordner werden mitgenommen. Modelle, die um den Nullpunkt gebaut sind, werden im Editor per „Am Punkt ausrichten“ platziert. |
| **Archive** | `.zip`, `.mcpack`, `.mcaddon`, Ordner | werden durchsucht, alle drei Arten können gemischt vorkommen. |

Übernommen werden Geometrie (inkl. Per-Face-UV, Rotation, Pivot, Inflate, Mirror, Vererbung `name:parent`
im Legacy-Format), Texturen, Lokalisierung und Animationen (Keyframes und Molang). Bones mit Namen eines
Körperteils folgen diesem Teil – genau wie in Bedrock.

## Was bewusst nicht geht

- **Verschlüsselte Marketplace-Inhalte.** Werden `contents.json`, `skins.json`, `manifest.json` oder
  `geometry.json` als verschlüsselt erkannt, bricht der Import mit dieser Meldung ab:
  *„Dieses Pack ist verschlüsselt (Marketplace-Kopierschutz). BTB Skin Studio umgeht keinen Kopierschutz und
  keine Kontoberechtigungen – importierbar sind nur eigene oder frei verfügbare, unverschlüsselte Inhalte.“*
- Es gibt **keinen** Zugriff auf Microsoft-Konten, die Persona-Garderobe eines Kontos oder Online-Kataloge.
  Nicht jedes Marketplace-Cosmetic ist importierbar – nur Dateien, die du rechtmäßig und unverschlüsselt besitzt.
- Nicht unterstützt: `poly_mesh` (wird gemeldet und ausgelassen), animierte Texturen (erstes Bild wird verwendet),
  Partikel- und Sound-Keyframes in Animationen, Persona-Emotes, schmale Persona-Arme (die breite Variante wird genommen).

## Ablauf

1. Datei per „Datei importieren“, Drag & Drop oder über den Ordner `btb_skinstudio/import` auswählen.
2. Die **Import-Vorschau** zeigt jedes erkannte Cosmetic live am eigenen Spieler, dazu alle Hinweise
   (Fehler rot, Warnungen gelb, Infos grau) – z. B. übersprungene Skins ohne Zusatzteile.
3. „Zu meinen Cosmetics“ speichert die Cosmetics als bearbeitbare Ordner in `btb_skinstudio/custom/`.
   IDs, die schon belegt sind, werden automatisch umbenannt.
4. Im Editor lassen sich Typ, Befestigung, Position, Farbe und Animation nachbessern.

## Getestet mit

- Synthetischen Persona-Teilen, Skin-Packs, Blockbench-Modellen und verschlüsselten Dateien (JUnit, `BedrockImportTest`).
- Echten, frei verfügbaren Skin-Packs aus dem Bedrock-Skins-Repository (lokal, nicht mitgeliefert):
  *Battle & Beasts 2* (17 Accessoires aus 45 Skins), *Villains* (14 aus 17), *Campfire Tales* (16 aus 16);
  im Spiel importiert und getragen. Prüfung jederzeit wiederholbar:
  `./gradlew test --tests '*RealData*' -Pbtb.realdata=/pfad/zum/pack`
- Echte Persona-Ordner standen nicht zur Verfügung – siehe [STATUS.md](STATUS.md).
