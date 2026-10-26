# OsmAnd avec l'identité de Telenav (MG4)

La branche `telenav-identity` construit OsmAnd sous l'identité de l'application de navigation d'origine de
la MG4 (Telenav, `com.telenav.app.arp`), pour que le système la démarre comme « la » navigation.

## Ce que l'identité reprend

Les noms viennent du manifeste de `Map_overseas_my24.apk` (Telenav 6.2.60, versionCode 610600735).

| Élément | Telenav | Ce build |
|---|---|---|
| Package | `com.telenav.app.arp` | idem (flavor `telenav`) |
| Libellé | « Nav » | « Nav » |
| versionCode | 610600735 | **610600800** (doit être supérieur pour remplacer l'app) |
| Activité de lancement | `com.telenav.arp.module.map.MainActivity` | `activity-alias` du même nom vers `MapActivity` |
| Actions de l'activité | `MAIN/LAUNCHER`, `com.telenav.arp.activity.NAVIGATION`, `…NAVIGATION_SEARCH`, `…NAVIGATION_SESSION`, `…UISS_SHOW_SCREEN` | les mêmes, sur l'alias |
| Service de l'assistant vocal | `com.telenav.arp.service.NavAppService` (`SAIC_VR_ACTION_START_NAV`) | réimplémenté (`OsmAnd/src-telenav`) |
| Signature | clé AOSP `platform` (SHA-256 `c8a2e9bc…92ab8`) | re-signé par le script avec `C:\Users\damie\jdks\aosp_keys\aosp-platform.p12` (alias `platform`) |

`Version.java` traite ce package comme la variante `nightlyFree` (version gratuite et développeur) : aucun
écran d'achat ni vérification Google Play.

`NavAppService` suit le contrat de l'original : démarré avec l'extra entier `FOREGROUND` (1 = amener la
navigation au premier plan, 0 = répondre seulement), il émet le broadcast `SAIC_VR_ACTION_RESPONSE`
(`COMMAND=SAIC_VR_ACTION_START_NAV`, `STATUS` 0 = accepté, 1 = commande invalide).

## Ce qui n'est pas repris (à faire si besoin)

- `NavigationService` (action `com.telenav.arp.service.NAVIGATION`, ~880 lignes) : le service de commande
  de la navigation (destinations, état…). Son contrat n'est pas reproduit.
- Les diffusions d'état `com.telenav.action.nav.status` et `com.telenav.action.maneuver.status`
  (probablement lues par le combiné d'instruments).
- `ResumeTripProvider`, les services internes et l'écran de cluster (`:cluster`).
- L'icône d'origine (l'icône OsmAnd Nightly est conservée) et `launchMode=singleInstance` (un alias hérite
  du mode de l'activité cible).

## État des essais

Essayé sur un émulateur Android 16 (APK arm64 exécuté par traduction), **pas sur la voiture** :

- les quatre actions `com.telenav.arp.activity.*` et le lancement par package résolvent vers
  `com.telenav.app.arp/com.telenav.arp.module.map.MainActivity` (une seule entrée de lancement) ;
- l'application s'ouvre par ce nom de composant ;
- `NavAppService` démarre sans plantage avec `FOREGROUND=1`, `FOREGROUND=0` et sans extra. La réponse
  `SAIC_VR_ACTION_RESPONSE` n'a pas été observée directement (aucun récepteur de test).

Sur Android 10 et plus, le système bloque le lancement d'une activité depuis un service en arrière-plan
(`BAL_BLOCK`, visible sur l'émulateur) : `FOREGROUND=1` n'y ramène donc pas l'application au premier plan.
L'autoradio est en Android 9, où cette restriction n'existe pas, mais cela reste à confirmer sur la voiture.

## Compiler sur le PC

Prérequis : JDK 21, Android SDK (plateforme 36, build-tools 36.1.0), Git. Le dépôt
[OsmAnd-resources](https://github.com/osmandapp/OsmAnd-resources) doit se trouver **à côté** de celui-ci
(`<parent>\resources`) ; le script le clone (superficiellement) s'il manque.

```powershell
.\scripts\build-telenav.ps1              # compile, signe avec la clé platform, vérifie le certificat
.\scripts\build-telenav.ps1 -Install     # ... puis installe sur l'autoradio connecté en adb
```

Le script accepte `-Abi` (`Arm64` par défaut), `-Jdk`, `-SdkDir`, `-Keystore`, `-KeyAlias`,
`-StorePassword` et `-Serial`. Le résultat est `OsmAnd\build\outputs\telenav\Nav-Arm64-debug.apk`.
La première compilation télécharge Gradle et les dépendances ; les suivantes sont bien plus rapides.

## Installer sur la voiture

`adb install` simple est refusé sur l'autoradio : le script pousse l'APK dans `/data/local/tmp` puis lance
`pm install -r -f`. Après l'installation, si le lanceur garde l'ancienne application,
`adb shell am force-stop` sur son processus puis relancer.

**Garde l'APK d'origine** (`MG4_APKs\com.telenav.app.arp\Map_overseas_my24.apk`) pour revenir en arrière.
