# TerminalHub: drift-, release- och marknadsforingshandoff

Detta dokument ar skrivet for en person eller en ny Codex-session som inte vet
nagonting om projektet sedan tidigare. Folj stegen i ordning och gissa inte om
signering, versionsnummer eller Google Play-spar.

## 1. Vad TerminalHub ar

TerminalHub ar en Android-app for att styra riktiga terminalsessioner fran en
telefon. Appen ansluter till Linux, macOS, en hemmaserver, arbetsstation eller
VPS via SSH. Den ar byggd runt projektflikar, tmux-baserad ateranslutning och
snabb anvandning av terminalbaserade AI-klienter som Codex, Claude Code,
Gemini och lokala verktyg.

Viktiga egenskaper:

- flera samtidiga projekt- och terminalflikar;
- SSH med losenord eller privat nyckel;
- tmux for sessioner som overlever avbrott och appstarter;
- lokal terminal som separat projekttyp;
- filuppladdning och filhamtning mellan telefon och fjarrprojekt;
- stor textinmatning och inmatningshistorik per projekt;
- sokning, app-/sessionsloggar och SSH-diagnostik;
- export/import av appkonfiguration;
- klickbara HTTP(S)-lankar i terminalen, aven nar en lang lank bryts over flera
  visuella terminalrader;
- production- och diagnostic-flavor, sa testappen kan installeras sida vid sida
  med den riktiga appen.

Appen ar Android-only. Datorn som appen ansluter till behover inte vara Android.

## 2. Projektets fasta fakta

| Post | Varde |
|---|---|
| Repo | `git@github.com:joynes/terminalhub.git` |
| Lokal huvudmapp | `/Users/joka/aiterminalhub/ai-terminal-app` |
| Android application ID | `se.joynes.terminalhub` |
| Diagnostic application ID | `se.joynes.terminalhub.diag` |
| Minsta Android API | 24 |
| Compile/target SDK i aktuell Gradle-konfiguration | 35 |
| Licens | GPL-3.0-only |
| Huvudmoduler | `app`, `terminal-emulator`, `terminal-view` |
| Play Console-konto | Johannes Kahlare |
| Play Console-app | TerminalHub (`se.joynes.terminalhub`) |

Lita pa `app/build.gradle.kts` om README och Gradle skulle saga olika saker.
Gradle-filen ar den exekverbara sanningen.

## 3. Forutsattningar pa byggdatorn

Installera eller verifiera:

1. Git.
2. Android Studio med Android SDK Platform 35 och aktuella Build Tools.
3. JDK 17 eller senare. Android Studios inbyggda JDK fungerar.
4. `adb` fran Android SDK Platform Tools.
5. `rclone` med en konfigurerad remote som heter `gdrive` om APK:n ska laddas
   till projektets Google Drive-mapp.

Pa projektets nuvarande Mac kan Java och adb sattas explicit:

```sh
cd /Users/joka/aiterminalhub/ai-terminal-app
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:/Users/joka/Library/Android/sdk/platform-tools:$PATH"
java -version
adb version
```

For en ny klon:

```sh
git clone git@github.com:joynes/terminalhub.git
cd terminalhub
git submodule status
./gradlew tasks
```

Projektet har inga nodvandiga Git-submoduler i nulaget. Kommandot ovan ar bara
en snabb kontroll sa att en framtida andring inte missas.

## 4. Kor appen fran Android Studio

1. Oppna repo-roten i Android Studio.
2. Lat Gradle Sync bli klar.
3. Oppna **Build Variants**.
4. Valj `diagnosticDebug` for saker testning sida vid sida med production-appen.
5. Anslut en telefon med USB debugging eller starta en emulator.
6. Valj `app`-konfigurationen och tryck Run.

Diagnostic-flavorn har egen appidentitet och egen lokal lagring. Den skriver
inte over den installerade production-appen och ser inte production-appens
sparade servrar eller projekt.

## 5. Bygg och testa fran terminalen

Kor fran repo-roten.

Snabb verifiering av terminalmotor, apptester och Android-testkompilering:

```sh
./gradlew \
  :terminal-emulator:testDebugUnitTest \
  :app:testDiagnosticDebugUnitTest \
  :app:compileDiagnosticDebugAndroidTestKotlin \
  :app:assembleDiagnosticDebug
```

Diagnostic-APK skapas har:

```text
app/build/outputs/apk/diagnostic/debug/app-diagnostic-debug.apk
```

Bygg production release APK och Play-bundle:

```sh
./gradlew assembleProductionRelease bundleProductionRelease
```

Resultat:

