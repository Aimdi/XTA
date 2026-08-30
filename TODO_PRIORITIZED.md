# 🚀 Priorisierte TODO-Liste für XTA

## 📊 Übersicht
- **Gesamt:** 19 TODOs in 14 Dateien
- **Geschätzter Aufwand:** ~72-102 Stunden
- **Empfohlene Teamgröße:** 2 Entwickler
- **Empfohlene Dauer:** 8-10 Wochen

---

## 🔴 P0 - Kritisch (4 Tasks, ~16-24h)
*Funktionalität gebrochen oder schwere Performance-Probleme*

### 1. Profile Animationen aktivieren
- **Datei:** `lib/profile/profile.dart:201`
- **Problem:** Animationen deaktiviert wegen Flutter Issue #52207
- **Action:** Prüfen ob Issue gelöst ist, Animationen re-aktivieren
- **Aufwand:** 2-4 Stunden
- **Impact:** ★★★ (Bessere UX)
- **Link:** https://github.com/flutter/flutter/issues/52207

### 2. Profile Loading Guard
- **Datei:** `lib/profile/profile.dart:208`
- **Problem:** UI-Crash möglich wenn Profil-Daten nicht geladen sind
- **Action:** Null-Checks und Guard-Clauses hinzufügen
- **Aufwand:** 1-2 Stunden
- **Impact:** ★★★ (Verhindert Crashes)

### 3. Profile Flickering beheben
- **Datei:** `lib/profile/profile.dart:635`
- **Problem:** UI flackert beim Laden
- **Action:** `AnimatedSwitcher` oder Placeholder Widgets verwenden
- **Aufwand:** 3-5 Stunden
- **Impact:** ★★★ (Bessere UX)

### 4. O(n²) Sortier-Logik optimieren
- **Datei:** `lib/subscriptions/users_model.dart:106,132`
- **Problem:** Ineffizientes Neu-Sortieren der gesamten Liste
- **Action:** `List.removeAt()` + `List.insert()` statt komplettem Sort
- **Aufwand:** 4-6 Stunden
- **Impact:** ★★★ (Performance-Gewinn)

---

## 🟡 P1 - Hoch (4 Tasks, ~24-32h)
*Starke UX-Verbesserungen*

### 5. In-App Browser für externe URLs
- **Datei:** `lib/tweet/_card.dart:404,446`
- **Problem:** Externe Links öffnen sich im System-Browser
- **Action:** `webview_flutter` für sichere In-App-Ansicht nutzen
- **Aufwand:** 6-8 Stunden
- **Impact:** ★★★ (Bessere UX, Sicherheit)
- **Abhängigkeiten:** Bereits in pubspec.yaml vorhanden

### 6. Suchfeld-Fokus fixen
- **Datei:** `lib/search/search.dart:79`
- **Problem:** Fokus setzt Auswahl zurück zum Anfang
- **Action:** `FocusNode` richtig verwalten, Cursor-Position speichern
- **Aufwand:** 2-4 Stunden
- **Impact:** ★★ (Bessere UX)

### 7. Thread-Handling verbessern
- **Datei:** `lib/client/client.dart:438`
- **Problem:** Suboptimale Thread-Verarbeitung
- **Action:** `createUnconversationedChains` evaluieren und integrieren
- **Aufwand:** 4-6 Stunden
- **Impact:** ★★ (Performance)

### 8. Empty State für Trends
- **Datei:** `lib/trends/_list.dart:43`
- **Problem:** Fehlende leere Zustandsanzeige
- **Action:** `EmptyStateWidget` erstellen und einbinden
- **Aufwand:** 1-2 Stunden
- **Impact:** ★★ (Bessere UX)

---

## 🟢 P2 - Mittel (5 Tasks, ~20-30h)
*Code-Qualität und Wartbarkeit*

### 9. Legacy Fields entfernen
- **Datei:** `lib/client/tweet_models.dart:276`
- **Problem:** Unbenutzte Felder (coordinates, truncated, place)
- **Action:** Analyse durchführen, ungenutzte Felder entfernen
- **Aufwand:** 2-4 Stunden
- **Impact:** ★ (Sauberer Code)

### 10. Boolean-Flag durch Enum ersetzen
- **Datei:** `lib/client/timeline_parser.dart:344`
- **Problem:** `mapToThreads` als Boolean ist unklar
- **Action:** `enum GroupingMode { flat, threads }` erstellen
- **Aufwand:** 2-3 Stunden
- **Impact:** ★ (Bessere Lesbarkeit)

### 11. Factory Pattern für SubscriptionGroup
- **Datei:** `lib/group/group_model.dart:118`
- **Problem:** Inkonistente Erstellung
- **Action:** `SubscriptionGroup.fromMap()` standardisieren
- **Aufwand:** 2-3 Stunden
- **Impact:** ★ (Wartbarkeit)

### 12. Immutable State Updates
- **Datei:** `lib/group/group_model.dart:479`
- **Problem:** Direkte Mutation statt immutable Updates
- **Action:** `copyWith()` Pattern einführen
- **Aufwand:** 3-5 Stunden
- **Impact:** ★ (Vorhersehbarer State)

### 13. No Data State für Gruppen
- **Datei:** `lib/group/group_screen.dart:145`
- **Problem:** Leere Widgets statt proper Empty State
- **Action:** Konsistentes EmptyStateWidget verwenden
- **Aufwand:** 2-3 Stunden
- **Impact:** ★ (Bessere UX)

