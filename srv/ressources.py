# -*- coding: utf-8 -*-
"""Accès aux fichiers embarqués (logo, police, cloudflared), en dev comme en .exe."""

from __future__ import annotations

import base64
import sys
from pathlib import Path


def dossier_ressources() -> Path:
    """PyInstaller extrait les ressources dans un dossier temporaire (_MEIPASS)."""
    interne = getattr(sys, "_MEIPASS", None)
    if interne:
        return Path(interne)
    return Path(__file__).resolve().parent


def chemin(nom: str) -> Path:
    return dossier_ressources() / nom


def en_base64(nom: str) -> str:
    """Ressource encodée pour être injectée directement dans le HTML de l'interface
    (data: URI) : aucun fichier à charger depuis le disque au moment du rendu."""
    try:
        return base64.b64encode(chemin(nom).read_bytes()).decode("ascii")
    except OSError:
        return ""
