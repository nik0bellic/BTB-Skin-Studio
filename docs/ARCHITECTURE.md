# Architektur

## Überblick

```
src/main   (Client und Server, ohne Client-Klassen)
  cosmetic/   Datenmodell: Kategorie, Untertyp, Slot, Befestigungspunkt, CosmeticDefinition
  geo/        Bedrock-Geometrie lesen/schreiben, BakedModel (Quads), PreviewRenderer (Java2D-Vorschau)
  anim/       Bedrock-Animationen, Vorlagen
  molang/     Molang-Teilmenge (Parser + Auswertung)
  pack/       .btbcosmeticpack/.btbcosmetic lesen, prüfen, schreiben, installieren (PackStore)
  skin/       .btbskin, PNG-Import, Bedrock-Skin-Packs, 3D-Hautebenen, Skin-Icons
  bedrock/    Importer (Persona, Skin-Pack-Accessoires, Blockbench), BedrockLocator (Suche auf dem PC)
  loadout/    was ein Spieler trägt: ID, Farbe, Inhalts-Hash, Verschiebung/Größe
  emote/      Emotes: 20 Posen als Keyframes (18 Winkel + Gewicht), Gegenstand, Dauer
  skin/       außerdem Clothing (Kleidung färben und auf den Skin legen), SkinPackWriter (.mcpack-Export)
  network/    Payloads + Protokollversion
  server/     ServerCosmeticSync: Prüfung, Verteilung, Blob-Cache
  util/       JSON, Farben, Übersetzungen (Tr)

src/client
  library/    CosmeticLibrary: Starter-Pack, installierte Packs, eigene Cosmetics, Cache
  render/     CosmeticLayer (Render-Layer am Spieler), CosmeticDrawer, Texturen, Lichtschein, Partikel
  skin/       SkinLibrary (Standard-Skins, eigene, Skin-Packs, Mitspieler), ClientSkins (Override)
  mixin/      AvatarRendererMixin (Render-State, Rüstung, Emote-Gegenstand, Körpergröße),
              PlayerModelMixin (Emote-Posen, Körperproportionen), AbstractClientPlayerMixin (getSkin-Override)
  emote/      ClientEmotes (Abspielen, Partikel, Abbruch beim Laufen), EmoteKeys (Direkttasten)
  gui/        Garderobe (Kosmetik/Skins/Charakter/Emotes), Menüfigur, Emote-Rad, Skin-Pack-Katalog,
              Java-Spielersuche, Skin-Pack-Builder, Import, Pack-Verwaltung, Bedrock-Suche, UI-Bausteine
  editor/     Cosmetic-Editor
  importer/   ImportPlan: Konflikte, Umbenennen, Installation
  net/        ClientCosmeticSync
  data/       Einstellungen, Garderobe (Outfits, Favoriten, Skins)
```

Formate, Pack-Prüfung, Importer und Sync-Server liegen bewusst in `src/main`: Sie lassen sich ohne
Spiel testen (`src/test`), und der dedizierte Server nutzt dieselbe Prüfung wie der Client.

## Rendering

1. Beim Laden wird jedes Modell einmal zu Quads „gebacken“ (`BakedModel`): Positionen, UVs und Normalen
   pro Bone. Animationen ändern danach nur Bone-Matrizen.
2. `AvatarRendererMixin` hängt an den Render-State des Spielers die aktiven Cosmetics (`CosmeticRenderData`).
3. `CosmeticLayer` (über `LivingEntityRenderLayerRegistrationCallback`) gibt pro Cosmetic die Quads über
   `SubmitNodeCollector.submitCustomGeometry` ab: `entityTranslucent` für die Textur, `eyes` für Leuchttexturen,
   Lichtschein als additiver Billboard (`GlowTexture`).
4. Befestigung: Ein Root-Bone mit Körperteilnamen folgt diesem Teil, sonst dem `attachment`. Danach kommen
   Transform (Position/Rotation/Größe um den Drehpunkt) und die Spieler-Anpassung (Verschieben/Größe).
5. Texturen sind nach Inhalts-Hash registriert. Gleiche Cosmetics teilen sich eine GPU-Textur, und ein
   Reload lädt nur geänderte Texturen neu.
6. 3D-Hautebenen (`SkinLayers3D`): Jeder deckende Pixel der äußeren Hautebene wird zu einem Voxel,
   verdeckte Seiten werden weggelassen, Randvoxel bis zur Kante verlängert (keine Lücken an den Ecken).
   Voxel sind am Kopf ¾, sonst ½ Pixel tief; Flächen, die immer verdeckt sind (Hals, Hüfte, Beinoberseite),
   bekommen keine Voxel. Welche Körperteile 3D sind, ist einstellbar. Das Ergebnis wird pro Skin gecacht.
   Blinzeln (`SkinBlink`): eine Kopie des Skins mit geschlossenen Augen wird einmal erzeugt; `getSkin()` liefert
   sie alle paar Sekunden für 0,14 s (pro Spieler versetzt).
