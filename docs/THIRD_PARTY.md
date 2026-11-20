# Lizenzen und Quellen

## BTB Skin Studio

Code, Starter-Cosmetics (Modelle, Texturen, Animationen, Vorschaubilder) und Dokumentation:
GNU LGPL v3.0 only (LGPL-3.0-only), Copyright (c) 2026 Behind The Blocks contributors – siehe [LICENSE](../LICENSE) und [NOTICE](../NOTICE).
Starter-, Themen-, Gesichts- und Kleidungs-Pack sowie das Skin-Pack „BTB Skins“ werden vollständig von den
Skripten in `tools/` erzeugt; es sind keine fremden Texturen oder Modelle enthalten. Der Holzfäller enthält nur
das Outfit; das Gesicht kommt zur Laufzeit aus dem Steve-Skin des installierten Spiels.

## Verwendete Bibliotheken (zur Laufzeit vom Spiel bereitgestellt)

| Komponente | Lizenz | Verwendung |
|---|---|---|
| Fabric Loader | Apache 2.0 | Mod-Loader |
| Fabric API | Apache 2.0 | Events, Netzwerk, Render-Layer, Tastenbelegung, Befehle |
| Gson (Teil von Minecraft) | Apache 2.0 | JSON lesen/schreiben |
| SDL3 (Teil von Minecraft) | zlib | Datei-Dialog |

## Übernommenes Wissen

- **Bedrock Skins** von BrandonItaly (https://github.com/BrandonItaly/Bedrock-Skins, MIT, © 2025):
  Die Box-Zuordnung für 128×128-Persona-Körpertexturen in `bedrock/PersonaLayout.java` folgt diesem
  Projekt, ebenso die Idee, Skin-Packs direkt als Ordner/ZIP zu laden. Für diese Teile gilt zusätzlich:

  > Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
  > associated documentation files (the "Software"), to deal in the Software without restriction,
  > including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
  > and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
  > subject to the following conditions: The above copyright notice and this permission notice shall be
  > included in all copies or substantial portions of the Software.
  > THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
  > LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
  > IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  > LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
  > WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
- **Bedrock Skin Packs** (Ressourcenpaket auf Modrinth): Der Skin-Pack-Katalog liest dasselbe Index-Format
  (`{"packs":[{id,name,description,downloadURI,imageUrl}]}`) und nutzt standardmäßig dieselbe Index-URL. Es wird
  kein Inhalt daraus mitgeliefert; die Packs lädt der Nutzer selbst auf Klick.
- **Controlify** (isXander): nur optionale Erkennung über die öffentliche API, keine Abhängigkeit.
- Das Bedrock-Geometrie- und Animationsformat ist von Mojang öffentlich dokumentiert
  (https://learn.microsoft.com/minecraft/creator/).

## Minecraft-Inhalte

Die Mod liefert keine Minecraft- oder Marketplace-Inhalte aus. Die Standard-Skins (Steve, Alex usw.)
werden zur Laufzeit aus dem installierten Spiel geladen. Bedrock-Inhalte werden nur aus Dateien gelesen,
die der Nutzer selbst besitzt; verschlüsselte Marketplace-Inhalte werden erkannt und nicht geöffnet.
Screenshots in `docs/` zeigen Standard-Skins, eigene Skins und Cosmetics; der Katalog-Screenshot zeigt die
Vorschaubilder aus dem Online-Katalog, wie sie in der Mod erscheinen.

Kein offizielles Minecraft-Produkt. Nicht von Mojang oder Microsoft genehmigt oder mit ihnen verbunden.
