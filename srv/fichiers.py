# -*- coding: utf-8 -*-
"""Gestion du contenu du dossier partagé depuis la fenêtre : parcourir, créer,
renommer, supprimer.

Toutes les opérations sont **confinées au dossier partagé**. Chaque chemin reçu de
l'interface est traité comme non fiable et re-vérifié ici : une faute de frappe ou un
chemin bricolé ne doit jamais pouvoir renommer ou supprimer quoi que ce soit ailleurs
sur le disque.
"""

from __future__ import annotations

import shutil
from pathlib import Path

import catalogue

# Caractères interdits par Windows dans un nom de fichier, plus le séparateur : un nom
# saisi dans l'interface ne doit désigner qu'une entrée, jamais un chemin.
CARACTERES_INTERDITS = set('<>:"/\\|?*')
NOMS_RESERVES = {
    "CON", "PRN", "AUX", "NUL",
    *(f"COM{i}" for i in range(1, 10)),
    *(f"LPT{i}" for i in range(1, 10)),
}


class ErreurFichiers(Exception):
    """Erreur destinée à être affichée telle quelle à l'utilisateur."""


def _resoudre(racine: Path, relatif: str) -> Path:
    """Convertit un chemin relatif venu de l'interface en chemin absolu vérifié."""
    base = racine.resolve()
    try:
        cible = (base / relatif).resolve() if relatif else base
    except OSError as erreur:
        raise ErreurFichiers("Invalid path.") from erreur
    if cible != base and base not in cible.parents:
        raise ErreurFichiers("This path is outside the shared folder.")
    return cible


def _verifier_nom(nom: str) -> str:
    propre = nom.strip().rstrip(". ")
    if not propre:
        raise ErreurFichiers("The name cannot be empty.")
    if any(caractere in CARACTERES_INTERDITS for caractere in propre):
        raise ErreurFichiers('A name cannot contain < > : " / \\ | ? *')
    if propre.upper().split(".")[0] in NOMS_RESERVES:
        raise ErreurFichiers(f'"{propre}" is a name reserved by Windows.')
    return propre


def _taille_lisible(octets: int) -> str:
    valeur = float(octets)
    for unite in ("B", "KB", "MB", "GB"):
        if valeur < 1024 or unite == "GB":
            return f"{valeur:.0f} {unite}" if unite == "B" else f"{valeur:.1f} {unite}"
        valeur /= 1024
    return f"{valeur:.1f} GB"


def _compter_direct(dossier: Path) -> int:
    try:
        return sum(1 for _ in dossier.iterdir())
    except OSError:
        return 0


def lister(racine: str | Path, relatif: str = "", marques: set[str] | None = None) -> dict:
    """Contenu d'un dossier : sous-dossiers d'abord, puis fichiers, par ordre alphabétique."""
    base = Path(racine)
    if not base.is_dir():
        raise ErreurFichiers("The shared folder no longer exists.")
    dossier = _resoudre(base, relatif)
    if not dossier.is_dir():
        raise ErreurFichiers("This folder no longer exists.")

    entrees = []
    for chemin in sorted(dossier.iterdir(), key=lambda c: (not c.is_dir(), c.name.lower())):
        try:
            est_dossier = chemin.is_dir()
            entrees.append(
                {
                    "nom": chemin.name,
                    "chemin": chemin.relative_to(base.resolve()).as_posix(),
                    "dossier": est_dossier,
                    "taille": "" if est_dossier else _taille_lisible(chemin.stat().st_size),
                    # Nombre d'éléments directs, affiché sur la vignette du dossier.
                    # Volontairement non récursif : parcourir toute l'arborescence à
                    # chaque affichage rendrait la navigation poussive sur un gros
                    # dossier de ROMs.
                    "contenu": _compter_direct(chemin) if est_dossier else 0,
                    # Repris du catalogue : une entrée héritant d'un dossier marqué
                    # doit apparaître marquée elle aussi, sinon l'interface affiche
                    # le contraire de ce que la console recevra.
                    "jaquette": not catalogue.sans_jaquette(
                        chemin.relative_to(base.resolve()).as_posix(), marques or set()
                    ),
                }
            )
        except OSError:
            # Fichier disparu ou illisible entre le parcours et la lecture : on
            # l'ignore plutôt que de faire échouer tout l'affichage.
            continue

    courant = dossier.relative_to(base.resolve()).as_posix()
    return {
        "chemin": "" if courant == "." else courant,
        "parent": _parent(courant),
        "entrees": entrees,
    }


def _parent(relatif: str) -> str | None:
    """Chemin du dossier parent, ou None si on est déjà à la racine partagée."""
    if not relatif or relatif == ".":
        return None
    morceaux = relatif.split("/")
    return "/".join(morceaux[:-1])


def creer_dossier(racine: str | Path, relatif: str, nom: str) -> str:
    base = Path(racine)
    parent = _resoudre(base, relatif)
    propre = _verifier_nom(nom)
    cible = parent / propre
    if cible.exists():
        raise ErreurFichiers(f'"{propre}" already exists.')
    try:
        cible.mkdir(parents=False)
    except OSError as erreur:
        raise ErreurFichiers(f"Could not create the folder: {erreur.strerror or erreur}") from erreur
    return cible.relative_to(base.resolve()).as_posix()


