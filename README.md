# Server-Minispiele für Minecraft 26.3

Eine Fabric-Mod für einen dedizierten Minecraft-Server mit drei Minispielen:

- **Sound erraten:** Alle angemeldeten Spieler hören denselben zufällig ausgewählten Minecraft-Sound. Wer als Erstes die richtige Antwort in den öffentlichen Chat schreibt, gewinnt die Runde. Schwierigkeit und Anzahl der aufeinanderfolgenden Runden sind einstellbar.
- **Hide and Seek:** Ein Spieler versteckt sich, ein anderer sucht. Während der Versteckzeit wartet der Sucher mit Blindheit am Startpunkt. Nametag und Positionsmarker des Versteckers bleiben bis zum Rundenende verborgen.
- **Item-Rennen:** Zwei Spieler erhalten dieselbe zufällige Liste von Survival-Items. Wer zuerst von jedem Item ein Exemplar gesammelt hat, gewinnt. Listengröße und Schwierigkeit sind vor dem Start einstellbar.

Die Mod verwendet ausschließlich vorhandene Minecraft-Sounds und -Items. Auf den Clients ist keine zusätzliche Mod erforderlich. Die bisherigen Projekte im übergeordneten Verzeichnis gehören nicht zu dieser Mod.

## Voraussetzungen

| Komponente | Version |
| --- | --- |
| Minecraft | 26.3 |
| Java | 25 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.162.0+26.3 |
| Fabric Loom | 1.17.21 |
| Gradle Wrapper | 9.6.0 |