```text
app/build/outputs/apk/production/release/app-production-release.apk
app/build/outputs/bundle/productionRelease/app-production-release.aab
```

Kor `git diff --check` och `git status --short` fore commit eller release.

## 6. Versionsnummer: las detta innan Google Play

`versionCode` beraknas i `app/build.gradle.kts` som det hogsta av `204` och
antalet commits i Git:

```sh
git rev-list --count HEAD
```

`versionName` blir `1.<versionCode>`. Exempel: commit count `233` ger
`versionCode=233` och `versionName=1.233`.

Konsekvenser:

- Committa den fardiga andringen **innan** den slutliga releasebuilden.
- Bygg om APK och AAB efter committen.
- Ett versionsnummer som redan har laddats upp till nagot Play-spar kan inte
  anvandas igen, aven om releasen senare togs bort.
- Kontrollera Play Console om lokal commit count riskerar att ligga efter ett
  redan anvant versionsnummer.

Verifiera APK-versionen pa macOS:

```sh
AAPT=$(find "$HOME/Library/Android/sdk/build-tools" -type f -name aapt2 | sort | tail -1)
"$AAPT" dump badging \
  app/build/outputs/apk/production/release/app-production-release.apk | head -1
```

## 7. Release-signering

Releasebyggen stoppas avsiktligt om signeringen saknas. Skapa lokalt
`release-keystore.properties` i repo-roten eller satt motsvarande miljo-
variabler:

```properties
RELEASE_STORE_FILE=/absolut/sokvag/till/upload-keystore.jks
RELEASE_STORE_PASSWORD=<hemligt-losenord>
RELEASE_KEY_ALIAS=<alias>
RELEASE_KEY_PASSWORD=<hemligt-losenord>
```

Alternativt:

```sh
export RELEASE_STORE_FILE=/absolut/sokvag/till/upload-keystore.jks
export RELEASE_STORE_PASSWORD='...'
export RELEASE_KEY_ALIAS='...'
export RELEASE_KEY_PASSWORD='...'
```

Viktigt:

- Commit aldrig keystore, egenskapsfil eller losenord.
- Google Play App Signing ar aktivt. AAB:n signeras med upload-nyckeln och
  Google signerar de APK:er som distribueras genom Play.
- En lokalt signerad release-APK kan normalt inte installeras ovanpa en app som
  installerats fran Google Play om nycklarna skiljer sig. Anvand da Internal
  testing for uppdateringen.

## 8. Installera pa fysisk Android-telefon

Verifiera anslutningen:

```sh
adb devices -l
```

Telefonen maste visas som `device`, inte `offline` eller `unauthorized`.

Installera diagnostic-appen sida vid sida:

```sh
adb install -r \
  app/build/outputs/apk/diagnostic/debug/app-diagnostic-debug.apk
```

Starta diagnostic-appen:

```sh
adb shell monkey -p se.joynes.terminalhub.diag \
  -c android.intent.category.LAUNCHER 1
```

Installera en lokalt signerad production-APK endast nar befintlig installation
ar signerad med samma lokala nyckel:

```sh
adb install -r \
  app/build/outputs/apk/production/release/app-production-release.apk
```

Om adb svarar `INSTALL_FAILED_UPDATE_INCOMPATIBLE` ar signaturen olika. Avinstallera
inte production-appen utan uttryckligt godkannande: en avinstallation kan ta bort
sparade servrar, projekt, nyckelreferenser och annan appdata. Publicera hellre
uppdateringen via Internal testing.

## 9. Grundlaggande funktionstest

Efter installation:

1. Starta appen och kontrollera att flikrad, terminal och tangentbord fungerar.
2. Oppna ett sparat projekt och kontrollera ateranslutning.
3. Kontrollera en frankopplad gra projektflik: tryck pa fliken och verifiera att
   projektet oppnas igen.
4. Skriv ut en enkel URL i terminalen och tryck pa den.
5. Skriv ut en URL som ar langre an terminalbredden och tryck pa valfri del av
   den radbrutna lanken. Hela URL:en ska oppnas i standardwebblasaren.
6. Kontrollera att vanlig text fortfarande fokuserar terminalen/oppnar
   tangentbordet.
7. Kontrollera upload/download, tmux-ateranslutning och appens loggvy.

**Download remote** visar vanliga filer direkt i det aktiva SSH-projektets mapp,
alltsa `<server.projectsFolder>/<project.name>`. Den visar inte filer fran den
allmanna SSH-inloggningsmappen och listar inte undermappar rekursivt.

Exempel for lanktest i terminalen:

```sh
printf '%s\n' 'https://example.com/a/very/long/path?first=1234567890&second=abcdefghijklmnopqrstuvwxyz'
```