---

## 🔵 P3 - Niedrig (4 Tasks, ~12-16h)
*Dokumentation und Tests*

### 14. Card States dokumentieren
- **Datei:** `lib/tweet/_card.dart:443`
- **Problem:** Unklare mögliche Zustände
- **Action:** Enum für alle Card-Typen erstellen
- **Aufwand:** 2-3 Stunden
- **Impact:** ★ (Dokumentation)

### 15. Datumsformat standardisieren
- **Datei:** `lib/database/entities.dart:149`
- **Problem:** Inkonistentes created_at Format
- **Action:** `DateFormat` aus intl Package nutzen
- **Aufwand:** 1-2 Stunden
- **Impact:** ★ (i18n)

### 16. Export-Funktionalität testen
- **Datei:** `lib/settings/settings_export_screen.dart:138`
- **Problem:** Ungetestete Export-Logik
- **Action:** Integrationstest schreiben
- **Aufwand:** 2-3 Stunden
- **Impact:** ★ (Qualitätssicherung)

### 17. Übersetzung vervollständigen
- **Datei:** `lib/utils/translation.dart:52`
- **Problem:** Unvollständige Lokalisierung
- **Action:** Alle Strings übersetzen
- **Aufwand:** 3-5 Stunden
- **Impact:** ★ (i18n)

---

## 📅 Umsetzungsplan

### Sprint 1 (1-2 Wochen): P0 - Kritische Fixes
- [ ] #1 Profile Animationen
- [ ] #2 Profile Loading Guard
- [ ] #3 Profile Flickering
- [ ] #4 O(n²) Sortier-Logik

### Sprint 2 (2-3 Wochen): P1 - UX-Verbesserungen
- [ ] #5 In-App Browser
- [ ] #6 Suchfeld-Fokus
- [ ] #7 Thread-Handling
- [ ] #8 Empty State Trends

### Sprint 3 (3-4 Wochen): P2 - Code-Qualität
- [ ] #9 Legacy Fields
- [ ] #10 Boolean-Flag → Enum
- [ ] #11 Factory Pattern
- [ ] #12 Immutable State
- [ ] #13 No Data State Gruppen

### Backlog: P3 - Dokumentation
- [ ] #14 Card States
- [ ] #15 Datumsformat
- [ ] #16 Export Tests
- [ ] #17 Übersetzung

---

## 🛠️ Technische Hinweise

### Für P0-Tasks:
1. **Profile Animationen:** Prüfe Flutter Version ≥ 3.19
2. **Loading Guard:** Nutze `maybeOf()` und Null-Checks
3. **Flickering:** `AnimatedSwitcher` oder `Placeholder` Widgets
4. **Sortier-Logik:** `List.removeAt(index)` + `List.insert(newIndex, item)`

### Für P1-Tasks:
1. **In-App Browser:** Nutze bestehendes `webview_flutter` Package
2. **Suchfeld-Fokus:** `FocusNode` mit `requestFocus()` und Cursor-Position
3. **Thread-Handling:** Performance-Test mit `createUnconversationedChains`
4. **Empty State:** Erstelle wiederverwendbares `EmptyStateWidget`

### Für P2-Tasks:
1. **Legacy Fields:** Nutze `dart analyze` für ungenutzte Felder
2. **Enum:** `enum GroupingMode { flat, threads, conversations }`
3. **Factory Pattern:** Standardisiere `fromMap()` Methoden
4. **Immutable State:** `copyWith()` Pattern für alle Models

---

## 📋 GitHub Issues Vorlage

```markdown
### [P0] Fix Profile Loading Flickering

**Beschreibung:**
Das Profil flackert beim Laden (Zeile 635 in `lib/profile/profile.dart`).

**Akzeptanzkriterien:**
- [ ] Kein sichtbares Flickering beim Laden
- [ ] Placeholder oder Ladeanimation während des Ladens
- [ ] Alle bestehenden Tests passieren

**Aufwand:** 3-5 Stunden
**Labels:** `priority:critical`, `type:bug`, `component:ui`
**Verknüpft mit:** https://github.com/flutter/flutter/issues/52207
```

---

## 📈 Metriken

| Priorität | Anzahl | Aufwand (h) | Impact |
|-----------|--------|-------------|---------|
| P0 Kritisch | 4 | 16-24 | ★★★ |
| P1 Hoch | 4 | 24-32 | ★★ |
| P2 Mittel | 5 | 20-30 | ★ |
| P3 Niedrig | 4 | 12-16 | ★ |
| **Gesamt** | **17** | **72-102** | - |

---

## 🎯 Empfehlungen

1. **Beginne mit P0-Tasks** - Sie haben den höchsten ROI
2. **Parallelisiere P1-Tasks** - In-App Browser und Suchfeld-Fokus können unabhängig voneinander bearbeitet werden
3. **P2-Tasks als Refactoring-Sprints** - Ideal für Code-Qualitäts-Wochen
4. **P3-Tasks für neue Mitwirkende** - Gute Einstiegsprojekte

---

## 📞 Fragen?

Bei Unklarheiten zu einem bestimmten Task:
- **P0/P1:** @maintainer für technische Details
- **P2:** Team-Diskussion für Architekturentscheidungen
- **P3:** PRs willkommen von der Community
