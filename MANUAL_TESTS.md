# 📋 Manual Test Guide: XTA Changes

**Version:** 1.0.0  
**Last Updated:** 2025-01-15  
**Target Branch:** `claude/main`

---

## 🎯 Test Overview

This guide provides **step-by-step instructions** for manually testing all changes made in commits:
- `3ef7617` (Cleanup)
- `52c02f8` (TODO List)
- `5a4cf13` (TODO Implementation)
- `e8feee6` (Critical Fixes)

**Total Tests:** 18  
**Estimated Time:** 30-45 minutes  
**Difficulty:** Easy to Medium

---

## 📱 Test Environment

### Requirements
| Requirement | Version | Status |
|-------------|---------|--------|
| Flutter | ≥3.12.2 | ✅ |
| Dart | ≥3.12.2 | ✅ |
| Device/Emulator | Android/iOS | ✅ |
| XTA App | Latest from `claude/main` | ✅ |

### Setup
```bash
# Clone the repository
git clone https://github.com/Aimdi/XTA.git
cd XTA

# Checkout the test branch
git checkout claude/main

# Get dependencies
flutter pub get

# Run the app
flutter run
```

---

## 🧪 Test Cases

---

## 🔴 P0 - Critical Tests (4 Tests)

### Test 1: Profile Animationen
**ID:** P0-T1  
**File:** `lib/profile/profile.dart`  
**Priority:** Critical  
**Estimated Time:** 2 minutes

#### Steps
1. Öffne die App
2. Navigiere zu einem **Profil** (z. B. eigenes Profil oder ein anderes)
3. Scrolle nach **unten** (so dass der Scroll-View Inhalt hat)
4. Tippe auf den **"Nach oben"-Button** (FloatingActionButton)

#### Expected Results
| # | Description | Pass | Fail |
|---|-------------|------|------|
| 1 | Scroll-Animation ist **smooth** (nicht ruckartig) | ✅ | ❌ |
| 2 | Animation dauert **~300ms** | ✅ | ❌ |
| 3 | Animation verwendet **EaseOut-Curve** | ✅ | ❌ |
| 4 | Falls Animation fehlschlägt: **Sofortiger Sprung** zu Top | ✅ | ❌ |

#### Notes
- **Vorher:** `jumpTo(0)` - sofortiger Sprung
- **Jetzt:** `animateTo(0, duration: 300ms, curve: Curves.easeOut)`
- **Fallback:** `jumpTo(0)` wenn Controller nicht ready

---

### Test 2: Profile Loading Guard
**ID:** P0-T2  
**File:** `lib/profile/profile.dart`  
**Priority:** Critical  
**Estimated Time:** 3 minutes

#### Steps
1. Öffne die App
2. Versuche, ein **Profil mit ungültiger ID** zu öffnen (z. B. leere ID oder nicht existierende)
3. Versuche, ein **Profil mit null screenName** zu öffnen

#### Expected Results
| # | Description | Pass | Fail |
|---|-------------|------|------|
| 1 | App **crasht nicht** | ✅ | ❌ |
| 2 | Zeigt **Loading Indicator** (`CircularProgressIndicator`) | ✅ | ❌ |
| 3 | Zeigt **Fehlermeldung** wenn `screenName` null ist | ✅ | ❌ |
| 4 | Fehlermeldung ist **lokalisiert** | ✅ | ❌ |

#### Notes
- **Vorher:** `return Container()` - leerer Bildschirm
- **Jetzt:** `return CircularProgressIndicator()` oder `Text(L10n.of(context).user_not_found)`

---

### Test 3: Profile Flickering
**ID:** P0-T3  
**File:** `lib/profile/profile.dart`  
**Priority:** Critical  
**Estimated Time:** 3 minutes

#### Steps
1. Öffne die App
2. Navigiere zu einem **Profil mit vielen Tweets**
3. Beobachte den **Übergang** zwischen Loading und geladenem Zustand
4. Scrolle schnell **nach oben/unten** und beobachte die UI

#### Expected Results
| # | Description | Pass | Fail |
|---|-------------|------|------|
| 1 | **Kein Flickering** beim Laden | ✅ | ❌ |
| 2 | **Smooth Transition** zwischen States | ✅ | ❌ |
| 3 | Loading Indicator wird **während des Ladens** angezeigt | ✅ | ❌ |
| 4 | UI bleibt **stabil** beim Scrollen | ✅ | ❌ |

#### Notes
- **Vorher:** `AnimatedSwitcher` mit `Container(key: 'waiting')`
- **Jetzt:** `AnimatedCrossFade` mit `CircularProgressIndicator`
- **Duration:** 200ms (vorher 150ms)

---

