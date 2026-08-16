# -*- coding: utf-8 -*-
"""Serveur HTTP local qui expose le dossier partagé à l'appli Android.

Tout est servi derrière un segment secret (« jeton ») régénéré à chaque démarrage :
https://<tunnel>/<jeton>/catalogue.json
https://<tunnel>/<jeton>/GameCube/Zelda.iso

L'URL du tunnel seule ne donne donc accès à rien, et couper puis relancer le partage
invalide immédiatement les anciennes adresses.

Le jeton d'URL ne suffit cependant pas à télécharger : il faut en plus présenter un
jeton d'appareil, remis à la console le jour où l'utilisateur a accepté sa demande
devant son PC. Seules deux routes s'en passent, celles qui servent justement à demander
cette autorisation.
"""

from __future__ import annotations

import json
import mimetypes
import socket
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse

import catalogue

TAILLE_MORCEAU = 64 * 1024


class _Gestionnaire(BaseHTTPRequestHandler):
    # Renseignés par ServeurFichiers avant le démarrage.
    racine: Path = Path(".")
    jeton: str = ""
    journal = None
    # Registre des consoles autorisées, partagé avec l'interface.
    appairage = None
    # Fonction plutôt que valeur : les marques changent pendant que le serveur
    # tourne, au gré des bascules dans la fenêtre.
    marques = None

    protocol_version = "HTTP/1.1"
    server_version = "MoonshopSrv"
    sys_version = ""

    def log_message(self, format_, *args):
        # Le journal par défaut écrit sur stderr, invisible dans un .exe fenêtré.
        if callable(type(self).journal):
            type(self).journal(format_ % args)

    def _refuser(self, code: int, message: str = "") -> None:
        corps = message.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(corps)))
        self.end_headers()
        try:
            self.wfile.write(corps)
        except OSError:
            pass

    def _chemin_demande(self) -> str | None:
        """Valide le jeton et renvoie le chemin relatif demandé, ou None si refusé."""
        chemin = unquote(urlparse(self.path).path)
        prefixe = "/" + type(self).jeton
        if chemin == prefixe:
            return ""
        if not chemin.startswith(prefixe + "/"):
            return None
        return chemin[len(prefixe) + 1:]

    def _resoudre(self, relatif: str) -> Path | None:
        """Empêche toute sortie du dossier partagé (../, liens, chemins absolus)."""
        racine = type(self).racine.resolve()
        try:
            cible = (racine / relatif).resolve()
        except OSError:
            return None
        if cible != racine and racine not in cible.parents:
            return None
        return cible

    def _repondre_json(self, donnees: dict, code: int = 200) -> None:
        corps = json.dumps(donnees).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(corps)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        try:
            self.wfile.write(corps)
        except OSError:
            pass

    def _autorise(self) -> bool:
        """Vrai si la requête présente le jeton d'une console acceptée."""
        registre = type(self).appairage
        if registre is None:
            # Pas de registre : configuration incomplète, on n'ouvre rien.
            return False
        return registre.jeton_valide(self.headers.get("X-Moonshop-Appareil", ""))

    def do_GET(self):  # noqa: N802 (nom imposé par BaseHTTPRequestHandler)
        relatif = self._chemin_demande()
        if relatif == "appairage/etat":
            self._etat_appairage()
            return
        self._servir(avec_corps=True)

    def do_HEAD(self):  # noqa: N802
        self._servir(avec_corps=False)

    def do_POST(self):  # noqa: N802
        # Le corps doit être lu quoi qu'il advienne : le laisser dans le tampon
        # désynchronise la connexion persistante et fait échouer la requête suivante.
        try:
            taille = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            taille = 0
        corps = self.rfile.read(taille) if taille > 0 else b""

        if self._chemin_demande() != "appairage":
            self._refuser(404, "Not found")
            return

        registre = type(self).appairage
        if registre is None:
            self._refuser(503, "Pairing unavailable")
            return
        try:
            demande = json.loads(corps.decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            self._refuser(400, "Bad request")
            return

        reponse = registre.demander(str(demande.get("id", "")), str(demande.get("nom", "")))
        # 202 tant que personne n'a cliqué : la console sait qu'elle doit patienter
        # plutôt que de conclure à une panne.
        self._repondre_json(reponse, 200 if reponse.get("statut") == "accepte" else 202)

    def _etat_appairage(self) -> None:
        registre = type(self).appairage
        if registre is None:
            self._refuser(503, "Pairing unavailable")
            return
        parametres = parse_qs(urlparse(self.path).query)
        identifiant = (parametres.get("id") or [""])[0]
        reponse = registre.etat_demande(identifiant)
        self._repondre_json(reponse)

    def _servir(self, avec_corps: bool) -> None:
        relatif = self._chemin_demande()
        if relatif is None:
            # Volontairement indistinct d'un fichier absent : une URL de tunnel
            # trouvée au hasard n'apprend rien sur ce qui est partagé.
            self._refuser(404, "Not found")
            return

        # Le jeton d'URL a seulement mené jusqu'ici ; c'est l'accord donné devant le
        # PC qui ouvre les fichiers. Réponse distincte du 404 : la console doit
        # pouvoir distinguer « pas autorisée » de « rien à cet endroit », sinon elle
        # ne saurait pas qu'il lui faut demander l'appairage.
        if not self._autorise():
            self._repondre_json({"erreur": "appairage requis"}, 401)
            return

        if relatif in ("", "catalogue.json"):
            self._servir_catalogue(avec_corps)
            return

        cible = self._resoudre(relatif)
        if cible is None or not cible.is_file():
            self._refuser(404, "Not found")
            return
        self._servir_fichier(cible, avec_corps)

    def _servir_catalogue(self, avec_corps: bool) -> None:
        fournisseur = type(self).marques
        entrees = catalogue.construire(
            type(self).racine, fournisseur() if callable(fournisseur) else None
        )
        corps = json.dumps(entrees, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(corps)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        if avec_corps:
            try:
                self.wfile.write(corps)
            except OSError:
                pass

    def _servir_fichier(self, cible: Path, avec_corps: bool) -> None:
        try:
            taille = cible.stat().st_size
        except OSError:
            self._refuser(404, "Not found")
            return

        type_mime = mimetypes.guess_type(cible.name)[0] or "application/octet-stream"
        self.send_response(200)
        self.send_header("Content-Type", type_mime)
        # Content-Length explicite : c'est lui qui alimente la barre de progression
        # et l'estimation du temps restant côté console.
        self.send_header("Content-Length", str(taille))
        self.send_header("Accept-Ranges", "none")
        self.end_headers()
        if not avec_corps:
            return
        try:
            with open(cible, "rb") as fichier:
                while True:
                    morceau = fichier.read(TAILLE_MORCEAU)
                    if not morceau:
                        break
                    self.wfile.write(morceau)
        except OSError:
            # Téléchargement interrompu côté console : rien à signaler.
            pass


def adresse_locale() -> str:
    """IP de la machine sur son réseau local (celle que voit la console sur le wifi).

    Passe par une socket UDP « connectée » : aucun paquet n'est envoyé, mais le système
    choisit l'interface qu'il utiliserait pour sortir, ce qui donne la bonne IP même
    avec plusieurs cartes réseau (wifi + ethernet + machines virtuelles).
    """
    prise = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        prise.connect(("8.8.8.8", 80))
        return prise.getsockname()[0]
    except OSError:
        return ""
    finally:
        prise.close()


class ServeurFichiers:
    """Cycle de vie du serveur HTTP local, démarré dans son propre fil."""

    def __init__(self, journal=None):
        self._serveur: ThreadingHTTPServer | None = None
        self._fil: threading.Thread | None = None
        self._journal = journal

    @property
    def actif(self) -> bool:
        return self._serveur is not None

    def demarrer(self, dossier: str, jeton: str, port: int, marques=None, appairage=None) -> int:
        """Démarre le serveur et renvoie le port réellement utilisé."""
        self.arreter()

        gestionnaire = type("GestionnaireLie", (_Gestionnaire,), {})
        gestionnaire.racine = Path(dossier)
        gestionnaire.jeton = jeton
        gestionnaire.journal = self._journal
        gestionnaire.marques = marques
        gestionnaire.appairage = appairage

        # Écoute sur toutes les interfaces, et non sur la seule boucle locale : c'est
        # ce qui permet à la console de télécharger en direct quand elle est sur le
        # même wifi, sans faire transiter les fichiers par Cloudflare. Le jeton secret
        # reste exigé, y compris sur le réseau local.
        # Port 0 en secours : si le port habituel est déjà pris, on en prend un libre
        # plutôt que de refuser de démarrer (le tunnel s'adapte tout seul).
        for tentative in (port, 0):
            try:
                self._serveur = ThreadingHTTPServer(("0.0.0.0", tentative), gestionnaire)
                break
            except OSError:
                self._serveur = None
        if self._serveur is None:
            raise OSError("Aucun port disponible")

        self._serveur.daemon_threads = True
        self._fil = threading.Thread(target=self._serveur.serve_forever, daemon=True)
        self._fil.start()
        return self._serveur.server_address[1]

    def arreter(self) -> None:
        if self._serveur is not None:
            try:
                self._serveur.shutdown()
                self._serveur.server_close()
            except OSError:
                pass
            self._serveur = None
        self._fil = None
