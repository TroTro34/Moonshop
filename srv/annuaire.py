# -*- coding: utf-8 -*-
"""Publication de l'adresse courante auprès de l'annuaire Moonshop.

L'annuaire (un Worker Cloudflare, voir le dossier `worker/`) est le seul point fixe du
système : le PC y dépose « code -> URL du moment » à chaque démarrage du partage, et la
console y retrouve l'adresse à jour derrière le code qu'elle a mémorisé une fois pour
toutes. C'est ce qui permet à l'URL du tunnel de changer sans que personne ne s'en aperçoive.

L'entrée expire d'elle-même : un PC éteint disparaît de l'annuaire au lieu de laisser
une adresse morte derrière lui. D'où le battement de cœur tant que le partage tourne.
"""

from __future__ import annotations

import json
import threading
import urllib.error
import urllib.request

# Renseigné à la compilation : c'est TON annuaire, commun à tous les utilisateurs de
# l'appli. Ils n'ont donc aucun compte à créer ni aucune adresse à connaître.
URL_ANNUAIRE = "https://moonshop-annuaire.moonshop-annuaire.workers.dev"

# Clé partagée exigée par l'annuaire en écriture : elle n'a pas vocation à être secrète
# (elle est dans l'exe), elle écarte simplement les écritures automatisées de passage.
CLE_PUBLICATION = "moonshop-srv"

# Message affiché quand l'annuaire refuse l'écriture pour cause de code déjà détenu :
# « HTTP 403 » seul enverrait chercher un problème de réseau.
REFUS_PROPRIETAIRE = "this code belongs to another computer"

# Cloudflare bloque en amont les requêtes portant une signature de bibliothèque
# d'automatisation (« Python-urllib/3.x ») : erreur 1010, la requête n'atteint même
# pas le Worker. L'appli s'identifie donc explicitement.
AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) MoonshopSrv/1.0"

DELAI_RESEAU = 15

# Rythme volontairement lent : le plan gratuit de Cloudflare KV n'autorise que 1 000
# écritures par jour, tous utilisateurs confondus. Un battement toutes les 5 minutes
# épuiserait ce quota avec trois PC allumés en permanence. L'entrée vit donc 24 h côté
# annuaire, et c'est la console qui détecte un PC éteint (l'adresse ne répond plus),
# pas l'annuaire.
PERIODE_BATTEMENT = 6 * 3600


class Annuaire:
    def __init__(self, journal=None, secret: str = ""):
        self._journal = journal
        # Secret propre à cette machine : l'annuaire n'accepte une écriture sur un code
        # que du PC qui l'a publié en premier.
        self._secret = secret
        self._battement: threading.Timer | None = None
        self._code = ""
        self._url = ""
        self._url_locale = ""
        # Raison précise du dernier échec, à afficher à l'utilisateur : « injoignable »
        # tout court l'enverrait chercher un problème de réseau alors que la cause peut
        # être tout autre (requête refusée, service en erreur…).
        self.derniere_erreur: str = ""

    def _tracer(self, message: str) -> None:
        if callable(self._journal):
            self._journal(message)

    def publier(self, code: str, url_publique: str, url_locale: str = "") -> bool:
        """Enregistre l'adresse et entretient l'entrée tant que le partage tourne.

        Renvoie False si l'annuaire est injoignable : le partage fonctionne quand même,
        mais le code ne mène nulle part — l'appelant doit le dire à l'utilisateur au
        lieu d'annoncer que tout va bien.
        """
        self._code = code
        self._url = url_publique
        self._url_locale = url_locale
        publie = self._envoyer()
        self._programmer_battement()
        return publie

    def _envoyer(self) -> bool:
        corps = json.dumps(
            {"code": self._code, "url": self._url, "url_locale": self._url_locale}
        ).encode("utf-8")
        requete = urllib.request.Request(
            f"{URL_ANNUAIRE}/publish",
            data=corps,
            method="POST",
            headers={
                "Content-Type": "application/json",
                "X-Moonshop-Key": CLE_PUBLICATION,
                "X-Moonshop-Secret": self._secret,
                "User-Agent": AGENT,
            },
        )
        try:
            with urllib.request.urlopen(requete, timeout=DELAI_RESEAU) as reponse:
                if reponse.status != 200:
                    self.derniere_erreur = f"directory answered {reponse.status}"
                    self._tracer(f"Annuaire : réponse inattendue ({reponse.status})")
                    return False
                self.derniere_erreur = ""
                return True
        except urllib.error.HTTPError as erreur:
            # Distingué du reste : ici l'annuaire (ou Cloudflare devant lui) a bien
            # répondu, mais a refusé la requête. Le code HTTP est la seule information
            # qui permette de trancher, il doit remonter jusqu'à l'utilisateur.
            self.derniere_erreur = (
                REFUS_PROPRIETAIRE if erreur.code == 403 else f"HTTP {erreur.code}"
            )
            self._tracer(f"Annuaire : requête refusée (HTTP {erreur.code})")
            return False
        except (urllib.error.URLError, OSError) as erreur:
            # Échec non bloquant : le partage fonctionne toujours pour qui connaît
            # déjà l'adresse, et le battement suivant retentera.
            self.derniere_erreur = str(getattr(erreur, "reason", erreur))
            self._tracer(f"Annuaire injoignable : {erreur}")
            return False

    def _programmer_battement(self) -> None:
        self._annuler_battement()
        self._battement = threading.Timer(PERIODE_BATTEMENT, self._battre)
        self._battement.daemon = True
        self._battement.start()

    def _battre(self) -> None:
        if not self._code or not self._url:
            return
        self._envoyer()
        self._programmer_battement()

    def _annuler_battement(self) -> None:
        if self._battement is not None:
            self._battement.cancel()
            self._battement = None

    def retirer(self) -> None:
        """Partage arrêté : on retire l'entrée pour que la console dise « offline »
        tout de suite, sans attendre l'expiration."""
        self._annuler_battement()
        if not self._code:
            return
        corps = json.dumps({"code": self._code}).encode("utf-8")
        requete = urllib.request.Request(
            f"{URL_ANNUAIRE}/retirer",
            data=corps,
            method="POST",
            headers={
                "Content-Type": "application/json",
                "X-Moonshop-Key": CLE_PUBLICATION,
                "X-Moonshop-Secret": self._secret,
                "User-Agent": AGENT,
            },
        )
        try:
            urllib.request.urlopen(requete, timeout=DELAI_RESEAU).close()
        except (urllib.error.URLError, OSError):
            pass
        self._code = ""
        self._url = ""