### Test 4: O(n²) Sortier-Logik
**ID:** P0-T4  
**File:** `lib/subscriptions/users_model.dart`  
**Priority:** Critical  
**Estimated Time:** 5 minutes

#### Steps
1. Öffne die App
2. Gehe zu **Einstellungen > Abonnements**
3. Füge **10+ Abonnements** hinzu
4. Ändere die **Sortier-Reihenfolge** (Custom Order)
5. Beobachte die **Performance** beim Sortieren

#### Expected Results
| # | Description | Pass | Fail |
|---|-------------|------|------|
| 1 | Sortierung **funktioniert korrekt** | ✅ | ❌ |
| 2 | **Keine spürbare Verzögerung** (O(n) statt O(n²)) | ✅ | ❌ |
| 3 | Sortierung wird **nur bei Änderungen** gespeichert | ✅ | ❌ |
| 4 | **Kein Memory Leak** | ✅ | ❌ |

#### Notes
- **Vorher:** `firstWhereOrNull` + `removeWhere` - O(n²)
- **Jetzt:** `indexWhere` + `removeAt` - O(n)
- **State Flags:** `_isInitialLoad`, `_hasLoaded`

---

## 🟡 P1 - High Priority Tests (4 Tests)

### Test 5: In-App Browser (Vorbereitung)
**ID:** P1-T5  
**File:** `lib/tweet/_card.dart`  
**Priority:** High  
**Estimated Time:** 2 minutes

#### Steps
1. Öffne die App
2. Finde einen **Tweet mit externem Link** (z. B. Event-Karte)
3. Tippe auf den **Link**

#### Expected Results
| # | Description | Pass | Fail |
|---|-------------|------|------|
| 1 | Link öffnet sich **im System-Browser** (vorläufig) | ✅ | ❌ |
| 2 | **Kein Crash** beim Öffnen | ✅ | ❌ |
| 3 | TODO-Kommentare **entfernt** | ✅ | ❌ |

#### Notes
- **Aktuell:** Externe Links öffnen sich im System-Browser
- **Zukünftig:** In-App-Browser mit `webview_flutter`
- **Status:** Code ist vorbereitet für In-App-Browser

---

### Test 6: Suchfeld-Fokus
**ID:** P1-T6  
**File:** `lib/search/search.dart`  
**Priority:** High  
**Estimated Time:** 3 minutes

#### Steps
1. Öffne die App
2. Gehe zu **Suche**
3. Tippe in das **Suchfeld** und gib Text ein
4. Tippe **außerhalb** des Suchfelds (Focus verlieren)
5. Tippe **wieder in das Suchfeld**

#### Expected Results
| # | Description | Pass | Fail |
|---|-------------|------|------|
| 1 | Cursor **bleibt an der Position** | ✅ | ❌ |
| 2 | Text **wird nicht zurückgesetzt** | ✅ | ❌ |
| 3 | **Kein unerwartetes Verhalten** | ✅ | ❌ |

#### Notes
- **Vorher:** Cursor sprang zurück zum Anfang
- **Jetzt:** `FocusNode.addListener` speichert Cursor-Position
- **Methode:** `addPostFrameCallback` stellt Position wieder her

---

### Test 7: Thread-Handling
**ID:** P1-T7  
**File:** `lib/client/client.dart`  
**Priority:** High  
**Estimated Time:** 3 minutes

#### Steps
1. Öffne die App
2. Navigiere zu einem **Tweet mit Thread** (z. B. Conversation)
3. Beobachte, wie **Antworten gruppiert** werden
4. Prüfe, ob **alle Tweets** im Thread angezeigt werden

#### Expected Results
| # | Description | Pass | Fail |
|---|-------------|------|------|
| 1 | Threads werden **korrekt gruppiert** | ✅ | ❌ |
| 2 | **Keine fehlenden Tweets** | ✅ | ❌ |
| 3 | **Keine Duplikate** | ✅ | ❌ |

#### Notes
- **Vorher:** `createTweetChains`
- **Jetzt:** `createUnconversationedChainsGraphql`
- **Parameter:** `mapToThreads: false`, `includeReplies: true`

---

### Test 8: Empty State für Trends
**ID:** P1-T8  
**File:** `lib/trends/_list.dart`  
**Priority:** High  
**Estimated Time:** 2 minutes

#### Steps
1. Öffne die App
2. Gehe zu **Trends**
3. **Lösche alle Trends** (falls möglich) oder simuliere leere Liste

#### Expected Results
| # | Description | Pass | Fail |
|---|-------------|------|------|
| 1 | **Empty State wird angezeigt** | ✅ | ❌ |
| 2 | **Icon** (`Icons.trending_up`) wird angezeigt | ✅ | ❌ |
| 3 | **Message** wird angezeigt | ✅ | ❌ |
| 4 | **Pull-to-Refresh Hinweis** wird angezeigt | ✅ | ❌ |

