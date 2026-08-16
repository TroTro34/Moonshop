# -*- coding: utf-8 -*-
"""Consoles autorisées à télécharger depuis ce PC.

Le code à six caractères se retient et se recopie : une capture d'écran, un partage
d'écran, un ami bavard, et il circule. Tant qu'il suffisait à télécharger, le voir
équivalait à l'avoir.

Ici il ne fait plus qu'ouvrir une porte : il permet de *demander* l'accès. C'est
l'utilisateur, devant son PC, qui accepte — et la console reçoit alors un jeton qui
n'appartient qu'à elle, révocable séparément des autres. Un code vu par un tiers ne
sert plus à rien sans quelqu'un pour cliquer.
"""

from __future__ import annotations

import threading
import time

import reglages as module_reglages

# Une demande non traitée finit par disparaître : sans cela, la fenêtre se remplirait
# des tentatives d'un inconnu qui balaye des codes.
DUREE_DEMANDE = 300

# Au-delà, les plus anciennes sont oubliées — même raison.
MAX_DEMANDES = 8


class Appairage:
    """Registre des consoles connues, partagé entre le serveur HTTP et l'interface.

    Les requêtes arrivent sur les fils du serveur pendant que l'utilisateur clique dans
    la fenêtre : tout passe donc par un verrou.
    """

    def __init__(self, reglages: dict, sauvegarder, sur_changement=None):
        self._reglages = reglages
        self._sauvegarder = sauvegarder
        self._sur_changement = sur_changement
        self._verrou = threading.RLock()
        # {identifiant: {"nom", "jeton", "vu"}}
        self._appareils: dict = dict(reglages.get("appareils") or {})
        # {identifiant: {"nom", "statut", "jeton", "depuis"}}
        self._demandes: dict = {}

    # ------------------------------------------------------------------ interne

    def _prevenir(self) -> None:
        if callable(self._sur_changement):
            self._sur_changement()

    def _persister(self) -> None:
        self._reglages["appareils"] = self._appareils
        self._sauvegarder(self._reglages)

    def _purger(self) -> None:
        """Oublie les demandes trop vieilles ou trop nombreuses."""
        maintenant = time.time()
        for identifiant, demande in list(self._demandes.items()):
            if demande["statut"] == "en_attente" and maintenant - demande["depuis"] > DUREE_DEMANDE:
                del self._demandes[identifiant]
        if len(self._demandes) > MAX_DEMANDES:
            surplus = sorted(self._demandes.items(), key=lambda e: e[1]["depuis"])
            for identifiant, _ in surplus[: len(self._demandes) - MAX_DEMANDES]:
                del self._demandes[identifiant]

    # ------------------------------------------------------- côté serveur HTTP

    def demander(self, identifiant: str, nom: str) -> dict:
        """Une console se présente. Renvoie son état, sans jamais bloquer.

        Une console déjà connue repart avec son jeton : réinstaller l'appli sur la
        console ne doit pas obliger à ressortir de la partie pour cliquer.
        """
        identifiant = (identifiant or "").strip()[:64]
        nom = (nom or "Console").strip()[:48] or "Console"
        if not identifiant:
            return {"statut": "refuse"}

        with self._verrou:
            connu = self._appareils.get(identifiant)
            if connu:
                connu["vu"] = time.time()
                connu["nom"] = nom
                self._persister()
                return {"statut": "accepte", "jeton_appareil": connu["jeton"]}

            self._purger()
            demande = self._demandes.get(identifiant)
            if demande is None:
                demande = {"nom": nom, "statut": "en_attente", "jeton": "", "depuis": time.time()}
                self._demandes[identifiant] = demande
                self._prevenir()
            elif demande["statut"] == "en_attente":
                demande["nom"] = nom
            return self._etat(identifiant, demande)

    def etat_demande(self, identifiant: str) -> dict:
        with self._verrou:
            connu = self._appareils.get(identifiant)
            if connu:
                return {"statut": "accepte", "jeton_appareil": connu["jeton"]}
            demande = self._demandes.get(identifiant)
            if demande is None:
                # Demande oubliée (expirée, ou PC redémarré) : la console la refera.
                return {"statut": "inconnu"}
            return self._etat(identifiant, demande)

    def _etat(self, identifiant: str, demande: dict) -> dict:
        if demande["statut"] == "accepte":
            return {"statut": "accepte", "jeton_appareil": demande["jeton"]}
        return {"statut": demande["statut"]}

    def jeton_valide(self, jeton: str) -> bool:
        if not jeton:
            return False
        with self._verrou:
            for appareil in self._appareils.values():
                if appareil.get("jeton") == jeton:
                    appareil["vu"] = time.time()
                    return True
        return False

    # ------------------------------------------------------------ côté interface

    def demandes_en_attente(self) -> list:
        with self._verrou:
            self._purger()
            return [
                {"id": identifiant, "nom": demande["nom"]}
                for identifiant, demande in self._demandes.items()
                if demande["statut"] == "en_attente"
            ]

    def liste_appareils(self) -> list:
        with self._verrou:
            return [
                {"id": identifiant, "nom": appareil.get("nom", "Console"), "vu": appareil.get("vu", 0)}
                for identifiant, appareil in sorted(
                    self._appareils.items(), key=lambda e: -e[1].get("vu", 0)
                )
            ]

    def accepter(self, identifiant: str) -> bool:
        with self._verrou:
            demande = self._demandes.get(identifiant)
            if demande is None or demande["statut"] != "en_attente":
                return False
            jeton = module_reglages.generer_jeton_appareil()
            demande["statut"] = "accepte"
            demande["jeton"] = jeton
            self._appareils[identifiant] = {
                "nom": demande["nom"],
                "jeton": jeton,
                "vu": time.time(),
            }
            self._persister()
        self._prevenir()
        return True

    def refuser(self, identifiant: str) -> bool:
        with self._verrou:
            demande = self._demandes.get(identifiant)
            if demande is None:
                return False
            # Conservée un instant avec le statut « refuse » plutôt que supprimée :
            # la console qui interroge doit pouvoir l'apprendre et cesser d'attendre.
            demande["statut"] = "refuse"
        self._prevenir()
        return True

    def revoquer(self, identifiant: str) -> bool:
        with self._verrou:
            if identifiant not in self._appareils:
                return False
            del self._appareils[identifiant]
            self._demandes.pop(identifiant, None)
            self._persister()
        self._prevenir()
        return True