Hintergrund zu den Versionen und zur Entwicklungsumgebung: [Fabric für Minecraft 26.3](https://www.fabricmc.net/2026/09/15/263.html), [Fabric für Minecraft 26.1 und Java 25](https://www.fabricmc.net/2026/03/14/261.html).

## Bauen und installieren

Ein JDK 25 installieren und `JAVA_HOME` auf dessen Verzeichnis setzen. Dann in diesem Verzeichnis ausführen:

```powershell
.\gradlew.bat build
```

Unter Linux oder macOS:

```sh
./gradlew build
```

Der Build erzeugt `build/libs/server-minigames-1.4.0.jar`. Diese JAR zusammen mit der passenden Fabric-API-JAR in den Ordner `mods/` des Fabric-Servers kopieren und den Server neu starten. Beim Aktualisieren die alte Mod-JAR ersetzen. Die zusätzliche `-sources.jar` dient zur Einsicht in den Quellcode und wird nicht installiert.

## Sound erraten: Schnellstart

Ein Operator öffnet die Anmeldung; die Standardwerte sind 15 Sekunden Anmeldung und anschließend 60 Sekunden zum Raten:

```text
/minigame sound start
```

Für eine Serie aus fünf schweren Runden:

```text
/minigame sound start schwer 5
```

Zur Auswahl stehen `leicht`, `normal`, `schwer` und `gemischt` (englische Aliase: `easy`, `normal`, `hard`, `mixed`). Ohne Angaben wird eine leichte Runde gespielt. Nach jeder gelösten oder abgelaufenen Runde startet ein Countdown von standardmäßig zehn Sekunden, dann ertönt der nächste Sound. Alle Teilnehmer bleiben für die gesamte Serie angemeldet.

Der Katalog enthält 44 Sounds: 16 leichte, 12 normale und 16 schwere. Zu den schwierigeren Beispielen gehören Aquisator-Angriff, Sculk-Sensor, Respawnanker, Tropfstein, Prüfungsspawner und das Befüllen eines verzierten Krugs. Jeder Sound hat passende deutsche und englische Antworten sowie persönliche Tipps. `Gemischt` kombiniert alle Schwierigkeitsstufen.

Spieler treten während der Anmeldung bei:

```text
/minigame sound beitreten
```

Nach Ablauf der Anmeldung hören alle Teilnehmer den Sound. Antworten werden als normale öffentliche Chatnachrichten geschrieben, zum Beispiel `Kuh`. Jeder Teilnehmer kann einen persönlichen Tipp abrufen oder den Sound erneut hören:

```text
/minigame sound tipp
/minigame sound nochmal
```

Mit `/minigame sound abstimmen` stimmen Teilnehmer dafür, den Sound erneut für alle Teilnehmer abzuspielen. Sobald mehr als die Hälfte zustimmt, wird derselbe Sound gemeinsam wiederholt. Bei vier Teilnehmern braucht es beispielsweise drei Stimmen. Der aktuelle Stimmenstand wird den Teilnehmern im Chat angezeigt.

## Sound erraten: Befehle

| Befehl | Wirkung |
| --- | --- |
| `/minigame` | Hilfe zu den Minispielen anzeigen. |
| `/minigame sound` | Hilfe zu „Sound erraten“ anzeigen. |
| `/minigame sound start [anmeldezeit] [rundenzeit]` | Anmeldung öffnen; Zeiten in Sekunden. Erfordert Operator-Stufe 2 oder die Serverkonsole. |
| `/minigame sound start <schwierigkeit> [runden] [anmeldezeit] [rundenzeit] [countdown]` | Eine Serie mit gewähltem Sound-Pool und Rundenzahl starten. Erfordert Operator-Stufe 2 oder die Serverkonsole. |
| `/minigame sound beitreten` | Während der Anmeldung teilnehmen. Alias: `join`. |
| `/minigame sound verlassen` | Anmeldung oder laufende Runde verlassen. Alias: `leave`. |
| `/minigame sound tipp` | Während der Runde einen privaten, schrittweise deutlicheren Tipp erhalten. Alias: `hint`. |
| `/minigame sound nochmal` | Den Sound während der Runde privat erneut hören; fünf Sekunden Wartezeit zwischen Wiederholungen. Alias: `replay`. |
| `/minigame sound abstimmen` | Für eine gemeinsame Sound-Wiederholung stimmen. Mehr als die Hälfte der aktuellen Teilnehmer muss zustimmen. Alias: `vote`. |
| `/minigame sound status` | Aktuellen Spielstatus anzeigen. |
| `/minigame sound stop` | Anmeldung oder Runde abbrechen. Erfordert Operator-Stufe 2 oder die Serverkonsole. |

Die Anmeldezeit muss zwischen 1 und 300 Sekunden liegen, die Rundenzeit zwischen 1 und 600 Sekunden. Eine Serie umfasst 1 bis 100 Runden; der Countdown zwischen den Runden beträgt 1 bis 60 Sekunden. Die Standardwerte sind eine Runde, 15 Sekunden Anmeldung, 60 Sekunden pro Sound und zehn Sekunden zwischen den Runden.

Zum Beispiel startet `/minigame sound start schwer 5 20 90 8` fünf schwere Runden mit 20 Sekunden Anmeldung, 90 Sekunden Ratezeit pro Sound und acht Sekunden Pause. Der bisherige Befehl `/minigame sound start 30 90` öffnet weiterhin eine Anmeldung für 30 Sekunden mit einer leichten Runde von 90 Sekunden. In der Serverkonsole werden Befehle ohne den führenden Schrägstrich eingegeben.

## Sound erraten: Spielregeln

- Es läuft höchstens ein Sound-Event gleichzeitig. Die Teilnahme ist freiwillig; jeder Spieler kann nur einmal beitreten. Angemeldete Spieler bleiben über alle Runden dabei. Nach der anfänglichen Anmeldung ist ein Beitritt erst zum nächsten Event möglich.
- Nur Antworten angemeldeter Teilnehmer im normalen öffentlichen Chat zählen. Befehle wie `/say`, `/me` und `/msg` zählen nicht als Antworten.
- Geprüft wird die gesamte Nachricht. Groß- und Kleinschreibung, Leerzeichen und Satzzeichen werden vereinheitlicht. Umlaute und ihre Schreibweisen `ae`, `oe`, `ue` sowie `ß` und `ss` werden gleichbehandelt. Passende deutsche und englische Bezeichnungen sowie die jeweilige Minecraft-Sound-ID werden akzeptiert.
- Die erste richtige Antwort, die beim Server ankommt, beendet die Runde und wird mit dem Gewinner bekanntgegeben. Es gibt noch keine Belohnungen oder Punkteliste.
- Nach einer richtigen Antwort oder einem Zeitablauf läuft der eingestellte Countdown bis zum nächsten Sound. Nach der eingestellten Rundenzahl endet das Event automatisch. Ein Zeitablauf zählt als gespielte Runde. Während des Countdowns zählen keine Antworten, Tipps oder Wiederholungsstimmen.
- Innerhalb des gewählten Sound-Pools werden zuerst alle Sounds in zufälliger Reihenfolge gespielt. Erst dann wird der Vorrat neu gemischt; bei mehreren verfügbaren Sounds wird auch am Übergang kein Sound unmittelbar wiederholt.
- Tipps, persönliche Wiederholungswartezeiten und sämtliche Abstimmungsstimmen werden für jeden neuen Sound zurückgesetzt.
- Tipps und persönliches erneutes Abspielen sind nur während der laufenden Runde für Teilnehmer verfügbar und werden nur dem jeweiligen Spieler angezeigt beziehungsweise abgespielt.
- Bei der gemeinsamen Wiederholung zählt jeder Teilnehmer höchstens einmal. Stimmen bleiben bis zur erfolgreichen Abstimmung oder bis zum Rundenende bestehen. Nach der Wiederholung werden sie zurückgesetzt; nach fünf Spielsekunden kann die nächste Abstimmung beginnen. Die Rundenzeit läuft unverändert weiter, und die persönliche Wiederholung bleibt unabhängig davon verfügbar.
- Beim Austritt oder Verbindungsabbruch wird die Stimme dieses Spielers entfernt und die Mehrheit unter den verbleibenden Teilnehmern neu berechnet. Ist sie dadurch erreicht, hören alle verbleibenden Teilnehmer den Sound erneut. Bei nur einem Teilnehmer genügt dessen eigene Stimme.
- Wenn die Zeit abläuft, wird die Lösung bekanntgegeben. Beim manuellen Abbruch wird sie nur aufgedeckt, wenn der Sound bereits gestartet wurde.
- Eine Anmeldung bleibt bis zum Ablauf der Anmeldezeit offen, auch wenn alle bisher angemeldeten Spieler sie verlassen oder die Verbindung trennen. Andere Spieler können noch beitreten; ohne Teilnehmer startet keine Runde. Verlassen während einer laufenden Runde oder eines Zwischen-Countdowns alle Teilnehmer das Spiel, endet das gesamte Event sofort. Bei einem noch laufenden Sound wird die Lösung bekanntgegeben. `/minigame sound stop` bricht ebenfalls die gesamte Serie ab.

Vanilla-Untertitel können einen Sound verraten; Ressourcenpakete können seine Wiedergabe verändern. Der Server kann diese Client-Einstellungen nicht verhindern. Für ein faires Spiel sollten sich die Teilnehmer vorab auf passende Einstellungen einigen.

## Hide and Seek: Schnellstart

Ein Operator bestimmt zwei Spieler: Alice sucht, Bob versteckt sich.

```text
/minigame hideandseek start Alice Bob
```

Beide Spieler müssen online, lebend, wach, ohne Reittier und in derselben Dimension sein. Erlaubt sind Überlebens- und Abenteuermodus. Bob startet innerhalb von 60 Sekunden die Versteckzeit mit:

```text
/minigame hideandseek bereit
```

Ab dann hat Bob standardmäßig 30 Sekunden zum Verstecken. Alice bekommt Blindheit und bleibt am Startpunkt. Bobs Spielermodell wird Alice währenddessen nicht übertragen, sodass auch die Perspektive von außen seinen Weg nicht verrät. Danach endet die Blindheit und Alice hat 180 Sekunden zum Suchen. Bobs Körper ist wieder sichtbar; sein Nametag und Positionsmarker bleiben verborgen.

Alice gewinnt, indem sie Bob mit der linken oder rechten Maustaste aus höchstens drei Blöcken Entfernung anklickt. Dazwischen darf keine Wand sein. Läuft die Suchzeit ab, gewinnt Bob.

## Hide and Seek: Befehle

| Befehl | Wirkung |
| --- | --- |
| `/minigame hideandseek` | Hilfe zu Hide and Seek anzeigen. |
| `/minigame hideandseek start <Sucher> <Verstecker> [Versteckzeit] [Suchzeit]` | Zwei Spieler auswählen und auf die Bereitschaft des Versteckers warten. Erfordert Operator-Stufe 2 oder die Serverkonsole. |
| `/minigame hideandseek bereit` | Als ausgewählter Verstecker die Versteckzeit starten. Alias: `ready`. |
| `/minigame hideandseek status` | Phase, verbleibende Zeit und Rollen anzeigen. |
| `/minigame hideandseek verlassen` | Als Teilnehmer die gesamte Runde abbrechen. Alias: `leave`. |
| `/minigame hideandseek stop` | Die Runde abbrechen. Erfordert Operator-Stufe 2 oder die Serverkonsole. |

Die Versteckzeit muss zwischen 1 und 300 Sekunden liegen, die Suchzeit zwischen 1 und 1800 Sekunden. Ohne Zeitangaben gelten 30 und 180 Sekunden. Zum Beispiel lässt `/minigame hideandseek start Alice Bob 45 240` Bob 45 Sekunden zum Verstecken und Alice vier Minuten zum Suchen. Die Rollen werden direkt beim Start vergeben; ein Beitrittsbefehl ist für dieses Minispiel nicht erforderlich.

## Hide and Seek: Spielregeln und Sichtschutz

- Es läuft höchstens eine Runde Hide and Seek gleichzeitig, mit genau einem Sucher und einem Verstecker. Meldet sich der Verstecker nicht innerhalb von 60 Sekunden bereit, wird die Runde abgesagt.
- Ab Beginn der Versteckzeit bis zum Rundenende wird der Nametag des Versteckers einschließlich einer möglichen Punkteanzeige darunter ausgeblendet. Sein Positionsmarker in der Minecraft-Richtungsleiste wird ebenfalls unterdrückt; der Sucher erhält währenddessen keine Positionsmarker.
- Nur während der Versteckzeit bleibt das Spielermodell des Versteckers für den Sucher verborgen. Nach Beginn der Suche ist es wieder sichtbar. Der Verstecker bekommt keinen Unsichtbarkeitseffekt.
- Der Sucher wird während der Versteckzeit am Startpunkt festgehalten und bekommt Blindheit. Nach Ablauf darf er sich wieder bewegen und suchen. Ein bereits vorhandener Blindheitseffekt wird anschließend mit seiner verbleibenden ursprünglichen Dauer wiederhergestellt.
- Das Anklicken zwischen den beiden Teilnehmern verursacht während der Runde keinen Kampfschaden. Nur der Sucher kann während der Suchphase durch Anklicken des Versteckers aus höchstens drei Blöcken Entfernung mit freier Sicht gewinnen.
- Verlassen, Tod, Verbindungsabbruch, Dimensionswechsel oder ein Wechsel in einen nicht erlaubten Spielmodus brechen die Runde ab. Das gilt auch, wenn ein Teilnehmer einschläft oder auf ein Reittier steigt.
- Bei Rundenende und beim regulären Herunterfahren des Servers werden Sichtschutz, die hinzugefügte Blindheit und die vorübergehenden Attributänderungen entfernt. Bestehende Teams und weltweite Spielregeln bleiben unverändert.

Es gibt keine festgelegte Arena, automatische Teleportation zu einer Arena, Belohnungen oder Punkteliste. Die Spieler wählen ihren Spielbereich selbst.

## Item-Rennen: Schnellstart

Ein Operator startet ein Rennen zwischen zwei verschiedenen Spielern, hier mit zehn normalen Items:

```text
/minigame itemrace start Alice Bob 10 normal
```

Beide Spieler müssen online, lebend und im Überlebensmodus sein. Das Rennen beginnt sofort; beide erhalten dieselbe zufällige Liste ohne doppelte Items. Von jedem Ziel genügt ein Exemplar. Ohne weitere Angaben startet `/minigame itemrace start Alice Bob` mit fünf leichten Items. Eine Schwierigkeit kann auch ohne Anzahl angegeben werden, zum Beispiel `/minigame itemrace start Alice Bob schwer`.

Die Listengröße ist von 1 bis 30 einstellbar. Zur Auswahl stehen `leicht`/`easy`, `normal`, `schwer`/`hard` und `gemischt`/`mixed`. Die drei getrennten Pools enthalten jeweils 36 Items; `gemischt` wählt aus allen 108. Leichte Ziele umfassen häufige Materialien und einfache Rezepte, normale Ziele erfordern mehr Bergbau, Landwirtschaft oder Herstellung. Schwere Ziele können Nether-, End- oder Bossfortschritt voraussetzen. Alle Ziele sind grundsätzlich in Vanilla-Survival erhältlich; die Auswahl berücksichtigt die konkrete Welt und den bisherigen Spielfortschritt nicht.

## Item-Rennen: Befehle

| Befehl | Wirkung |
| --- | --- |
| `/minigame itemrace` | Hilfe zum Item-Rennen anzeigen. |
| `/minigame itemrace start <Spieler1> <Spieler2> [Anzahl] [Schwierigkeit]` | Zwei Spieler auswählen und sofort starten. Standard: fünf leichte Items. Erfordert Operator-Stufe 2 oder die Serverkonsole. |
| `/minigame itemrace liste` | Als Teilnehmer die eigene Liste mit abgehakten Zielen anzeigen. Alias: `list`. |
| `/minigame itemrace status` | Schwierigkeit, Listengröße und Sammelfortschritt beider Spieler anzeigen. |
| `/minigame itemrace verlassen` | Als Teilnehmer das gesamte Rennen abbrechen. Alias: `leave`. |
| `/minigame itemrace stop` | Das Rennen abbrechen. Erfordert Operator-Stufe 2 oder die Serverkonsole. |

## Item-Rennen: Spielregeln

- Es läuft höchstens ein Item-Rennen gleichzeitig, mit genau zwei Teilnehmern. Wer zuerst alle Ziele gesammelt hat, gewinnt. Vervollständigen beide ihre Liste im selben Server-Tick, endet das Rennen unentschieden.
- Am Ende jedes Server-Ticks wird das aktuelle Spielerinventar einschließlich Schnellzugriffsleiste, Nebenhand und angelegter Ausrüstung geprüft. Bereits vor dem Start vorhandene Items zählen mit. Ziele werden dauerhaft für dieses Rennen abgehakt; die Items dürfen danach verwendet, abgelegt oder verloren werden und müssen nicht gleichzeitig im Inventar liegen.
- Inhalte von Bündeln, Shulkerkisten und anderen verschachtelten Behältern werden nicht durchsucht. Endertruhen, externe Behälter und der am Mauszeiger gehaltene Inventarstapel zählen ebenfalls nicht. Ein Item muss bei einer Inventarprüfung im Spielerinventar liegen.
- Tod und Wiederbelebung löschen den Sammelfortschritt nicht. Dimensionswechsel, Schlafen und Reittiere sind erlaubt. Verbindungsabbruch, Verlassen oder Wechsel aus dem Überlebensmodus brechen das gesamte Rennen ohne Gewinner ab.
- Ein manueller Abbruch oder das Herunterfahren des Servers setzt das Rennen ohne Gewinner zurück. Es gibt keine Zeitgrenze und keine Belohnungen.

## Weitere Minispiele ergänzen

Die Spielregeln, Rundenserien, Schwierigkeitsfilter, Befehle, Sound- und Item-IDs, Sammelfortschritt und Hide-and-Seek-Attribute werden automatisiert geprüft. Ein Fabric-Starttest hat außerdem die Mod-Registrierung und das Anwenden des Sichtschutz-Mixins auf Minecraft 26.3 bestätigt; er endete vor dem Weltstart. Ein Sichttest mit zwei echten Spielern steht noch aus.

Der Quellcode liegt unter `src/main/java/de/vali/minigames/`:

| Klasse | Aufgabe |
| --- | --- |
| `ServerMinigamesMod` | Fabric-Einstiegspunkt und Registrierung der Minispiele. |
| `api.ServerMinigame` | Gemeinsame Schnittstelle für zusätzliche Minispiele. |
| `sound.SoundGuessMinigame` | Anbindung von „Sound erraten“ an Server, Befehle, Chat und Soundwiedergabe. |
| `game.SoundGuessGame` | Spielzustand und Regeln unabhängig von der Minecraft-Anbindung. |
| `game.SoundCatalog` | Auswahl vorhandener Sounds mit akzeptierten Antworten und Tipps. |
| `game.SoundDifficulty` | Auswahl und Filterung der Schwierigkeitsstufen. |
| `game.SoundRiddle` | Ein Soundrätsel mit Sound-ID, Lösung, Tipps und Antwortvergleich. |
| `hideandseek.HideAndSeekMinigame` | Befehle, Rollen, Finden durch Anklicken und Anbindung an Serverereignisse. |
| `game.HideAndSeekGame` | Phasen, Zeitgrenzen und Siegbedingungen von Hide and Seek unabhängig von Minecraft. |
| `hideandseek.HideAndSeekProtection` | Vorübergehender Sichtschutz, Blindheit, Festhalten des Suchers und Wiederherstellung. |
| `hideandseek.HideAndSeekVisibility` | Hält fest, welcher Sucher den Verstecker während der Versteckzeit nicht sehen darf. |
| `mixin.HideAndSeekPlayerMixin` | Unterbindet die Übertragung des Spielermodells an den Sucher während der Versteckzeit. |
| `itemrace.ItemRaceMinigame` | Befehle, Inventarprüfung, Fortschrittsanzeige und Serverereignisse des Item-Rennens. |
| `game.ItemRaceGame` | Gemeinsame Zielliste, dauerhafter Sammelfortschritt und Siegbedingungen unabhängig von Minecraft. |
| `game.ItemRaceCatalog` | Getrennte Survival-Item-Pools und zufällige Auswahl ohne Wiederholungen. |
| `game.ItemRaceDifficulty` | Deutsche und englische Namen der Schwierigkeitsstufen des Item-Rennens. |

Für ein weiteres Minispiel `ServerMinigame` implementieren und die Instanz in `ServerMinigamesMod` registrieren. Zusätzliche erratbare Sounds werden im `SoundCatalog` mit Minecraft-Sound-ID, Antwortbezeichnungen und Tipps ergänzt.
