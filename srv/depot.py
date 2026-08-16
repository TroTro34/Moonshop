# -*- coding: utf-8 -*-
"""Glisser-déposer de fichiers depuis l'Explorateur vers la fenêtre.

Pourquoi passer par la fenêtre native plutôt que par l'événement `drop` du HTML :
dans une WebView, les fichiers déposés arrivent sous forme d'objets `File` **sans
chemin sur le disque** (le bac à sable du navigateur l'interdit). Impossible donc de
copier une ROM de 4 Go depuis le JavaScript : il faudrait la lire entièrement en
mémoire. La fenêtre WinForms qui héberge la WebView, elle, reçoit les vrais chemins.

Deux conditions pour que le dépôt lui parvienne : `AllowDrop` sur le formulaire, et
`AllowExternalDrop = False` sur le contrôle WebView2, sans quoi ce dernier intercepte
le dépôt et ne le transmet jamais.

Tout est enveloppé : si l'une de ces pièces manque ou change de nom dans une future
version de pywebview, le glisser-déposer est simplement absent — l'import par le
bouton, lui, continue de fonctionner.
"""

from __future__ import annotations


def activer(fenetre_native, sur_depot) -> bool:
    """Branche le dépôt sur la fenêtre. Renvoie False si ce n'est pas possible."""
    try:
        from System.Windows.Forms import DataFormats, DragDropEffects  # type: ignore
    except Exception:
        return False

    try:
        _laisser_passer_webview(fenetre_native)

        def sur_entree(_expediteur, evenement):
            if evenement.Data.GetDataPresent(DataFormats.FileDrop):
                evenement.Effect = DragDropEffects.Copy

        def sur_lache(_expediteur, evenement):
            if not evenement.Data.GetDataPresent(DataFormats.FileDrop):
                return
            chemins = [str(c) for c in evenement.Data.GetData(DataFormats.FileDrop)]
            if chemins:
                sur_depot(chemins)

        fenetre_native.AllowDrop = True
        fenetre_native.DragEnter += sur_entree
        fenetre_native.DragOver += sur_entree
        fenetre_native.DragDrop += sur_lache
        return True
    except Exception:
        return False


def _laisser_passer_webview(conteneur) -> None:
    """Désactive la gestion du dépôt par WebView2, à tous les niveaux de la fenêtre."""
    try:
        controles = list(conteneur.Controls)
    except Exception:
        return
    for controle in controles:
        try:
            if hasattr(controle, "AllowExternalDrop"):
                controle.AllowExternalDrop = False
        except Exception:
            pass
        _laisser_passer_webview(controle)
