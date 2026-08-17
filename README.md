# Moonshop

Installer ses jeux sur une console Android depuis son propre PC, de n'importe où, sans
compte à créer ni application tierce.

Le projet tient en trois morceaux :

| | Rôle |
|---|---|
| **`app/`** | L'application Android (Kotlin, Jetpack Compose) installée sur la console. |
| **`srv/`** | **Moonshop srv**, l'application PC qui partage un dossier de jeux. Voir [srv/README.md](srv/README.md). |
| **`worker/`** | L'annuaire, un Worker Cloudflare déployé une fois pour toutes. Voir [worker/README.md](worker/README.md). |

## Comment ça marche

Sur le PC, Moonshop srv sert le dossier choisi en HTTP local et ouvre un tunnel
Cloudflare vers lui — aucun port n'est ouvert sur la box, la connexion part de la
machine. L'adresse du tunnel change à chaque démarrage ; c'est pourquoi le PC publie
« code → adresse du moment » dans l'annuaire, et la console retrouve son PC derrière un
code de six caractères tapé une seule fois.

Quand les deux appareils sont sur le même réseau, la console s'en aperçoit toute seule et
télécharge en direct, sans détour par Cloudflare : le débit devient celui du wifi plutôt
que celui de l'upload de la box.

## Reprendre le projet

L'annuaire déployé est celui de ce dépôt, et son quota est partagé : une copie du projet
qui garde son adresse consomme celui-ci. Pour voler de ses propres ailes, déployer le
sien — voir [worker/README.md](worker/README.md) — puis remplacer `URL_BASE` dans
`app/src/main/java/com/monshop/app/Annuaire.kt` et `URL_ANNUAIRE` dans `srv/annuaire.py`.

Rien d'autre n'est partagé : les clés d'API appartiennent à chaque utilisateur, et le
secret qui rattache un code à une machine est tiré localement, à la première ouverture.

## Sécurité

**Le code ne donne pas l'accès, il le demande.** Une console inconnue dépose une demande
que l'utilisateur accepte devant son PC ; elle reçoit alors un jeton qui n'appartient
qu'à elle, révocable depuis la fenêtre. Un code aperçu par-dessus une épaule ne suffit
donc pas à télécharger.

**Un code appartient à la machine qui l'a publié.** Chaque PC tire un secret à sa
première ouverture ; l'annuaire n'en retient que l'empreinte et refuse toute écriture qui
ne la présente pas. Personne ne peut détourner un code vers un autre serveur.

**Le partage est en lecture seule**, limité au dossier choisi, et la sortie de ce dossier
est bloquée. La console ne dispose d'aucun moyen d'écrire sur le PC.

Ce qui reste, et qu'il faut savoir : Cloudflare déchiffre au passage, donc voit les
fichiers transférés à distance ; et la liaison directe sur le réseau local est en HTTP
simple, donc lisible par qui partage ce réseau.

## Clés d'API

Moonshop n'embarque **aucune clé**. Une clé écrite dans l'application serait lisible par
quiconque ouvre le fichier, donc consommable et révocable par un tiers. Chacun fournit
les siennes, demandées une par une par l'assistant de première ouverture, et modifiables
ensuite dans *Settings → API keys*.

| Service | Ce qu'on perd sans lui | Où l'obtenir |
|---|---|---|
| **IGDB** | Descriptions, année, genre, studio, note | [console Twitch](https://dev.twitch.tv/console/apps/create) |
| **SteamGridDB** | Jaquettes, bannières, logos | [profil SteamGridDB](https://www.steamgriddb.com/profile/preferences/api) |
| **Google Drive** | La source Drive, alternative au PC | [Google Cloud](https://console.cloud.google.com/apis/credentials) |

Sans aucune de ces clés, l'application installe les jeux normalement : elle affiche les
noms de fichiers sur des tuiles unies, sans illustration ni description.

## Installer

**Sur la console.** Moonshop se distribue en APK, hors magasin : il faut donc autoriser
l'installation depuis la source qui sert le fichier (navigateur ou gestionnaire de
fichiers), ce qu'Android propose au premier essai. L'appli demande une seule permission,
l'accès à Internet.

**Sur le PC.** Moonshop srv n'a rien à installer : c'est un exécutable qu'on lance.
Windows affiche un avertissement SmartScreen à la première ouverture, l'application
n'étant pas signée par un éditeur enregistré — *Informations complémentaires* puis
*Exécuter quand même*. Une version macOS est compilée à chaque changement mais n'a
jamais été essayée en conditions réelles : la considérer comme expérimentale.

Au premier lancement, un assistant demande le code du PC puis les clés d'API, une par
une, avec un lien vers l'endroit où les obtenir. Chaque étape peut être passée.

## Compiler

L'APK se construit à chaque poussée sur `main` (voir `.github/workflows/build.yml`) et
sort en artefact. En local :

```bash
./gradlew assembleRelease
```

Aucune clé privée n'est présente dans ce dépôt. Sans configuration, la version de
publication est signée avec la clé de debug — suffisant pour installer à la main, pas
pour distribuer largement, puisque cette clé est publique et permettrait à un tiers de
forger une mise à jour qui s'installerait par-dessus.

Pour signer avec une vraie clé, renseigner ces variables d'environnement (ou les secrets
GitHub Actions du même nom) : `MOONSHOP_KEYSTORE_FILE`, `MOONSHOP_KEYSTORE_PASSWORD`,
`MOONSHOP_KEY_ALIAS`, `MOONSHOP_KEY_PASSWORD`.

## Licence

MIT, voir [LICENSE](LICENSE). Cela ne couvre que le code : ce que chacun choisit de
partager avec Moonshop ne regarde que lui.

## Ce qui reste à faire

- Faire essayer la version macOS : elle se compile et son code tient compte du système,
  mais personne ne l'a lancée. Elle n'est ni signée ni notariée.
- Annulation d'un téléchargement en cours.
- Chiffrement de bout en bout entre le PC et la console. Écarté pour l'instant : il
  coûterait du débit là où le tunnel peut déjà être coupé au profit du réseau local.