def renommer(racine: str | Path, relatif: str, nouveau_nom: str) -> str:
    base = Path(racine)
    cible = _resoudre(base, relatif)
    if cible == base.resolve():
        raise ErreurFichiers("The shared folder itself cannot be renamed here.")
    if not cible.exists():
        raise ErreurFichiers("This item no longer exists.")
    propre = _verifier_nom(nouveau_nom)
    destination = cible.parent / propre
    if destination == cible:
        return relatif
    if destination.exists():
        raise ErreurFichiers(f'"{propre}" already exists.')
    try:
        cible.rename(destination)
    except OSError as erreur:
        raise ErreurFichiers(f"Could not rename: {erreur.strerror or erreur}") from erreur
    return destination.relative_to(base.resolve()).as_posix()


def supprimer(racine: str | Path, relatif: str) -> None:
    """Supprime un fichier, ou un dossier avec tout son contenu.

    Irréversible (pas de corbeille) : l'interface doit demander confirmation avant.
    """
    base = Path(racine)
    cible = _resoudre(base, relatif)
    if cible == base.resolve():
        raise ErreurFichiers("The shared folder itself cannot be deleted here.")
    if not cible.exists():
        raise ErreurFichiers("This item no longer exists.")
    try:
        if cible.is_dir():
            shutil.rmtree(cible)
        else:
            cible.unlink()
    except OSError as erreur:
        raise ErreurFichiers(f"Could not delete: {erreur.strerror or erreur}") from erreur


def _nom_libre(dossier: Path, nom: str) -> Path:
    """Évite d'écraser un fichier existant : « Zelda.iso » devient « Zelda (2).iso »."""
    cible = dossier / nom
    if not cible.exists():
        return cible
    tige, suffixe = (cible.stem, cible.suffix) if cible.suffix else (cible.name, "")
    for numero in range(2, 1000):
        candidat = dossier / f"{tige} ({numero}){suffixe}"
        if not candidat.exists():
            return candidat
    raise ErreurFichiers(f'Too many copies of "{nom}" already.')


def _copier_fichier(source: Path, destination: Path, avancer) -> None:
    """Copie par morceaux plutôt qu'en un bloc : une ROM de plusieurs gigaoctets doit
    pouvoir afficher une progression, et non figer l'interface jusqu'à la fin."""
    taille = source.stat().st_size
    copies = 0
    with open(source, "rb") as entree, open(destination, "wb") as sortie:
        while True:
            morceau = entree.read(4 * 1024 * 1024)
            if not morceau:
                break
            sortie.write(morceau)
            copies += len(morceau)
            avancer(len(morceau), source.name, copies, taille)


def importer(
    racine: str | Path,
    relatif: str,
    sources: list[str],
    progression=None,
) -> dict:
    """Copie des fichiers ou dossiers extérieurs dans le dossier partagé.

    Renvoie le décompte de ce qui a été importé et la liste des échecs : un fichier
    verrouillé ou disparu ne doit pas interrompre l'import des suivants.
    """
    base = Path(racine)
    destination = _resoudre(base, relatif)
    if not destination.is_dir():
        raise ErreurFichiers("The destination folder no longer exists.")

    chemins = [Path(source) for source in sources]
    total = 0
    for chemin in chemins:
        try:
            if chemin.is_dir():
                total += sum(f.stat().st_size for f in chemin.rglob("*") if f.is_file())
            elif chemin.is_file():
                total += chemin.stat().st_size
        except OSError:
            continue

    fait = [0]

    def avancer(octets: int, nom: str, _copies: int, _taille: int) -> None:
        fait[0] += octets
        if progression:
            pourcentage = int(fait[0] * 100 / total) if total else 100
            progression(nom, min(pourcentage, 100))

    importes, echecs = 0, []
    for chemin in chemins:
        try:
            if chemin.is_dir():
                cible = _nom_libre(destination, chemin.name)
                cible.mkdir()
                for element in sorted(chemin.rglob("*")):
                    relatif_element = element.relative_to(chemin)
                    if element.is_dir():
                        (cible / relatif_element).mkdir(exist_ok=True)
                    elif element.is_file():
                        (cible / relatif_element).parent.mkdir(parents=True, exist_ok=True)
                        _copier_fichier(element, cible / relatif_element, avancer)
                importes += 1
            elif chemin.is_file():
                _copier_fichier(chemin, _nom_libre(destination, chemin.name), avancer)
                importes += 1
            else:
                echecs.append(f"{chemin.name}: not found")
        except OSError as erreur:
            echecs.append(f"{chemin.name}: {erreur.strerror or erreur}")

    return {"importes": importes, "echecs": echecs}


def compter_contenu(racine: str | Path, relatif: str) -> int:
    """Nombre d'éléments contenus dans un dossier, pour prévenir avant suppression."""
    base = Path(racine)
    cible = _resoudre(base, relatif)
    if not cible.is_dir():
        return 0
    total = 0
    for _ in cible.rglob("*"):
        total += 1
        if total > 999:
            break
    return total
