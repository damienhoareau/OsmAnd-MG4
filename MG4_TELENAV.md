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

## NavigationService

`NavigationService` (action `com.telenav.arp.service.NAVIGATION`, AIDL `INavigationService`, 30 méthodes dans
l'ordre d'origine, donc mêmes numéros de transaction) est l'interface par laquelle les autres applications de
la voiture (assistant vocal, combiné) lisent et pilotent la navigation. Elle est réimplémentée au-dessus du
routage d'OsmAnd :

- lecture : statut (1 guidage, 0 en pause, -1 aucun), identifiant de session, destination, départ, étapes,
  segments de guidage (virages, voies, noms de rue), arêtes de l'itinéraire, état de navigation ;
- commandes : pause, reprise, arrêt, son coupé ou non, répétition de la consigne, ajout d'une étape en tête ;
- OsmAnd n'a pas d'identifiant de session : il est créé au début d'une navigation et supprimé à sa fin.
  Vitesses en m/s et longueurs en m comme chez Telenav ; les types de virage et de voie sont des
  approximations des valeurs de Telenav.

## Informations pour le combiné d'instruments

Dans l'app d'origine, `ProductApplication` remplace l'émetteur de broadcasts par un émetteur qui parle au
service système `com.saicmotor.adapterservice/.services.MapService` : c'est le vrai chemin vers le combiné.
Les broadcasts `com.telenav.action.*` ne sont donc probablement lus que par d'éventuelles applications
tierces. Les deux sont envoyés ici (`NavNotifier`, démarré par `ClusterInitProvider` avec le processus) :

- **`MapService` de SAIC** (`SaicCluster`, `IMapService.aidl`) : l'AIDL d'origine est obfusqué (98 méthodes) ;
  ses numéros de transaction sont repris dans l'ordre, les méthodes inutilisées sont des emplacements.
  Envoyés : état de navigation, manœuvre (numéro d'icône, distance en m ou en pieds, texte), nom de rue,
  voies, distance et minutes restantes, limite de vitesse. Les numéros d'icône et de voie sont ceux de
  Telenav (`ManeuverIcons`, `TelenavBundles.laneCodes`).
- **Broadcasts** (`NavBroadcasts`) : `com.telenav.action.nav.status` (`KEY_NAV_START`, `KEY_NAV_STOP`) et
  `com.telenav.action.maneuver.status` (`KEY_MANEUVER_START`, `KEY_MANERVER_UPDATE`, `KEY_MANEUVER_END`),
  avec `SUB_ACTION` et `ACTION_INFO` (`EXT_STRING_STREET_NAME`, `EXT_STRING_DISTANCE`). Pas d'icône bitmap.

OsmAnd n'émet aucun événement au démarrage du guidage : `NavNotifier` surveille son état une fois par
seconde. Le nombre de feux rouges reste à 0. Contrairement à Telenav, l'app n'enregistre pas de callback
auprès de `MapService` ; si le service l'exige, seule la voiture le montrera.

## Ce qui n'est pas repris (à faire si besoin)

- Dans `NavigationService` : la recherche de lieux (`getPoiList`, `getPoiDetail`, `geocode`), les cartes
  de stationnement et l'âge de la carte. Ils répondent comme Telenav sans résultat (liste vide ou `null`).
  Les images du combiné (`getClusterMapImage`, `getClusterTurnImage`) renvoient des bundles vides.
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

`NavigationService`, essayé avec un client qui utilise l'`INavigationService` d'origine (décompilée) : les
lectures répondent, pause/reprise/son/étape/arrêt agissent sur la navigation OsmAnd en cours.

Les informations du combiné, essayées avec un faux `com.saicmotor.adapterservice` qui journalise les appels
(même AIDL) pendant une conduite simulée de 12 km : les appels arrivent sur les bonnes méthodes, la distance
de manœuvre décroît régulièrement, les icônes (virages, ronds-points), les voies, la limite de vitesse et la
fin de navigation sont envoyées, les broadcasts sont reçus. Le vrai `MapService` n'a pas été essayé.

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
