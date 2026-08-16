# -*- coding: utf-8 -*-
"""Icône dans la zone de notification (barre des tâches).

Fermer la fenêtre ne doit pas couper le partage : la console d'en face est peut-être
en plein téléchargement. L'appli se contente donc de se cacher, et cette icône est ce
qui permet de la retrouver — sans elle, un partage tournerait sans aucun moyen visible
de l'arrêter autrement que par le gestionnaire des tâches.
"""

from __future__ import annotations

import threading

import plateforme
import ressources


class Plateau:
    def __init__(self, sur_ouvrir, sur_quitter, journal=None):
        self._sur_ouvrir = sur_ouvrir
        self._sur_quitter = sur_quitter
        self._journal = journal
        self._icone = None

    @property
    def disponible(self) -> bool:
        return self._icone is not None

    def demarrer(self) -> bool:
        """Crée l'icône et la fait vivre dans son propre fil. False si impossible."""
        # Sur macOS, pystray exige que sa boucle tourne sur le fil principal — déjà
        # occupé par la fenêtre. Plutôt que d'échouer à moitié, on renonce
        # franchement : l'appelant fera alors de la croix une vraie sortie.
        if plateforme.EST_MAC:
            if callable(self._journal):
                self._journal("Menu bar icon unavailable on macOS.")
            return False

        try:
            import pystray
            from PIL import Image
        except Exception:
            return False

        try:
            image = Image.open(ressources.chemin("moonshop.ico"))
            menu = pystray.Menu(
                pystray.MenuItem("Open Moonshop srv", self._ouvrir, default=True),
                pystray.MenuItem("Quit", self._quitter),
            )
            self._icone = pystray.Icon("moonshop_srv", image, "Moonshop srv", menu)
            threading.Thread(target=self._icone.run, daemon=True).start()
            return True
        except Exception as erreur:
            if callable(self._journal):
                self._journal(f"Zone de notification indisponible : {erreur}")
            self._icone = None
            return False

    def _ouvrir(self, *_):
        self._sur_ouvrir()

    def _quitter(self, *_):
        self.arreter()
        self._sur_quitter()

    def arreter(self) -> None:
        if self._icone is not None:
            try:
                self._icone.stop()
            except Exception:
                pass
            self._icone = None
