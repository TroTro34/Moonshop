# -*- coding: utf-8 -*-
"""Extrait le JavaScript de l'interface pour qu'un vérificateur puisse le relire.

Une erreur de syntaxe dans ce script n'empêche pas la fenêtre de s'afficher : le HTML
et les styles se chargent normalement, et seul le comportement disparaît — aucun bouton
ne répond, aucun état ne s'affiche. Le défaut est donc invisible tant qu'on regarde une
capture d'écran, et c'est exactement pour cela qu'il doit être détecté à la compilation.
"""
import pathlib
import re
import sys

source = pathlib.Path(__file__).with_name("ui.html").read_text(encoding="utf-8")
blocs = re.findall(r"<script[^>]*>(.*?)</script>", source, re.S)
if not blocs:
    print("Aucun bloc <script> dans ui.html", file=sys.stderr)
    sys.exit(1)

sortie = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "ui_script.js")
sortie.write_text("\n".join(blocs), encoding="utf-8")
print(f"{len(blocs)} bloc(s) extrait(s) vers {sortie}")
