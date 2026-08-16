# -*- coding: utf-8 -*-
"""Construction du catalogue.json à partir du dossier partagé.

Convention, volontairement sans configuration : chaque sous-dossier de premier niveau
est une catégorie (une console), et les fichiers qu'il contient sont les jeux. Les
fichiers posés à la racine atterrissent dans « Misc ».

Le format produit est celui qu'attend l'appli Android (clés en français, historiques) :
nom, chemin_serveur, image, description, categorie.
"""

from __future__ import annotations

from pathlib import Path
from urllib.parse import quote

CATEGORIE_PAR_DEFAUT = "Misc"

# Fichiers de travail qui n'ont rien à faire dans un catalogue.
EXTENSIONS_IGNOREES = {".tmp", ".part", ".crdownload", ".!ut", ".lnk"}
NOMS_IGNORES = {"catalogue.json", "desktop.ini", "thumbs.db"}

# Images de jaquette : posées à côté du jeu, même nom de base (Zelda.zip + Zelda.jpg).
EXTENSIONS_IMAGE = (".jpg", ".jpeg", ".png", ".webp")


def _est_publiable(chemin: Path) -> bool:
    if not chemin.is_file():
        return False
    if chemin.name.startswith("."):
        return False
    if chemin.name.lower() in NOMS_IGNORES:
        return False
    if chemin.suffix.lower() in EXTENSIONS_IGNOREES:
        return False
    return True


def _chemin_serveur(racine: Path, fichier: Path) -> str:
    relatif = fichier.relative_to(racine).as_posix()
    return "/" + quote(relatif)


def _jaquette(racine: Path, fichier: Path) -> str | None:
    """Cherche une image portant le même nom de base que le fichier de jeu."""
    for extension in EXTENSIONS_IMAGE:
        candidate = fichier.with_suffix(extension)
        if candidate.exists() and candidate.is_file():
            return _chemin_serveur(racine, candidate)
    return None


def sans_jaquette(chemin_relatif: str, marques: set[str]) -> bool:
    """Vrai si l'entrée, ou l'un de ses dossiers parents, est marquée sans jaquette.

    L'héritage évite d'avoir à marquer un à un les cent fichiers d'un dossier de
    sauvegardes : marquer le dossier suffit.
    """
    if not marques:
        return False
    morceaux = chemin_relatif.split("/")
    return any("/".join(morceaux[:i]) in marques for i in range(1, len(morceaux) + 1))


def construire(dossier: str | Path, marques: set[str] | None = None) -> list[dict]:
    """Parcourt le dossier et renvoie la liste des entrées du catalogue."""
    racine = Path(dossier)
    if not racine.is_dir():
        return []

    entrees: list[dict] = []
    images = set()
    marques = marques or set()

    # Premier passage : repérer les jaquettes, pour ne pas les publier comme des jeux.
    for fichier in racine.rglob("*"):
        if _est_publiable(fichier) and fichier.suffix.lower() in EXTENSIONS_IMAGE:
            jeu_associe = any(
                fichier.with_suffix(extension).exists()
                for extension in (".zip", ".7z", ".iso", ".chd", ".nsp", ".xci", ".apk")
            )
            if jeu_associe:
                images.add(fichier)

    for fichier in sorted(racine.rglob("*"), key=lambda c: c.as_posix().lower()):
        if not _est_publiable(fichier) or fichier in images:
            continue
        relatif = fichier.relative_to(racine)
        categorie = relatif.parts[0] if len(relatif.parts) > 1 else CATEGORIE_PAR_DEFAUT
        entrees.append(
            {
                "nom": fichier.name,
                "chemin_serveur": _chemin_serveur(racine, fichier),
                "image": _jaquette(racine, fichier),
                "description": "",
                "categorie": categorie,
                # Faux : la console n'ira pas chercher d'illustration pour ce
                # fichier. Sans quoi une sauvegarde ou un PDF hérite de la jaquette
                # d'un jeu sans rapport, choisi sur la seule ressemblance du nom.
                "jaquette": not sans_jaquette(relatif.as_posix(), marques),
            }
        )
    return entrees


def compter(dossier: str | Path) -> tuple[int, int]:
    """(nombre de jeux, nombre de catégories) — pour l'affichage dans l'interface."""
    entrees = construire(dossier)
    return len(entrees), len({entree["categorie"] for entree in entrees})
