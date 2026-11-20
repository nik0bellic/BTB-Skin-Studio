# Dateiformat: `.btbcosmetic` und `.btbcosmeticpack`

Beide Formate sind normale ZIP-Archive. Ein Cosmetic besteht aus Metadaten (`cosmetic.json`),
einem Modell im **Bedrock-Geometrieformat** und PNG-Texturen. Weil das Modell Bedrock-Geometrie ist,
lässt es sich direkt in Blockbench („Bedrock Model“) öffnen und bearbeiten.

## Ordnerstruktur

```
mein_pack.btbcosmeticpack            (ZIP)
├── pack.json                        Pflicht: Pack-Metadaten
├── pack.png                         optional: Symbol
└── cosmetics/
    ├── zauberhut/                   ein Ordner pro Cosmetic (Name frei wählbar)
    │   ├── cosmetic.json            Pflicht
    │   ├── model.geo.json           Pflicht: Bedrock-Geometrie (1.8.0, 1.10.0 oder 1.12.0+)
    │   ├── texture.png              Pflicht
    │   ├── emissive.png             optional: leuchtende Pixel (Vollhelligkeit)
    │   ├── tint_mask.png            optional: deckende Pixel werden eingefärbt
    │   ├── animation.json           optional: Bedrock-Animationen
    │   └── preview.png              optional: Vorschaubild 128×128 für die Garderobe
    └── zauberstab/ …

zauberhut.btbcosmetic                (ZIP) – genau der Inhalt eines Cosmetic-Ordners
```

Ein ZIP, das alles in einem einzigen Unterordner enthält, wird automatisch ausgepackt gelesen.
Eigene Cosmetics liegen unverpackt in `btb_skinstudio/custom/<id>/` und können dort direkt bearbeitet werden.

## `pack.json`

```json
{
  "format": 1,
  "id": "fantasy_hats",
  "version": "1.2.0",
  "name": { "de": "Fantasy-Hüte", "en": "Fantasy Hats" },
  "description": { "de": "Zauberhüte und Kronen" },
  "author": "Nik2810",
  "icon": "pack.png",
  "categories": ["Fantasy"],
  "cosmetics": []
}
```

| Feld | Pflicht | Bedeutung |
|---|---|---|
| `format` | – | Formatversion (aktuell 1). Neuere Formate werden mit klarer Meldung abgelehnt. |
| `id` | ja | Eindeutige Pack-ID, `a-z 0-9 _ . -`. Gleiche ID = Update desselben Packs. Reserviert: `custom`, `btb_builtin*`. |
| `version` | – | Versionsnummer, numerisch verglichen (`1.10.0` > `1.9.2`). Ein Downgrade wird gemeldet. |
| `name`, `description` | – | Text oder Objekt mit Sprachen (`de`, `en`, …). |
| `cosmetics` | – | Optionale Liste der Ordner; leer = jeder Ordner unter `cosmetics/` mit `cosmetic.json`. |

## `cosmetic.json`

```json
{
  "format": 1,
  "id": "fantasy:zauberhut",
  "version": "1.0.0",
  "name": { "de": "Zauberhut", "en": "Wizard Hat" },
  "description": { "de": "Mit funkelnden Sternen." },
  "author": "Nik2810",
  "type": "hat",
  "attachment": "head",
  "slots": ["head_top"],
  "model": "model.geo.json",
  "geometry": "geometry.zauberhut",
  "texture": "texture.png",
  "emissive_texture": "emissive.png",
  "tint_mask": "tint_mask.png",
  "animation": "animation.json",
  "animations": ["animation.zauberhut.wackeln"],
  "preview": "preview.png",
  "transform": { "offset": [0, 0, 0], "rotation": [0, 0, 0], "scale": [1, 1, 1], "pivot": [0, 0, 0] },
  "tint": { "enabled": true, "default": "#FFFFFF", "palette": ["#FFFFFF", "#7FD8FF"] },
  "glow": { "color": "#FFB347", "size": 7, "intensity": 0.6, "offset": [0, 9, 0], "flicker": true },
  "particles": { "type": "minecraft:small_flame", "rate": 1.2, "offset": [0, 10, 0], "spread": 0.6 },
  "hide_layers": ["hat"],
  "tags": ["magie", "hut"]
}
```

