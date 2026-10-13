# Server-Minispiele für Minecraft 26.3

Eine Fabric-Mod für einen dedizierten Minecraft-Server mit zwei Minispielen:

- **Sound erraten:** Alle angemeldeten Spieler hören denselben zufällig ausgewählten Minecraft-Sound. Wer als Erstes die richtige Antwort in den öffentlichen Chat schreibt, gewinnt.
- **Hide and Seek:** Ein Spieler versteckt sich, ein anderer sucht. Während der Versteckzeit wartet der Sucher mit Blindheit am Startpunkt. Nametag und Positionsmarker des Versteckers bleiben bis zum Rundenende verborgen.

Die Mod verwendet ausschließlich vorhandene Minecraft-Sounds. Auf den Clients ist keine zusätzliche Mod erforderlich. Die bisherigen Projekte im übergeordneten Verzeichnis gehören nicht zu dieser Mod.

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

Der Build erzeugt `build/libs/server-minigames-1.2.0.jar`. Diese JAR zusammen mit der passenden Fabric-API-JAR in den Ordner `mods/` des Fabric-Servers kopieren und den Server neu starten. Beim Aktualisieren die alte Mod-JAR ersetzen. Die zusätzliche `-sources.jar` dient zur Einsicht in den Quellcode und wird nicht installiert.

## Sound erraten: Schnellstart

Ein Operator öffnet die Anmeldung; die Standardwerte sind 15 Sekunden Anmeldung und anschließend 60 Sekunden zum Raten:

```text
/minigame sound start
```

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
| `/minigame sound beitreten` | Während der Anmeldung teilnehmen. Alias: `join`. |
| `/minigame sound verlassen` | Anmeldung oder laufende Runde verlassen. Alias: `leave`. |
| `/minigame sound tipp` | Während der Runde einen privaten, schrittweise deutlicheren Tipp erhalten. Alias: `hint`. |
| `/minigame sound nochmal` | Den Sound während der Runde privat erneut hören; fünf Sekunden Wartezeit zwischen Wiederholungen. Alias: `replay`. |
| `/minigame sound abstimmen` | Für eine gemeinsame Sound-Wiederholung stimmen. Mehr als die Hälfte der aktuellen Teilnehmer muss zustimmen. Alias: `vote`. |
| `/minigame sound status` | Aktuellen Spielstatus anzeigen. |
| `/minigame sound stop` | Anmeldung oder Runde abbrechen. Erfordert Operator-Stufe 2 oder die Serverkonsole. |

Die Anmeldezeit muss zwischen 1 und 300 Sekunden liegen, die Rundenzeit zwischen 1 und 600 Sekunden. Zum Beispiel öffnet `/minigame sound start 30 90` eine Anmeldung für 30 Sekunden mit einer anschließenden Runde von 90 Sekunden. In der Serverkonsole werden Befehle ohne den führenden Schrägstrich eingegeben.

## Sound erraten: Spielregeln

