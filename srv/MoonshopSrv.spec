# -*- mode: python ; coding: utf-8 -*-
"""Recette PyInstaller, commune à Windows et macOS.

Un seul exécutable, sans installation ni runtime à côté. Les ressources (interface,
police, logo, cloudflared) sont embarquées et extraites au lancement dans un dossier
temporaire, que `ressources.py` sait retrouver.

PyInstaller ne sait pas compiler pour un autre système que celui sur lequel il tourne :
chaque plateforme a donc son propre workflow, mais tous deux appellent ce fichier. Le
workflow y dépose au préalable la police, le logo, l'icône et le binaire cloudflared.
"""

import sys

EST_MAC = sys.platform == "darwin"

binaire_tunnel = "cloudflared" if EST_MAC else "cloudflared.exe"
icone = "moonshop.icns" if EST_MAC else "moonshop.ico"

# Modules chargés dynamiquement au démarrage : PyInstaller ne peut pas les deviner
# en lisant le code, et ils diffèrent selon le système.
modules_caches = ["clr_loader"] if not EST_MAC else []
modules_caches += (
    ["webview.platforms.cocoa"]
    if EST_MAC
    else ["webview.platforms.winforms", "webview.platforms.edgechromium", "pystray._win32", "PIL.Image"]
)

ressources_embarquees = [
    ("ui.html", "."),
    ("tbj_buffy.ttf", "."),
    ("logo_moonshop_white.png", "."),
]
# L'icône sert aussi dans la zone de notification sous Windows, pas seulement à l'exe.
if not EST_MAC:
    ressources_embarquees.append(("moonshop.ico", "."))

analyse = Analysis(
    ["moonshop_srv.py"],
    pathex=[],
    binaries=[(binaire_tunnel, ".")],
    datas=ressources_embarquees,
    hiddenimports=modules_caches,
    hookspath=[],
    runtime_hooks=[],
    excludes=["tkinter", "unittest", "pydoc_data"],
    noarchive=False,
)

archive = PYZ(analyse.pure)

exe = EXE(
    archive,
    analyse.scripts,
    analyse.binaries,
    analyse.datas,
    [],
    name="MoonshopSrv",
    debug=False,
    strip=False,
    upx=False,
    runtime_tmpdir=None,
    # Application fenêtrée : aucune console derrière la fenêtre.
    console=False,
    icon=icone,
)

if EST_MAC:
    app = BUNDLE(
        exe,
        name="Moonshop srv.app",
        icon=icone,
        bundle_identifier="com.monshop.srv",
        info_plist={
            "CFBundleName": "Moonshop srv",
            "CFBundleDisplayName": "Moonshop srv",
            "CFBundleShortVersionString": "1.0",
            "NSHighResolutionCapable": True,
            # macOS demande l'autorisation d'accéder au réseau local depuis Sequoia :
            # sans cette explication, la boîte de dialogue reste muette sur le pourquoi.
            "NSLocalNetworkUsageDescription":
                "Moonshop srv shares your games with your console over your local network.",
        },
    )
