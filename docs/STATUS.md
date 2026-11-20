# Status

Stand: November 2026, Version 0.1.0, Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.166.2+26.3.

## Build und Tests

- `./gradlew build` läuft fehlerfrei durch (JDK 25).
- 31 Unit-Tests in `src/test`, 30 bestanden, 1 übersprungen (`RealDataImportTest` braucht echte
  Bedrock-Dateien, Aufruf mit `-Pbtb.realdata=<ordner>`). Abgedeckt:
  - Geometrie (alt und neu, Box-/Per-Face-UV), Molang, Animationen
  - Pack lesen, schreiben und prüfen: doppelte IDs, Pfad-Ausbruch, defekte PNGs, Archivgrenzen
  - Bedrock-Importer: Persona, Skin-Pack-Accessoires, verschlüsselte Inhalte
  - Skins: `.btbskin`, PNG-Import, Slim-Erkennung, 64×32, Bedrock-Skin-Packs
  - Sync-Protokoll: Chunking, Grenzen, Server-Prüfung
  - Vorschaubilder; beide Sprachdateien (gültiges JSON, gleiche Schlüssel und Platzhalter)
- Spieltests liefen automatisiert im echten Client (`AutoTest`, Xvfb, Software-Rendering) mit Screenshots.

## Im Spiel geprüft

| Bereich | Ergebnis |
|---|---|
| Alle 38 Starter-Cosmetics | rendern an der richtigen Stelle, von vorne und hinten geprüft (Rücken-Teile) |
| Lichtschein, Partikel, Animationen | Fackel, Feuer-Halo, Eis-Tiara, Heiligenschein, Flügel |
| Garderobe | Kategorien, Untertypen, Suche, Favoriten, Outfits, Konflikte, Farben, Ansichten |
| Anpassen | Verschieben und Skalieren, live in Vorschau und Welt |
| Skins | Standard-Skins und Bedrock-Skin-Pack mit Zusatzteilen in der Welt getragen (PNG-Import per Unit-Test) |
| 3D-Hautebenen | an/aus, Voxel an Ärmeln, Jacke und Haaren |
| Import | Demo-Pack installiert; Pack mit belegter ID wird blockiert, „Umbenennen“ installiert es mit neuer ID |
| Bedrock-Import | Accessoires aus einem unverschlüsselten Skin-Pack |
| Multiplayer | dedizierter Server und zwei Clients: Bob sieht Alices Starter-Cosmetics und ihre eigene Krone (per Hash übertragen) |
| Sprachen | Oberfläche komplett auf Deutsch und Englisch geprüft |

## Bekannte Einschränkungen

- **Marketplace-Inhalte** sind verschlüsselt und werden nicht importiert. Die Mod erkennt sie und erklärt das.
- **Bedrock-Suche auf dem PC:** Die Pfade für Windows (Store/GDK, Preview) entsprechen den bekannten
  Installationsorten, wurden aber nur unter Linux (mcpelauncher-Struktur) getestet, nicht auf einem echten Windows-PC.
- **Character-Creator-Teile (Persona):** Der Import ist mit nachgebauten Testdaten geprüft. Echte
  Persona-Dateien sind meist Teil verschlüsselter Inhalte und standen nicht zur Verfügung.
- **3D-Hautebenen** funktionieren nur für Skins, deren Bild die Mod selbst kennt: Standard-Skins,
  importierte Skins, Skin-Packs und Skins von Mitspielern mit Mod. Der Account-Skin wird vom Spiel direkt auf
  die Grafikkarte geladen; seine Pixel sind für die Mod nicht lesbar.
- **Skin-Sync im Multiplayer** ist per Unit-Test und Protokolltest abgesichert. Im Mehrspieler-Spieltest wurde
  nur der Cosmetic-Sync mit zwei Clients geprüft.
- Spieler ohne Mod sehen weder Cosmetics noch BTB-Skins. Das ist kein Fehler: Die Mod ändert nichts am
  Vanilla-Protokoll.
- Bedrock-`poly_mesh` und Partikeleffekte aus Bedrock-Dateien werden nicht übernommen.
- Lichtschein ist ein optischer Effekt und beleuchtet die Welt nicht.

## Offen / Ideen

- Spieltest der Bedrock-Suche auf Windows
- Skin-Sync mit zwei Clients im Spiel prüfen
- Mehr Starter-Skins (eigene, statt nur Minecraft-Standard)
- Optional: Server-Konfiguration (Größenlimits, erlaubte Packs)
