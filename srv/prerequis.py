# -*- coding: utf-8 -*-
"""Vérification du composant WebView2, et installation en un clic s'il manque.

L'interface de Moonshop srv est du HTML affiché par WebView2, le moteur d'Edge. Il est
préinstallé sur Windows 11 et arrive par les mises à jour sur Windows 10, mais une
machine ancienne ou jamais mise à jour peut en être dépourvue. Dans ce cas, l'exe se
lance et **aucune fenêtre n'apparaît** : ni erreur, ni explication. C'est la pire chose
qui puisse arriver à quelqu'un qui découvre l'application, et c'est la seule étape
technique qui pourrait subsister pour un utilisateur ordinaire.

D'où cette vérification, faite avant même de créer la fenêtre, avec des boîtes de
dialogue Windows natives : elles fonctionnent quand rien d'autre ne fonctionne encore.
"""

from __future__ import annotations

import ctypes
import os
import subprocess
import tempfile
import urllib.request

import plateforme

# Identifiant du « WebView2 Runtime » dans la base de registre, publié par Microsoft.
GUID_WEBVIEW2 = "{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}"
URL_INSTALLATEUR = "https://go.microsoft.com/fwlink/p/?LinkId=2124703"

_OUI_NON = 0x00000004
_ICONE_QUESTION = 0x00000020
_ICONE_ERREUR = 0x00000010
_REPONSE_OUI = 6


def _dialogue(message: str, titre: str, drapeaux: int) -> int:
    try:
        return ctypes.windll.user32.MessageBoxW(None, message, titre, drapeaux)
    except Exception:
        return 0


def present() -> bool:
    """Vrai si WebView2 est installé (pour la machine ou pour l'utilisateur)."""
    if not plateforme.EST_WINDOWS:
        return True
    try:
        import winreg
    except ImportError:
        return True

    emplacements = [
        (winreg.HKEY_LOCAL_MACHINE, rf"SOFTWARE\WOW6432Node\Microsoft\EdgeUpdate\Clients\{GUID_WEBVIEW2}"),
        (winreg.HKEY_LOCAL_MACHINE, rf"SOFTWARE\Microsoft\EdgeUpdate\Clients\{GUID_WEBVIEW2}"),
        (winreg.HKEY_CURRENT_USER, rf"Software\Microsoft\EdgeUpdate\Clients\{GUID_WEBVIEW2}"),
    ]
    for racine, chemin in emplacements:
        try:
            with winreg.OpenKey(racine, chemin) as cle:
                version, _ = winreg.QueryValueEx(cle, "pv")
                # Une clé présente mais vide signifie « désinstallé » chez Microsoft.
                if version and version != "0.0.0.0":
                    return True
        except OSError:
            continue
    return False


def _installer() -> bool:
    """Télécharge l'installateur officiel et le lance en mode silencieux."""
    try:
        cible = os.path.join(tempfile.gettempdir(), "MicrosoftEdgeWebview2Setup.exe")
        with urllib.request.urlopen(URL_INSTALLATEUR, timeout=60) as reponse:
            with open(cible, "wb") as fichier:
                fichier.write(reponse.read())
        # /silent /install : l'utilisateur n'a que l'autorisation Windows à accorder.
        acheve = subprocess.run(
            [cible, "/silent", "/install"],
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
            timeout=600,
        )
        return acheve.returncode == 0
    except Exception:
        return False


def assurer_webview2() -> bool:
    """Renvoie True si l'application peut afficher sa fenêtre, False s'il faut renoncer."""
    if present():
        return True

    reponse = _dialogue(
        "Moonshop srv needs the Microsoft WebView2 component to show its window.\n\n"
        "It is free, published by Microsoft, and takes about a minute to install.\n\n"
        "Install it now?",
        "Moonshop srv",
        _OUI_NON | _ICONE_QUESTION,
    )
    if reponse != _REPONSE_OUI:
        return False

    if _installer() and present():
        return True

    _dialogue(
        "The component could not be installed automatically.\n\n"
        "Download it manually from:\n"
        "https://developer.microsoft.com/microsoft-edge/webview2/\n\n"
        "Choose the Evergreen Bootstrapper, then start Moonshop srv again.",
        "Moonshop srv",
        _ICONE_ERREUR,
    )
    return False