- Es läuft höchstens eine Runde „Sound erraten“ gleichzeitig. Die Teilnahme ist freiwillig; jeder Spieler kann nur einmal beitreten. Nach Beginn des Sounds ist ein Beitritt erst zur nächsten Runde möglich.
- Nur Antworten angemeldeter Teilnehmer im normalen öffentlichen Chat zählen. Befehle wie `/say`, `/me` und `/msg` zählen nicht als Antworten.
- Geprüft wird die gesamte Nachricht. Groß- und Kleinschreibung, Leerzeichen und Satzzeichen werden vereinheitlicht. Umlaute und ihre Schreibweisen `ae`, `oe`, `ue` sowie `ß` und `ss` werden gleichbehandelt. Passende deutsche und englische Bezeichnungen sowie die jeweilige Minecraft-Sound-ID werden akzeptiert.
- Die erste richtige Antwort, die beim Server ankommt, beendet die Runde und wird mit dem Gewinner bekanntgegeben. Es gibt noch keine Belohnungen oder Punkteliste.
- Tipps und persönliches erneutes Abspielen sind nur während der laufenden Runde für Teilnehmer verfügbar und werden nur dem jeweiligen Spieler angezeigt beziehungsweise abgespielt.
- Bei der gemeinsamen Wiederholung zählt jeder Teilnehmer höchstens einmal. Stimmen bleiben bis zur erfolgreichen Abstimmung oder bis zum Rundenende bestehen. Nach der Wiederholung werden sie zurückgesetzt; nach fünf Spielsekunden kann die nächste Abstimmung beginnen. Die Rundenzeit läuft unverändert weiter, und die persönliche Wiederholung bleibt unabhängig davon verfügbar.
- Beim Austritt oder Verbindungsabbruch wird die Stimme dieses Spielers entfernt und die Mehrheit unter den verbleibenden Teilnehmern neu berechnet. Ist sie dadurch erreicht, hören alle verbleibenden Teilnehmer den Sound erneut. Bei nur einem Teilnehmer genügt dessen eigene Stimme.
- Wenn die Zeit abläuft, wird die Lösung bekanntgegeben. Beim manuellen Abbruch wird sie nur aufgedeckt, wenn der Sound bereits gestartet wurde.
- Eine Anmeldung bleibt bis zum Ablauf der Anmeldezeit offen, auch wenn alle bisher angemeldeten Spieler sie verlassen oder die Verbindung trennen. Andere Spieler können noch beitreten; ohne Teilnehmer startet keine Runde. Verlassen während einer laufenden Runde alle Teilnehmer das Spiel oder trennen die Verbindung, endet diese Runde sofort und die Lösung wird bekanntgegeben.

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

## Weitere Minispiele ergänzen

Die aktuelle Version wurde mit 100 automatisierten Tests geprüft. Ein Fabric-Starttest hat außerdem die Mod-Registrierung und das Anwenden des Sichtschutz-Mixins auf Minecraft 26.3 bestätigt; er endete vor dem Weltstart. Ein Sichttest mit zwei echten Spielern steht noch aus.

Der Quellcode liegt unter `src/main/java/de/vali/minigames/`:

| Klasse | Aufgabe |
| --- | --- |
| `ServerMinigamesMod` | Fabric-Einstiegspunkt und Registrierung der Minispiele. |
| `api.ServerMinigame` | Gemeinsame Schnittstelle für zusätzliche Minispiele. |
| `sound.SoundGuessMinigame` | Anbindung von „Sound erraten“ an Server, Befehle, Chat und Soundwiedergabe. |
| `game.SoundGuessGame` | Spielzustand und Regeln unabhängig von der Minecraft-Anbindung. |
| `game.SoundCatalog` | Auswahl vorhandener Sounds mit akzeptierten Antworten und Tipps. |
| `game.SoundRiddle` | Ein Soundrätsel mit Sound-ID, Lösung, Tipps und Antwortvergleich. |
| `hideandseek.HideAndSeekMinigame` | Befehle, Rollen, Finden durch Anklicken und Anbindung an Serverereignisse. |
| `game.HideAndSeekGame` | Phasen, Zeitgrenzen und Siegbedingungen von Hide and Seek unabhängig von Minecraft. |
| `hideandseek.HideAndSeekProtection` | Vorübergehender Sichtschutz, Blindheit, Festhalten des Suchers und Wiederherstellung. |
| `hideandseek.HideAndSeekVisibility` | Hält fest, welcher Sucher den Verstecker während der Versteckzeit nicht sehen darf. |
| `mixin.HideAndSeekPlayerMixin` | Unterbindet die Übertragung des Spielermodells an den Sucher während der Versteckzeit. |

Für ein weiteres Minispiel `ServerMinigame` implementieren und die Instanz in `ServerMinigamesMod` registrieren. Zusätzliche erratbare Sounds werden im `SoundCatalog` mit Minecraft-Sound-ID, Antwortbezeichnungen und Tipps ergänzt.
