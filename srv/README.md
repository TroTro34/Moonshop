# Moonshop srv

L'application PC qui partage un dossier de jeux vers l'appli Moonshop, **depuis
n'importe quel réseau**, sans compte à créer ni configuration de box.

## Ce que vit l'utilisateur

1. Il lance `MoonshopSrv.exe` (rien à installer).
2. Il choisit son dossier de jeux.
3. Il clique **Launch**. Un code s'affiche : `A7X9K2`.
4. Sur sa console, il tape ce code **une seule fois**.

Ensuite, plus rien : à chaque ouverture, la console retrouve toute seule l'adresse du
PC, même si celle-ci a changé, même en 4G, même chez quelqu'un d'autre. Le code, lui,
ne change jamais — il est tiré une fois et conservé dans `%APPDATA%\MoonshopSrv`.

## Comment ça marche

| Brique | Rôle |
|---|---|
| `serveur_fichiers.py` | Sert le dossier en HTTP sur toutes les interfaces, derrière un jeton secret |
| `tunnel.py` | Lance `cloudflared` : le serveur devient joignable depuis Internet, par une connexion **sortante** (aucun port à ouvrir, fonctionne derrière un CGNAT) |
| `annuaire.py` | Publie « code → adresse du moment » sur le Worker (voir `../worker/`) |
| `catalogue.py` | Génère `catalogue.json` à la volée depuis l'arborescence du dossier |
| `fichiers.py` | Gestion du contenu partagé depuis la fenêtre (parcourir, créer, renommer, supprimer) |
| `depot.py` | Glisser-déposer depuis l'Explorateur, branché sur la fenêtre native |
| `plateau.py` | Icône dans la zone de notification (l'appli continue fenêtre fermée) |
| `moonshop_srv.py` | Fenêtre, états, orchestration |

Le catalogue suit une convention sans configuration : **un sous-dossier = une catégorie**
(une console), les fichiers à la racine vont dans « Misc ». Une image portant le même nom
qu'un jeu (`Zelda.iso` + `Zelda.jpg`) devient automatiquement sa jaquette.

## Ajouter des jeux

Deux voies, l'une sûre, l'autre plus commode :

- le bouton **Add files**, qui ouvre le sélecteur système (sélection multiple) ;
- le **glisser-déposer** d'un fichier ou d'un dossier n'importe où sur la fenêtre.

Le dépôt est traité par la fenêtre WinForms native et non par l'événement `drop` du
HTML : dans une WebView, les fichiers déposés arrivent sans chemin sur le disque, ce qui
rendrait impossible la copie d'une ROM de plusieurs gigaoctets autrement qu'en la
chargeant entièrement en mémoire. Si ce branchement échoue, la zone pointillée n'apparaît
pas et le bouton reste disponible.

La copie se fait par morceaux, avec une jauge : une ROM volumineuse ne fige pas la
fenêtre. Un fichier de même nom n'écrase jamais l'existant, il devient « Zelda (2).iso ».

## Fermer sans couper le partage

La croix de la fenêtre **cache** l'application au lieu de la quitter : une console est
peut-être en plein téléchargement. L'icône de la zone de notification permet de la
rouvrir ou de la quitter pour de bon. Si cette icône ne peut pas être créée, la fermeture
redevient une vraie sortie — sinon le partage tournerait sans moyen visible de l'arrêter.

## Deux adresses publiées

Le serveur écoute sur **toutes les interfaces**, pas seulement sur la boucle locale : le
PC publie donc son adresse de tunnel *et* son adresse sur le réseau local. La console
essaie la seconde en premier et bascule en direct quand elle est sur le même wifi — les
fichiers ne font alors plus l'aller-retour jusqu'à Cloudflare, et le débit n'est plus
plafonné par l'upload de la box.

Conséquence à connaître : **Windows demande une autorisation de pare-feu au premier
Launch**. Sans elle, le partage fonctionne toujours par le tunnel, mais la liaison directe
sur le réseau local est bloquée.

## Sécurité

Tout est servi derrière un jeton aléatoire régénéré **à chaque Launch** :
`https://<tunnel>.trycloudflare.com/<jeton>/...`. L'URL du tunnel seule ne donne accès à
rien, et couper le partage invalide immédiatement les anciennes adresses.

Deux limites à connaître :

- Qui possède le code peut télécharger le contenu du dossier tant que le partage tourne.
  Six caractères font environ un milliard de combinaisons ; active tout de même la règle
  de limitation de débit gratuite de Cloudflare sur l'annuaire (voir `../worker/`).
- Les *quick tunnels* Cloudflare sont documentés comme non destinés à la production :
  pas de garantie de disponibilité. `tunnel.py` est isolé pour pouvoir changer de
  fournisseur (localhost.run, pinggy…) sans toucher au reste.

## Sur macOS

L'application fonctionne aussi sur Mac, avec trois différences à connaître.

**Elle est construite pour Intel**, et tourne donc sur Apple Silicon via Rosetta — que
macOS propose d'installer tout seul au premier lancement. Un unique build couvre ainsi
tout le parc, là où une version Apple Silicon exclurait les Mac Intel.

**Gatekeeper bloque l'application au premier lancement**, faute de signature Apple
(99 $/an). Il faut faire un **clic droit sur l'app puis *Ouvrir***, et confirmer. Une
seule fois. Si macOS la déclare « endommagée », c'est l'attribut de quarantaine posé sur
le fichier téléchargé :

```bash
xattr -dr com.apple.quarantine "/Applications/Moonshop srv.app"
```

**Fermer la fenêtre quitte l'application** et coupe donc le partage, alors que sous
Windows elle continue dans la zone de notification. La raison est technique : pystray
exige le fil principal, déjà occupé par la fenêtre. L'application le dit explicitement
sous le bouton quand un partage est en cours. Le glisser-déposer est également absent —
le bouton *Add files* le remplace.

## Construire l'exe

Le build tourne sur GitHub Actions (`.github/workflows/build-srv.yml`) : l'exe est
récupérable en artifact `MoonshopSrv`, à chaque push touchant `srv/`. Rien à installer
en local.

Pour builder à la main sous Windows :

```bash
pip install -r srv/requirements.txt
```

Puis copie dans `srv/` la police `app/src/main/res/font/tbj_buffy.ttf`, le logo
`app/src/main/res/drawable/logo_moonshop_white.png` et `cloudflared.exe`
([téléchargement](https://github.com/cloudflare/cloudflared/releases/latest)), avant de lancer :

```bash
pyinstaller --noconfirm --clean MoonshopSrv.spec
```

## Développer sans builder

```bash
python srv/moonshop_srv.py
```

L'interface est du HTML (`ui.html`) : la police et le logo y sont injectés en base64 au
démarrage, il n'y a donc aucun fichier à charger au moment du rendu.