| Feld | Bedeutung |
|---|---|
| `id` | **Pflicht.** `namespace:name`, Kleinbuchstaben. Eindeutig über alle installierten Packs. |
| `type` | Untertyp, bestimmt Kategorie und Standard-Slots, z. B. `hat`, `crown`, `glasses`, `backpack`, `wings`, `cape`, `companion`, `boots` – vollständige Liste in `CosmeticType.java`. |
| `attachment` | `head`, `body`, `back`, `left_arm`, `right_arm`, `left_leg`, `right_leg`, `left_shoulder`, `right_shoulder` (`shoulder` = links). |
| `slots` | Belegte Körperbereiche (z. B. `head_top`, `eyes`, `back`, `shoulder_left`). Zwei Cosmetics mit gemeinsamem Slot schließen sich aus. Leer = Standard des Typs. |
| `geometry` | Geometrie-ID in der Modelldatei, falls sie mehrere enthält. |
| `animations` | Diese Animationen laufen in Schleife; leer = alle aus `animation.json`. |
| `transform` | Verschiebung (Pixel), Drehung (Grad), Größe und Drehpunkt relativ zum Befestigungspunkt. |
| `tint` | Einfärbbar; `default` ist die Grundfarbe, `palette` die Vorschläge in der Garderobe. Ohne `tint_mask` wird die ganze Textur gefärbt. |
| `glow` | Rein optischer Lichtschein um einen Punkt (verändert nicht die Weltbeleuchtung). |
| `particles` | Client-Partikel, nur einfache Partikeltypen (`flame`, `small_flame`, `end_rod`, `firefly` …), max. 20/s. |
| `hide_layers` | Blendet Hautebenen aus: `hat`, `jacket`, `sleeves`, `pants`. |

## Modell und Koordinaten

- Bedrock-Spielerraum: Einheit Pixel, Y nach oben, Füße bei 0, Kopf von y=24 bis y=32, vorne ist −Z, links ist +X.
- Ein Modell, das in Blockbench mit der Spielervorlage gebaut wurde, sitzt ohne weitere Anpassung richtig.
- **Wurzel-Bones folgen Körperteilen:** Heißt ein Bone wie ein Körperteil (`head`, `body`, `leftArm`, `rightArm`, `leftLeg`, `rightLeg`, auch `hat`, `jacket`, `leftSleeve` …), bewegt er sich mit diesem Teil. Alle anderen Wurzel-Bones folgen dem `attachment`. So kann ein Cosmetic mehrere Körperteile gleichzeitig nutzen (z. B. Handschuhe für beide Hände).
- Unterstützt: Box-UV und Per-Face-UV, `inflate`, `mirror`, Würfel-Rotation mit Pivot, Bone-Hierarchie. Nicht unterstützt: `poly_mesh` (wird mit Warnung übersprungen).
- Grenzen: 256 Bones, 4096 Würfel, Texturen bis 2048×2048.

## Animationen

Format: Bedrock-Animationsdateien (`format_version` 1.8.0+) mit `rotation`, `position`, `scale` pro Bone,
als Konstante, Molang-Ausdruck oder Keyframes (`linear`, `catmullrom`, `step`, `pre`/`post`).

Molang-Teilmenge: Zahlen, `+ − * /`, Vergleiche, `&& || !`, `?:`, Variablen mit Zuweisung,
`math.sin/cos` (Grad), `abs, sqrt, floor, ceil, round, trunc, min, max, clamp, lerp, mod, pow, exp, ln, atan, atan2, asin, acos, sign, random, hermite_blend, pi`,
sowie `query.anim_time`, `query.life_time`, `query.modified_move_speed`, `query.ground_speed`, `query.walk_distance`,
`query.is_sneaking`, `query.is_on_ground`, `query.head_x_rotation`, `query.head_y_rotation`.
Unbekannte Queries ergeben 0 und werden beim Laden als Warnung gemeldet.

## Prüfung beim Laden

Jedes Pack wird vollständig geprüft, bevor es verwendet wird. Ein defektes Cosmetic wird übersprungen und
erklärt, das restliche Pack bleibt nutzbar. Geprüft werden u. a.: gültiges JSON (auch mit Kommentaren),
Pflichtfelder, ID-Format, doppelte IDs im Pack und gegenüber installierten Packs, Dateinamen (keine Pfade
nach außen), PNG-Signatur und -Größe, Seitenverhältnis Textur/Modell, Animationen, Archivgrenzen
(max. 4096 Dateien, 16 MB pro Datei, 64 MB gesamt).
