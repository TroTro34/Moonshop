# -*- coding: utf-8 -*-
"""Réglages persistés de Moonshop srv (dossier partagé, code d'appairage)."""

from __future__ import annotations

import json
import secrets

import plateforme

# Alphabet volontairement amputé de I, O, 0, 1 : le code se tape au clavier virtuel
# d'une console, où confondre O et 0 est la première source d'erreur.
ALPHABET_CODE = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
LONGUEUR_CODE = 6

DOSSIER_CONFIG = plateforme.dossier_config()
FICHIER_CONFIG = DOSSIER_CONFIG / "config.json"


def _defauts() -> dict:
    return {
        "dossier": "",
        "code": "",
        "port": 8765,
        "sans_jaquette": [],
        # Prouve à l'annuaire que ce PC est bien celui qui a publié ce code. Ne quitte
        # jamais la machine : seule son empreinte est stockée côté annuaire.
        "secret_publication": "",
        # Consoles autorisées : {identifiant: {"nom", "jeton", "vu"}}.
        "appareils": {},
        # Tunnel Cloudflare. Coupé, le partage ne sort plus du réseau local : plus
        # rien ne transite par un tiers, au prix de l'accès à distance.
        "tunnel_actif": True,
    }


def charger() -> dict:
    """Lit la config, en complétant les clés manquantes par leurs valeurs par défaut."""
    reglages = _defauts()
    try:
        with open(FICHIER_CONFIG, encoding="utf-8") as fichier:
            reglages.update(json.load(fichier))
    except (OSError, ValueError):
        pass
    # Le code est généré une seule fois puis ne change jamais : c'est lui que
    # l'utilisateur a tapé sur sa console, le réémettre le déconnecterait.
    modifie = False
    if not reglages.get("code"):
        reglages["code"] = generer_code()
        modifie = True
    # Tiré une seule fois, à la première ouverture : c'est lui qui rattache le code à
    # cette machine. Le régénérer reviendrait à abandonner le code à qui le réclame.
    if not reglages.get("secret_publication"):
        reglages["secret_publication"] = generer_secret()
        modifie = True
    if modifie:
        sauvegarder(reglages)
    return reglages


def sauvegarder(reglages: dict) -> None:
    try:
        DOSSIER_CONFIG.mkdir(parents=True, exist_ok=True)
        with open(FICHIER_CONFIG, "w", encoding="utf-8") as fichier:
            json.dump(reglages, fichier, indent=2)
    except OSError:
        # Config non enregistrable (dossier en lecture seule) : l'appli reste
        # utilisable pour la session en cours, avec un code régénéré au prochain
        # démarrage. Mieux que de refuser de se lancer.
        pass


def generer_code() -> str:
    return "".join(secrets.choice(ALPHABET_CODE) for _ in range(LONGUEUR_CODE))


def generer_secret() -> str:
    """Secret de publication propre à ce PC, jamais transmis à la console."""
    return secrets.token_urlsafe(32)


def generer_jeton_appareil() -> str:
    """Jeton remis à une console une fois l'appairage accepté."""
    return secrets.token_urlsafe(24)


def generer_jeton() -> str:
    """Segment secret placé en tête des URL : sans lui, l'URL du tunnel seule ne
    donne accès à rien. Régénéré à chaque démarrage du partage."""
    return secrets.token_urlsafe(18)
