# -*- coding: utf-8 -*-
"""Ce qui diffère entre Windows et macOS.

Regroupé ici plutôt que dispersé en tests `if sys.platform` : le reste du code n'a
ainsi jamais à savoir sur quel système il tourne.
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

EST_WINDOWS = sys.platform.startswith("win")
EST_MAC = sys.platform == "darwin"


def nom_cloudflared() -> str:
    """Sur Windows le binaire porte une extension, ailleurs non."""
    return "cloudflared.exe" if EST_WINDOWS else "cloudflared"


def dossier_config() -> Path:
    """Emplacement conventionnel des réglages, propre à chaque système."""
    if EST_WINDOWS:
        base = os.environ.get("APPDATA")
        if base:
            return Path(base) / "MoonshopSrv"
    elif EST_MAC:
        return Path.home() / "Library" / "Application Support" / "MoonshopSrv"
    else:
        base = os.environ.get("XDG_CONFIG_HOME")
        if base:
            return Path(base) / "moonshop-srv"
        return Path.home() / ".config" / "moonshop-srv"
    return Path.home() / "MoonshopSrv"


def rendre_executable(chemin: Path) -> None:
    """Restaure le bit d'exécution, perdu à l'extraction sur les systèmes Unix.

    Sans lui, cloudflared est présent mais refuse de démarrer — et le message
    d'erreur, « permission refusée », n'oriente pas vers la bonne cause.
    """
    if EST_WINDOWS:
        return
    try:
        mode = chemin.stat().st_mode
        chemin.chmod(mode | 0o111)
    except OSError:
        pass