#### Notes
- **Vorher:** `return Container()` - leerer Bildschirm
- **Jetzt:** `Center` + `Column` + `Icon` + `Text`

---

## 🟢 P2 - Medium Priority Tests (5 Tests)

### Test 9: Legacy Fields
**ID:** P2-T9  
**File:** `lib/client/tweet_models.dart`  
**Priority:** Medium  
**Estimated Time:** 1 minute

#### Steps
1. Öffne die App
2. Lade **ältere Tweets** (falls vorhanden)
3. Prüfe, ob **alle Felder** korrekt angezeigt werden

#### Expected Results
| # | Description | Pass | Fail |
|---|-------------|------|------|
| 1 | **Keine NullPointer-Exceptions** | ✅ | ❌ |
| 2 | Legacy-Felder (`coordinates`, `truncated`, etc.) sind **null oder leer** | ✅ | ❌ |
| 3 | **Keine UI-Probleme** | ✅ | ❌ |

#### Notes
- **Felder:** `coordinates`, `truncated`, `place`, `possiblySensitiveAppealable`
- **Status:** Dokumentiert für Backwards-Kompatibilität

---

### Test 10: GroupingMode Enum
**ID:** P2-T10  
**File:** `lib/client/timeline_parser.dart`  
**Priority:** Medium  
**Estimated Time:** 2 minutes

#### Steps
1. Öffne die App
2. Prüfe, ob **alle Timeline-Funktionen** arbeiten
3. Prüfe, ob **keine Type-Errors** auftreten

#### Expected Results
| # | Description | Pass | Fail |
|---|-------------|------|------|
| 1 | **Keine Type-Errors** | ✅ | ❌ |
| 2 | `GroupingMode.threads` funktioniert | ✅ | ❌ |
| 3 | `GroupingMode.flat` funktioniert | ✅ | ❌ |

#### Notes
- **Enum:** `GroupingMode` (`threads`, `flat`)
- **Export:** `export 'timeline_parser.dart' show GroupingMode`

---

### Test 11: Factory Pattern
**ID:** P2-T11  
**File:** `lib/database/entities.dart`  
**Priority:** Medium  
**Estimated Time:** 2 minutes

#### Steps
1. Öffne die App
2. Gehe zu **Gruppen**
3. **Lade eine Gruppe** und prüfe, ob alle Daten korrekt sind

#### Expected Results
| # | Description | Pass | Fail |
|---|-------------|------|------|
| 1 | Gruppe wird **korrekt geladen** | ✅ | ❌ |
| 2 | **Alle Subscriptions** sind vorhanden | ✅ | ❌ |
| 3 | **Keine NullPointer-Exceptions** | ✅ | ❌ |

#### Notes
- **Methode:** `SubscriptionGroupGet.fromDatabaseMap`
- **Parameter:** `group`, `subscriptions`

---

### Test 12: Immutable State Updates
**ID:** P2-T12  
**File:** `lib/group/group_model.dart`  
**Priority:** Medium  
**Estimated Time:** 2 minutes

#### Steps
1. Öffne die App
2. Gehe zu **Gruppen**
3. **Ändere eine Gruppe** (z. B. Name, Icon)
4. Prüfe, ob die **Änderungen sofort sichtbar** sind

#### Expected Results
| # | Description | Pass | Fail |
|---|-------------|------|------|
| 1 | Änderungen werden **sofort angezeigt** | ✅ | ❌ |
| 2 | **Keine verzögerten Updates** | ✅ | ❌ |
| 3 | **Keine UI-Bugs** | ✅ | ❌ |

#### Notes
- **Pattern:** `copyWith` für Immutable Updates
- **Dokumentation:** Hinzugefügt

---

### Test 13: No Data State für Gruppen
**ID:** P2-T13  
**File:** `lib/group/group_screen.dart`  
**Priority:** Medium  
**Estimated Time:** 2 minutes

#### Steps
1. Öffne die App
2. Versuche, eine **nicht existierende Gruppe** zu öffnen
3. Prüfe, ob **Empty State** angezeigt wird

#### Expected Results
| # | Description | Pass | Fail |
|---|-------------|------|------|
| 1 | Empty State wird **angezeigt** | ✅ | ❌ |
| 2 | **Icon** (`Icons.group_outlined`) wird angezeigt | ✅ | ❌ |
| 3 | **Message** wird angezeigt | ✅ | ❌ |
| 4 | **Hinweis zum Erstellen** wird angezeigt | ✅ | ❌ |

#### Notes
- **Vorher:** `return _loadingView()` - Lade-View
- **Jetzt:** `Center` + `Column` + `Icon` + `Text`

---

## 🔵 P3 - Low Priority Tests (4 Tests)

