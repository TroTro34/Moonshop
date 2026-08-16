# Annuaire Moonshop

Le seul point fixe du système. Moonshop srv y dépose « code → adresse du tunnel du
moment », l'appli Android y retrouve l'adresse à jour derrière le code mémorisé.

**Il est déployé une fois, par toi.** Les utilisateurs de l'appli ne créent aucun compte,
ne configurent rien, et n'ont même pas connaissance de ce service.

## Déploiement (gratuit, une seule fois)

Il faut un compte Cloudflare gratuit (sans carte bancaire) et Node.js.

Installer wrangler **localement** d'abord : appelé directement via `npx`, il est
retéléchargé à chaque commande, et son cache se corrompt facilement sous Windows
(`EBUSY` sur `workerd`, verrouillé par un antivirus) — au point que les commandes
finissent par ne plus rien exécuter du tout.

```bash
cd worker
npm install
npx wrangler login
npx wrangler whoami
npx wrangler kv namespace create ANNUAIRE
```

`whoami` doit afficher ton compte : c'est le point de contrôle avant d'aller plus loin.

Recopie l'identifiant renvoyé dans `wrangler.toml`, puis :

```bash
npx wrangler deploy
```

Wrangler affiche l'adresse du service, du genre
`https://moonshop-annuaire.<ton-compte>.workers.dev`.

## Instance en service

L'annuaire est déployé et câblé des deux côtés :

```
https://moonshop-annuaire.moonshop-annuaire.workers.dev
```

- `srv/annuaire.py` → `URL_ANNUAIRE`
- `app/.../Annuaire.kt` → `AnnuaireConfig.URL_BASE`

En cas de redéploiement sur un autre compte, ce sont les deux seules lignes à changer.

## Limites du plan gratuit

L'annuaire stocke dans Cloudflare KV : 100 000 lectures par jour, mais seulement
**1 000 écritures**. Une écriture correspond à un *Launch* (l'entrée vit ensuite 24 h),
donc le quota tient largement — c'est pour cette raison que le PC ne republie pas son
adresse en boucle. C'est la console qui détecte un PC éteint, en constatant que
l'adresse ne répond plus.

## Limitation de débit (recommandé)

Un code fait six caractères, soit environ un milliard de combinaisons : le forcer
brutalement est irréaliste, mais un attaquant qui essaie en masse consommerait le quota
gratuit et mettrait le service hors service pour tout le monde. Dans le tableau de bord
Cloudflare, *Security > WAF > Rate limiting rules*, une règle gratuite suffit :

> chemin contient `/resolve` → plus de 20 requêtes/minute par IP → bloquer 1 minute

## Points d'entrée

| Méthode | Route | Qui l'appelle |
|---|---|---|
| `POST` | `/publish` `{code, url}` | Moonshop srv, à chaque Launch |
| `POST` | `/retirer` `{code}` | Moonshop srv, à l'arrêt du partage |
| `GET` | `/resolve?code=XXXXXX` | L'appli Android, à chaque ouverture |

Les deux routes d'écriture exigent l'en-tête `X-Moonshop-Key`, dont la valeur doit
correspondre à `CLE_PUBLICATION` dans `srv/annuaire.py`.