## 10. Ladda release-APK till projektets Google Drive

Repo-instruktionen kraver att senaste release-APK laddas till `gdrive:apks/`
efter en fardig kodandring:

```sh
rclone copyto \
  app/build/outputs/apk/production/release/app-production-release.apk \
  gdrive:apks/app-production-release.apk
```

Hamta delningslanken om remoten tillater det:

```sh
rclone link gdrive:apks/app-production-release.apk
```

Verifiera filen:

```sh
rclone lsl gdrive:apks/app-production-release.apk
```

Detta ar distribution av en APK for manuell installation. Google Play tar i
stallet emot AAB-filen.

## 11. Publicera till Google Play Internal testing

Anvand Internal testing for snabb telefonverifiering utan production-review:

1. Bygg och signera `bundleProductionRelease` efter den slutliga committen.
2. Oppna Google Play Console och valj utvecklarkontot **Johannes Kahlare**.
3. Oppna **TerminalHub** (`se.joynes.terminalhub`).
4. Ga till **Test and release > Testing > Internal testing**.
5. Tryck **Create new release**.
6. Ladda upp:
   `app/build/outputs/bundle/productionRelease/app-production-release.aab`.
7. Vanta tills Play visar exakt vantat versionsnummer och inga Errors.
8. Skriv korta release notes pa engelska under `en-US`.
9. Tryck **Next** och las samtliga Errors/Warnings.
10. Errors maste losas. Warnings ska forstas men kan vara icke-blockerande.
11. Tryck **Save and publish** och bekrafta publiceringen.
12. Verifiera att releasen visar **Available to internal testers**.
13. Under fliken **Testers**, kontrollera att ratt e-postlista eller Google
    Group har tillgang och dela opt-in-lanken vid behov.

Intern status fore denna handoff: version `232 (1.232)` publicerades till
Internal testing den 16 augusti 2026. Nasta commit-baserade bygge far ett hogre
nummer och maste visas som en separat release.

## 12. Publicera till Production

Gor detta forst efter test av samma eller motsvarande AAB i Internal testing:

1. Oppna **Test and release > Production** i TerminalHub-appen i Play Console.
2. Valj **Create new release**, eller promota en verifierad intern release om
   Play Console erbjuder det och artefakten ar exakt den som ska publiceras.
3. Ladda upp AAB:n om den inte redan finns i Play artifact library.
4. Kontrollera version code, target SDK, supported devices och release notes.
5. Ga till Preview/Review och las Errors och Warnings.
6. Spara production-andringen.
7. Oppna **Publishing overview**.
8. Skicka andringen till Google review.
9. Verifiera att status blir **In review**. Publiceringstiden bestams darefter
   av Google och eventuella installningar for managed publishing.

Publicera aldrig till Production bara for att testa en APK. Internal testing ar
ratt spar for detta.

## 13. Felsokning

### Java hittas inte

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

### Version code has already been used

Kontrollera `git rev-list --count HEAD`. Gor klart en riktig andring, committa,
bygg om AAB:n och verifiera att versionsnumret okat. Forsok inte ladda upp samma
version igen.

### Release signing is not configured

Kontrollera att alla fyra `RELEASE_*`-varden finns och att keystore-sokvagen ar
absolut och lasbar.

### Telefonen syns inte

```sh
adb kill-server
adb start-server
adb devices -l
```

Bekrafta USB-debugging-dialogen pa telefonen. For tradlos debugging, para och
anslut enligt Androids aktuella dialog och ateranvand det visade IP/port-paret.

### Play-installerad app kan inte ersattas med lokal APK

Det ar sannolikt korrekt signaturskydd. Anvand Internal testing. Avinstallera
inte appen utan att forst exportera konfiguration och fa uttryckligt godkannande.

### Download remote visar inga filer

Kontrollera forst att filerna ligger direkt i det aktiva projektets fjarrmapp,
inte bara i SSH-hemmappen eller i en undermapp. Skriv exempelvis i projektets
terminal:

```sh
pwd
find . -maxdepth 1 -type f -print
```

Tryck darefter **REFRESH** i download-dialogen. Funktionen galler endast
SSH-projekt; lokala projekt har ingen fjarrmapp att hamta fran.

## 14. Kallor i repot som en ny person ska lasa

Las i denna ordning:

