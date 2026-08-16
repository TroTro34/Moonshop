# -*- coding: utf-8 -*-
"""Tunnel Cloudflare embarqué : rend le serveur local joignable depuis n'importe où.

cloudflared est lancé en « quick tunnel » : aucune configuration, aucun compte, aucune
ouverture de port sur la box. Il ouvre une connexion *sortante* vers Cloudflare, ce qui
fonctionne aussi derrière un CGNAT (4G) des deux côtés.

Contrepartie assumée : l'URL est aléatoire et change à chaque démarrage — c'est
exactement ce que l'annuaire est là pour masquer.
"""

from __future__ import annotations

import re
import subprocess
import threading

import plateforme
import ressources

MOTIF_URL = re.compile(r"https://[a-z0-9][a-z0-9-]*\.trycloudflare\.com")
DELAI_URL_SECONDES = 45

# Empêche la fenêtre de console noire de clignoter au lancement.
_SANS_FENETRE = getattr(subprocess, "CREATE_NO_WINDOW", 0)


class Tunnel:
    def __init__(self, journal=None):
        self._processus: subprocess.Popen | None = None
        self._journal = journal
        self._url: str | None = None
        self._url_trouvee = threading.Event()

    @property
    def url(self) -> str | None:
        return self._url

    @property
    def actif(self) -> bool:
        return self._processus is not None and self._processus.poll() is None

    def _tracer(self, message: str) -> None:
        if callable(self._journal):
            self._journal(message)

    def demarrer(self, port: int) -> str:
        """Lance cloudflared et attend l'URL publique. Lève une erreur si elle n'arrive pas."""
        self.arreter()
        self._url = None
        self._url_trouvee.clear()

        binaire = ressources.chemin(plateforme.nom_cloudflared())
        if not binaire.exists():
            raise FileNotFoundError(
                "cloudflared is missing from the application."
            )
        plateforme.rendre_executable(binaire)

        self._processus = subprocess.Popen(
            [
                str(binaire),
                "tunnel",
                "--url",
                f"http://127.0.0.1:{port}",
                "--no-autoupdate",
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
            creationflags=_SANS_FENETRE,
        )
        threading.Thread(target=self._lire_sortie, daemon=True).start()

        if not self._url_trouvee.wait(DELAI_URL_SECONDES):
            self.arreter()
            raise TimeoutError(
                "Cloudflare n'a pas répondu à temps. Vérifie ta connexion Internet."
            )
        return self._url  # type: ignore[return-value]

    def _lire_sortie(self) -> None:
        """cloudflared annonce l'URL dans son flux de sortie, entourée d'un cadre."""
        processus = self._processus
        if processus is None or processus.stdout is None:
            return
        for ligne in processus.stdout:
            if self._url is None:
                trouvee = MOTIF_URL.search(ligne)
                if trouvee:
                    self._url = trouvee.group(0)
                    self._tracer(f"Tunnel ouvert : {self._url}")
                    self._url_trouvee.set()
        # Sortie du processus : si l'URL n'était pas encore trouvée, on débloque
        # l'attente pour signaler l'échec au lieu de patienter jusqu'au délai.
        self._url_trouvee.set()

    def arreter(self) -> None:
        processus = self._processus
        self._processus = None
        self._url = None
        if processus is None:
            return
        try:
            processus.terminate()
            processus.wait(timeout=5)
        except (OSError, subprocess.TimeoutExpired):
            try:
                processus.kill()
            except OSError:
                pass
