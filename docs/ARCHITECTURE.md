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
  network/    Payloads + Protokollversion
  server/     ServerCosmeticSync: Prüfung, Verteilung, Blob-Cache
  util/       JSON, Farben, Übersetzungen (Tr)

src/client
  library/    CosmeticLibrary: Starter-Pack, installierte Packs, eigene Cosmetics, Cache
  render/     CosmeticLayer (Render-Layer am Spieler), CosmeticDrawer, Texturen, Lichtschein, Partikel
  skin/       SkinLibrary (Standard-Skins, eigene, Skin-Packs, Mitspieler), ClientSkins (Override)
  mixin/      AvatarRendererMixin (Render-State), AbstractClientPlayerMixin (getSkin-Override)
  gui/        Garderobe, Vorschau, Import, Pack-Verwaltung, Bedrock-Suche, Datei-Dialoge, UI-Bausteine
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
   verdeckte Seiten werden weggelassen. Das Ergebnis wird pro Skin einmal berechnet und gecacht.

## Skins

`AbstractClientPlayerMixin` ersetzt den Rückgabewert von `getSkin()` durch `PlayerSkin.insecure(...)` mit
der gewählten Textur. Das gilt nur für Spieler, die einen BTB-Skin gewählt haben; sonst bleibt der
Account-Skin. Accessoires eines Bedrock-Skins werden wie ein Cosmetic gerendert.

## Multiplayer-Protokoll (Version 2)

| Richtung | Payload | Inhalt |
|---|---|---|
| S→C | `Hello` | Protokollversion des Servers |
| C→S | `SetLoadout` | bis zu 16 Einträge: ID, Farbe, Hash, Anpassung |
| C→S | `SetSkin` | Skin-ID, Hash, Slim (oder leer) |
| S→C | `SyncLoadouts` / `SyncSkins` | Zustand aller Spieler beim Beitreten, danach nur Änderungen |
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
- Vorschaubilder werden einmal erzeugt (PNG im Pack oder `PreviewRenderer`) und gecacht.

## Übersetzung

Alle Texte liegen in `assets/btb_skinstudio/lang/de_de.json` und `en_us.json`. `Tr` folgt der
Spielsprache: Deutsch für `de_*`, sonst Englisch. Namen von Cosmetics und Skins können mehrsprachig sein
(`{"de": …, "en": …}`), Bedrock-Namen kommen aus `texts/<sprache>.lang`.