7. Rüstung (`ArmorFit`): Mit Helm bleiben nur Brillen (etwas größer, über dem Helm) und der Heiligenschein.
   Brustpanzer schieben Rücken-Teile nach hinten, Elytren blenden Flügel/Umhang/Rucksack aus. Alles andere
   wird gegen die Rüstungs-Hüllen geprüft: erst etwas vergrößern/verschieben, sonst ausblenden.
8. Emotes: `PlayerModelMixin` überblendet nach `setupAnim` die Gliedmaßen mit der Emote-Pose (Gewicht für
   weiches Ein- und Ausblenden); der Gegenstand kommt über `ItemModelResolver` in die rechte Hand.

## Skins

`AbstractClientPlayerMixin` ersetzt den Rückgabewert von `getSkin()` durch `PlayerSkin.insecure(...)` mit
der gewählten Textur. Das gilt nur für Spieler, die einen BTB-Skin gewählt haben; sonst bleibt der
Account-Skin. Accessoires eines Bedrock-Skins werden wie ein Cosmetic gerendert.

Quellen der Skin-Bibliothek: Standard-Skins des Spiels, eigene Skins, `skin_packs/` (Ordner, ZIP, mcpack,
auch vom Katalog geladen), Skin-Packs aus Ressourcenpaketen (`assets/<ns>/skin_packs/<pack>/skins.json`,
optional `btb_bases.json`: Outfit wird beim Laden auf einen Standard-Skin gelegt) und die Skin-Packs der
Bedrock-Installation (`BedrockLocator.builtInSkinPackDirs`).

Der Charakter (`ClothesLibrary`) ist ein eigener Skin `skin:my_character`: Grund-Skin, darüber pro Slot ein
graues Kleidungs-Template, das eingefärbt wird (`Clothing.compose`), plus Detailebene. Gesichtsteile sind
normale Cosmetics aus dem Pack `btb_face` mit Hautfarben-Tint.

## Multiplayer-Protokoll (Version 4)

| Richtung | Payload | Inhalt |
|---|---|---|
| S→C | `Hello` | Protokollversion des Servers |
| C→S | `SetLoadout` | bis zu 16 Einträge: ID, Farbe, Hash, Anpassung |
| C→S | `SetSkin` | Skin-ID, Hash, Slim (oder leer) |
| S→C | `SyncLoadouts` / `SyncSkins` | Zustand aller Spieler beim Beitreten, danach nur Änderungen |
| C→S / S→C | `PlayEmote` / `EmotePlayed` | Emote-ID (Server: höchstens alle 250 ms) |
| C→S | `SetBody` | Größe, Kopf, Arme, Beine (je 0,8–1,25) |
| S→C | `PlayerBody` / `SyncBodies` | Körperproportionen eines / aller Spieler |
| S→C | `RemovePlayer` | Spieler hat verlassen |
| C→S / S→C | `RequestCosmetic` / `CosmeticChunk` | Archiv nach Hash anfordern / in 30-KB-Stücken übertragen |

- Bekannte Cosmetics (Starter-Pack, gleiches Pack installiert) werden nur über die ID verknüpft. Es fließen
  nur wenige hundert Byte.
- Eigene Cosmetics und Skins werden nur übertragen, wenn ein Empfänger den Hash nicht kennt. Der Server
  hält sie in einem LRU-Cache, der Client speichert sie unter `cache/<hash>`. Spätere Sitzungen übertragen
  sie nicht erneut.
- Der Server prüft jedes Archiv mit derselben Logik wie der Import und prüft, ob der Absender das Cosmetic
  trägt. Pro Spieler gilt ein Rate-Limit, ein Archiv darf höchstens 512 KB groß sein.
- Clients ohne Mod oder mit anderer Protokollversion werden ignoriert. Unbekannte IDs werden übersprungen.

## Performance

- Kein Neuaufbau pro Frame: Gebackene Quads und Texturen werden gecacht, Animationen nur ausgewertet,
  wenn das Cosmetic Animationen hat.
- Partikel sind auf 20/s begrenzt, eigene Partikel in der Ego-Perspektive werden nicht erzeugt.
- Fremde Cosmetics lassen sich ausblenden, Partikel und Lichtschein abschalten.
- Vorschaubilder (Python-Generator und `PreviewRenderer`) nutzen einen Tiefenpuffer pro Pixel; die
  Schaufensterpuppe liegt minimal dahinter, damit flach aufliegende Teile immer sichtbar sind.
- Vorschaubilder werden einmal erzeugt (PNG im Pack oder `PreviewRenderer`) und gecacht.
- Kleidung wird nur beim Ändern neu zusammengesetzt, Rüstungsprüfung nur für Spieler mit Rüstung und Cosmetics.
- Skin-Packs aus Installation und Katalog werden im Hintergrund geladen; Netzwerkzugriffe (Katalog,
  Java-Spieler) gibt es nur auf Klick.

## Übersetzung

Alle Texte liegen in `assets/btb_skinstudio/lang/de_de.json` und `en_us.json`. `Tr` folgt der
Spielsprache: Deutsch für `de_*`, sonst Englisch. Namen von Cosmetics und Skins können mehrsprachig sein
(`{"de": …, "en": …}`), Bedrock-Namen kommen aus `texts/<sprache>.lang`.