1. `README.md` - produktoversikt och normal anvandning.
2. `AGENTS.md` - obligatoriskt arbetsflode for commit och Drive-uppladdning.
3. `app/build.gradle.kts` - flavors, SDK, versionering och signering.
4. `docs/privacy-policy.md` - publicerad integritetspolicy.
5. `THIRD_PARTY_NOTICES.md` och `LICENSE` - licenser och Termux-attribution.
6. `TERMUX_INTEGRATION.md` - hur terminalmodulerna ar integrerade.
7. `docs/assets/terminalhub-demo.gif` - befintligt marknadsforingsmaterial.

## 15. Marknadsforingsbrief

### Primar malgrupp

- utvecklare och tekniska skapare som vill styra en riktig dator fran Android;
- anvandare av Codex, Claude Code, Gemini eller andra terminalbaserade
  AI-klienter;
- personer med hemmaserver, arbetsstation eller VPS bakom Tailscale/VPN;
- personer som vill lata langa terminaljobb fortsatta i tmux och ateransluta
  fran telefonen.

### Produktens tydligaste positionering

TerminalHub ar inte en AI-chatapp och inte en moln-IDE. Det ar en mobil
kontrollpanel for riktiga terminaler och AI-kodningsverktyg som redan kor pa
anvandarens egen dator eller server.

### Starkaste budskap

- Byt snabbt mellan flera levande projektterminaler.
- Fortsatt samma tmux-session efter natverksavbrott eller appstart.
- Anvand valfri terminalbaserad AI-klient; appen laser inte in anvandaren till
  en leverantor.
- Flytta filer mellan telefon och aktivt fjarrprojekt.
- Oppna terminalens langa, radbrutna webblankar med ett tryck.
- Behall den riktiga shellmiljon, repo-kontexten och verktygens egna kommandon.

### Saker marknadsforingen inte far lova utan ny verifiering

- iOS-stod;
- att TerminalHub hostar eller levererar en AI-modell;
- att SSH automatiskt ar sakert pa publikt internet;
- att alla terminalprogram fungerar perfekt med touch/musrapportering;
- foretagsfunktioner, teamadministration eller central policyhantering;
- specifika priser, betyg, installationsantal eller Play-status utan aktuell
  kontroll.

Rekommendera privat VPN/Tailscale och god SSH-hygien. Publicera aldrig privata
serveradresser, nycklar, loggar, e-postlistor eller Play Console-data i
marknadsforingsmaterial.

## 16. Starta en separat Codex-session for marknadsforing

Skapa en ny session med arbetsmappen:

```text
/Users/joka/aiterminalhub/ai-terminal-app
```

Klistra sedan in denna prompt:

```text
Du ansvarar for marknadsforingen av TerminalHub. Borja med att lasa
docs/OPERATIONS_AND_MARKETING_HANDOFF.md, README.md, docs/privacy-policy.md,
THIRD_PARTY_NOTICES.md och AGENTS.md. Inspektera aven docs/assets/terminalhub-demo.gif.

Fokusera endast pa marknadsforing, positionering och lanseringsmaterial. Andra
inte appkod och publicera inget externt utan mitt uttryckliga godkannande.

Ta fram:
1. en kort positioneringsstrategi och tre prioriterade malgrupper;
2. forslag pa Google Play-titel, kort beskrivning och full beskrivning;
3. ett konkret screenshot-/demo-manus med ordning och bildtexter;
4. ett lanseringspaket for GitHub README, Reddit/Hacker News och sociala medier;
5. en lista over pastenden som maste verifieras innan publicering;
6. en 30-dagars plan med matbara men realistiska experiment.

Allt ska vara sakligt. Beskriv TerminalHub som en Android-klient for riktiga
SSH/tmux-terminaler och terminalbaserade AI-verktyg, inte som en egen AI-modell.
Anvand inga personuppgifter, privata serverdetaljer eller hemligheter.
```

En separat session ar lamplig eftersom marknadsforingsarbetet da kan utveckla
budskap, Play-text och kampanjmaterial utan att blanda ihop det med appens
tekniska felsokning och releasearbete. Officiella OpenAI-exempel beskriver samma
grundide: en dedikerad, kontextmedveten projektpartner och ett separat launch-
campaign-arbetsflode.

## 17. Handoff-checklista

Innan en teknisk session avslutas:

- [ ] relevanta tester ar grona;
- [ ] fysisk telefon ar testad nar andringen ar device-/touchberoende;
- [ ] `git diff --check` ar ren;
- [ ] andringen ar committad enligt `AGENTS.md`;
- [ ] release APK ar byggd efter committen;
- [ ] APK-versionen ar verifierad;
- [ ] APK ar uppladdad till `gdrive:apks/`;
- [ ] Play AAB ar byggd om en Play-release efterfragas;
- [ ] Internal testing anvands fore Production;
- [ ] inga hemligheter eller personuppgifter finns i committen eller chatten.