### Test 14: Card States
**ID:** P3-T14  
**File:** `lib/tweet/_card.dart`  
**Priority:** Low  
**Estimated Time:** 1 minute

#### Steps
1. Öffne die App
2. Finde Tweets mit **verschiedenen Card-Typen**
3. Prüfe, ob alle **korrekt angezeigt** werden

#### Expected Results
| # | Description | Pass | Fail |
|---|-------------|------|------|
| 1 | **Keine UI-Probleme** mit Cards | ✅ | ❌ |
| 2 | **Alle Card-Typen** funktionieren | ✅ | ❌ |

#### Notes
- **Dokumentierte Zustände:** `live`, `ended`, `upcoming`, `replay`

---

### Test 15: Datumsformat
**ID:** P3-T15  
**File:** `lib/database/entities.dart`  
**Priority:** Low  
**Estimated Time:** 1 minute

#### Steps
1. Öffne die App
2. Prüfe, ob **alle Daten** korrekt formatiert sind

#### Expected Results
| # | Description | Pass | Fail |
|---|-------------|------|------|
| 1 | **Daten werden korrekt angezeigt** | ✅ | ❌ |
| 2 | **Keine Formatierungsprobleme** | ✅ | ❌ |

#### Notes
- **Format:** ISO 8601 (`yyyy-MM-dd hh:mm:ss`)

---

### Test 16: Export-Funktionalität
**ID:** P3-T16  
**File:** `lib/settings/settings_export_screen.dart`  
**Priority:** Low  
**Estimated Time:** 2 minutes

#### Steps
1. Öffne die App
2. Gehe zu **Einstellungen > Export**
3. Versuche, **Daten zu exportieren**

#### Expected Results
| # | Description | Pass | Fail |
|---|-------------|------|------|
| 1 | **Export funktioniert** | ✅ | ❌ |
| 2 | **Keine Secrets** im Export | ✅ | ❌ |
| 3 | **Kein Crash** | ✅ | ❌ |

#### Notes
- **Status:** Verifiziert und dokumentiert

---

### Test 17: Übersetzung
**ID:** P3-T17  
**File:** `lib/utils/translation.dart`  
**Priority:** Low  
**Estimated Time:** 1 minute

#### Steps
1. Öffne die App
2. Prüfe, ob **alle Texte** korrekt übersetzt sind

#### Expected Results
| # | Description | Pass | Fail |
|---|-------------|------|------|
| 1 | **Alle Texte sind übersetzt** | ✅ | ❌ |
| 2 | **Keine fehlenden Übersetzungen** | ✅ | ❌ |

#### Notes
- **Status:** Übersetzung ist vollständig

---

## 📊 Test Summary

| Priority | Tests | Pass | Fail | Status |
|----------|-------|------|------|--------|
| **P0 Critical** | 4 | - | - | ⏳ |
| **P1 High** | 4 | - | - | ⏳ |
| **P2 Medium** | 5 | - | - | ⏳ |
| **P3 Low** | 4 | - | - | ⏳ |
| **Total** | **17** | - | - | ⏳ |

---

## 📝 Test Notes

### Before Testing
- ✅ App **neu starten** (um Caches zu löschen)
- ✅ **Debug Mode** aktivieren (für bessere Logs)
- ✅ **Device/Emulator** verwenden (kein Web)

### During Testing
- ⚠️ **Langsame Geräte** können Performance-Probleme zeigen
- ⚠️ **Netzwerk-Verbindungen** können API-Calls beeinflussen
- ⚠️ **Datenbank** kann leere States beeinflussen

### After Testing
- ✅ **Screenshots** von Fehlern machen
- ✅ **Logs** sammeln (`adb logcat` oder Flutter DevTools)
- ✅ **Issues** in [GitHub Issues](https://github.com/Aimdi/XTA/issues) melden

---

## 🚨 Known Issues

| ID | Description | Workaround | Status |
|----|-------------|-----------|--------|
| **N/A** | Keine bekannten Issues | - | ✅ |

---

## 📚 Additional Resources

- [TEST_REPORT.md](TEST_REPORT.md) - Detaillierter Test-Report
- [CHANGELOG.md](CHANGELOG.md) - Alle Änderungen dokumentiert
- [TODO_PRIORITIZED.md](TODO_PRIORITIZED.md) - Priorisierte TODO-Liste

---

## 🤝 Contributing

1. **Führe alle Tests** aus diesem Guide aus
2. **Melde Issues** in [GitHub Issues](https://github.com/Aimdi/XTA/issues)
3. **Füge Test-Ergebnisse** zu diesem Dokument hinzu
4. **Aktualisiere** dieses Dokument bei neuen Features

---

## 📄 License

This document is part of the XTA project and is licensed under the MIT License.
