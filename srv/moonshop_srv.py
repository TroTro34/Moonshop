# -*- coding: utf-8 -*-
"""Moonshop srv — partage les jeux d'un PC vers l'appli Moonshop, depuis n'importe où.

Un seul geste pour l'utilisateur : choisir un dossier, cliquer sur Launch. Derrière,
trois briques s'enchaînent :

  serveur_fichiers  sert le dossier en HTTP sur la machine, derrière un jeton secret
  tunnel            expose ce serveur via Cloudflare, sans ouvrir de port sur la box
  annuaire          publie « code -> adresse du moment » pour que la console suive

Le code d'appairage, lui, ne change jamais : il est tiré une fois et conservé dans
%APPDATA%. C'est la seule chose que l'utilisateur tape sur sa console, une fois pour toutes.
"""

from __future__ import annotations

import json
import threading

import webview

import annuaire as module_annuaire
import appairage as module_appairage
import catalogue
import depot
import fichiers
import plateau as module_plateau
import prerequis
import reglages
import ressources
import serveur_fichiers
import tunnel as module_tunnel

TITRE = "Moonshop srv"
LARGEUR = 560
HAUTEUR = 780


class Application:
    """État de l'appli + API appelée depuis l'interface (pywebview.api.*)."""

    def __init__(self):
        self.reglages = reglages.charger()
        self.serveur = serveur_fichiers.ServeurFichiers(journal=self._tracer)
        self.tunnel = module_tunnel.Tunnel(journal=self._tracer)
        self.annuaire = module_annuaire.Annuaire(
            journal=self._tracer, secret=self.reglages.get("secret_publication", "")
        )
        # Le registre pousse l'état dès qu'une console se présente : la demande doit
        # apparaître dans la fenêtre sans que l'utilisateur ait à rafraîchir.
        self.appairage = module_appairage.Appairage(
            self.reglages, reglages.sauvegarder, sur_changement=self._pousser
        )
        self.fenetre: webview.Window | None = None
        self.etat = "hors_ligne"
        self.message = ""
        # Adresse publique complète du partage en cours (tunnel + jeton). Affichée à
        # l'utilisateur uniquement quand le code n'a pas pu être publié : c'est alors
        # sa seule porte de sortie, à coller dans « Server address » sur la console.
        self.adresse = ""
        # Adresse du PC sur son réseau local : la console l'essaie en premier et
        # télécharge alors en direct, sans faire transiter les fichiers par Cloudflare.
        self.adresse_locale = ""
        # Import en cours : (nom du fichier, pourcentage), ou None. Copier une ROM de
        # plusieurs gigaoctets prend du temps, l'interface doit le montrer.
        self.import_nom = ""
        self.import_pourcentage = 0
        self.plateau: module_plateau.Plateau | None = None
        self.depot_actif = False
        self.quitte = False
        self.chemin_courant = ""
        # Chemins dont la console ne doit pas chercher d'illustration : une
        # sauvegarde ou un document héritait sinon de la jaquette d'un jeu au nom
        # vaguement ressemblant.
        self.sans_jaquette: set[str] = set(self.reglages.get("sans_jaquette", []))

    # ------------------------------------------------------------------ interface

    def _instantane(self) -> dict:
        dossier = self.reglages.get("dossier", "")
        jeux, categories = catalogue.compter(dossier) if dossier else (0, 0)
        return {
            "dossier": dossier,
            "jeux": jeux,
            "categories": categories,
            "code": self.reglages.get("code", ""),
            "adresse": self.adresse,
            "adresse_locale": self.adresse_locale,
            "import_nom": self.import_nom,
            "import_pourcentage": self.import_pourcentage,
            "depot_actif": self.depot_actif,
            "tunnel_actif": bool(self.reglages.get("tunnel_actif", True)),
            "plateau_actif": self.plateau is not None and self.plateau.disponible,
            "etat": self.etat,
            "message": self.message,
            "demandes": self.appairage.demandes_en_attente(),
            "appareils": self.appairage.liste_appareils(),
        }

    def _pousser(self) -> None:
        """Envoie l'état complet à l'interface, qui se contente de l'afficher."""
        if self.fenetre is None:
            return
        try:
            self.fenetre.evaluate_js(f"majEtat({json.dumps(self._instantane())})")
        except Exception:
            # Fenêtre en cours de fermeture : rien à afficher, rien à signaler.
            pass

    def _tracer(self, message: str) -> None:
        self.message = message
        self._pousser()

    def _changer_etat(self, etat: str, message: str = "") -> None:
        self.etat = etat
        self.message = message
        self._pousser()

    # ------------------------------------------------------- actions de l'interface

    def etat_initial(self) -> None:
        self._pousser()

    def choisir_dossier(self) -> None:
        if self.fenetre is None:
            return
        choix = self.fenetre.create_file_dialog(webview.FOLDER_DIALOG)
        if not choix:
            return
        self.reglages["dossier"] = choix[0]
        reglages.sauvegarder(self.reglages)
        self._changer_etat(self.etat, "")

    # --------------------------------------------------- gestion du contenu partagé

    def _racine(self) -> str:
        return self.reglages.get("dossier", "")

    def basculer_tunnel(self, actif: bool) -> None:
        """Active ou coupe le tunnel. Prend effet au prochain démarrage du partage."""
        self.reglages["tunnel_actif"] = bool(actif)
        reglages.sauvegarder(self.reglages)
        if self.etat in ("en_ligne", "partiel"):
            self._tracer("Saved. Stop and start sharing again to apply.")
        else:
            self._pousser()

    def changer_code(self) -> None:
        """Tire un nouveau code, en libérant l'ancien.

        Utile si le code a été montré à quelqu'un, ou dans le cas improbable où deux
        machines auraient tiré le même : l'annuaire refuse alors la publication, et
        sans cette porte de sortie le partage resterait injoignable par le code.
        """
        partageait = self.etat in ("en_ligne", "partiel")
        # L'ancienne entrée est retirée avant : sans ça elle survivrait 24 h en
        # pointant vers un partage qui ne lui répond plus.
        try:
            self.annuaire.retirer()
        except Exception:
            pass

        self.reglages["code"] = reglages.generer_code()
        reglages.sauvegarder(self.reglages)

        if partageait and self.adresse:
            publie = self.annuaire.publier(
                self.reglages["code"], self.adresse, self.adresse_locale
            )
            if publie:
                self._changer_etat("en_ligne", "New code published. Type it on your consoles.")
            else:
                raison = self.annuaire.derniere_erreur
                self._changer_etat(
                    "partiel",
                    "New code not published" + (f" ({raison})." if raison else "."),
                )
        else:
            self._tracer("New code ready. It will be published at the next Launch.")

    def accepter_appareil(self, identifiant: str) -> None:
        self.appairage.accepter(identifiant)

    def refuser_appareil(self, identifiant: str) -> None:
        self.appairage.refuser(identifiant)

    def revoquer_appareil(self, identifiant: str) -> None:
        self.appairage.revoquer(identifiant)

    def definir_dossier_courant(self, chemin: str) -> None:
        """L'interface signale où l'utilisateur se trouve dans l'arborescence.

        Un fichier lâché sur la fenêtre arrive par la couche native, qui ignore tout
        de ce qui est affiché : sans cette information, le dépôt atterrirait toujours
        à la racine plutôt que dans le dossier ouvert.
        """
        self.chemin_courant = chemin or ""

    def lister_fichiers(self, chemin: str = "") -> dict:
        """Contenu d'un dossier. Les erreurs sont renvoyées, pas levées : l'interface
        les affiche telles quelles au lieu de rester muette."""
        if not self._racine():
            return {"erreur": "Choose a games folder first."}
        try:
            return fichiers.lister(self._racine(), chemin, self.sans_jaquette)
        except fichiers.ErreurFichiers as erreur:
            return {"erreur": str(erreur)}

    def basculer_jaquette(self, chemin: str) -> dict:
        """Active ou coupe la recherche d'illustration pour une entrée.

        Marquer un dossier vaut pour tout ce qu'il contient : un dossier de
        sauvegardes se règle en un geste au lieu de cent.
        """
        if not chemin:
            return {"erreur": "Nothing selected."}
        if chemin in self.sans_jaquette:
            self.sans_jaquette.discard(chemin)
        else:
            self.sans_jaquette.add(chemin)
        self.reglages["sans_jaquette"] = sorted(self.sans_jaquette)
        reglages.sauvegarder(self.reglages)
        self._pousser()
        return {"ok": True}

    def creer_dossier(self, chemin: str, nom: str) -> dict:
        return self._operation(lambda: fichiers.creer_dossier(self._racine(), chemin, nom))

    def renommer(self, chemin: str, nouveau_nom: str) -> dict:
        return self._operation(lambda: fichiers.renommer(self._racine(), chemin, nouveau_nom))

    def supprimer(self, chemin: str) -> dict:
        return self._operation(lambda: fichiers.supprimer(self._racine(), chemin))

    def compter_contenu(self, chemin: str) -> dict:
        try:
            return {"total": fichiers.compter_contenu(self._racine(), chemin)}
        except fichiers.ErreurFichiers as erreur:
            return {"erreur": str(erreur)}

    def choisir_fichiers_a_importer(self, chemin: str) -> None:
        """Ouvre le sélecteur système, puis importe la sélection dans le dossier visé."""
        if self.fenetre is None or not self._racine():
            return
        choix = self.fenetre.create_file_dialog(webview.OPEN_DIALOG, allow_multiple=True)
        if choix:
            self.importer(list(choix), chemin)

    def importer(self, sources: list[str], chemin: str = "") -> None:
        """Copie en tâche de fond : l'interface doit rester utilisable pendant qu'une
        ROM de plusieurs gigaoctets se copie."""
        if not self._racine() or not sources:
            return
        threading.Thread(target=self._importer, args=(sources, chemin), daemon=True).start()

    def _importer(self, sources: list[str], chemin: str) -> None:
        def progression(nom: str, pourcentage: int) -> None:
            # Ne pousse l'état que sur un changement de pourcentage entier : sinon
            # chaque bloc de 4 Mo déclencherait un rafraîchissement de la fenêtre.
            if pourcentage != self.import_pourcentage or nom != self.import_nom:
                self.import_nom = nom
                self.import_pourcentage = pourcentage
                self._pousser()

        self.import_nom = "…"
        self.import_pourcentage = 0
        self._pousser()
        try:
            bilan = fichiers.importer(self._racine(), chemin, sources, progression)
            message = f"{bilan['importes']} item{'' if bilan['importes'] == 1 else 's'} added."
            if bilan["echecs"]:
                message += " Failed: " + ", ".join(bilan["echecs"][:3])
            self.message = message
        except fichiers.ErreurFichiers as erreur:
            self.message = str(erreur)
        finally:
            self.import_nom = ""
            self.import_pourcentage = 0
            self._pousser()

    def _operation(self, action) -> dict:
        """Exécute une modification, puis rafraîchit le décompte de jeux affiché :
        le catalogue est relu à chaque requête, l'interface doit suivre."""
        if not self._racine():
            return {"erreur": "Choose a games folder first."}
        try:
            action()
        except fichiers.ErreurFichiers as erreur:
            return {"erreur": str(erreur)}
        self._pousser()
        return {"ok": True}

    # ------------------------------------------------------------------ partage

    def demarrer(self) -> None:
        # Le tunnel met quelques secondes à s'ouvrir : sans fil dédié, la fenêtre
        # resterait figée pendant ce temps.
        threading.Thread(target=self._demarrer, daemon=True).start()

    def arreter(self) -> None:
        threading.Thread(target=self._arreter, daemon=True).start()

    # ------------------------------------------------------------------ moteur

    def _demarrer(self) -> None:
        dossier = self.reglages.get("dossier", "")
        if not dossier:
            self._changer_etat("erreur", "Choose a games folder first.")
            return

        self._changer_etat("demarrage", "Starting the local server…")
        try:
            jeton = reglages.generer_jeton()
            port = self.serveur.demarrer(
                dossier,
                jeton,
                int(self.reglages.get("port", 8765)),
                marques=lambda: self.sans_jaquette,
                appairage=self.appairage,
            )

            ip_locale = serveur_fichiers.adresse_locale()
            self.adresse_locale = f"http://{ip_locale}:{port}/{jeton}" if ip_locale else ""

            if self.reglages.get("tunnel_actif", True):
                self._changer_etat("demarrage", "Opening the Cloudflare tunnel…")
                url_publique = self.tunnel.demarrer(port)
                self.adresse = f"{url_publique}/{jeton}"
            else:
                # Sans tunnel, il n'existe aucune adresse joignable de l'extérieur :
                # le code ne mènera au PC que depuis le réseau de la maison.
                self.adresse = ""
                if not self.adresse_locale:
                    raise OSError(
                        "No local network address found, and the tunnel is turned off."
                    )

            self._changer_etat("demarrage", "Publishing your code…")
            publie = self.annuaire.publier(
                self.reglages["code"], self.adresse, self.adresse_locale
            )

            jeux, _ = catalogue.compter(dossier)
            portee = (
                "Leave this window open."
                if self.reglages.get("tunnel_actif", True)
                else "Local network only — the tunnel is off."
            )
            if publie:
                self._changer_etat("en_ligne", f"{jeux} games shared. {portee}")
            else:
                # Les jeux sont bel et bien partagés, mais le code ne mène à rien :
                # l'annoncer « Online » enverrait l'utilisateur taper un code mort.
                raison = self.annuaire.derniere_erreur
                self._changer_etat(
                    "partiel",
                    f"{jeux} games shared. Code not published"
                    + (f" ({raison})." if raison else "."),
                )
        except Exception as erreur:
            self._arreter_briques()
            self.adresse = ""
            self._changer_etat("erreur", str(erreur))

    def _arreter(self) -> None:
        self._arreter_briques()
        self.adresse = ""
        self.adresse_locale = ""
        self._changer_etat("hors_ligne", "Sharing stopped.")

    def _arreter_briques(self) -> None:
        try:
            self.annuaire.retirer()
        finally:
            self.tunnel.arreter()
            self.serveur.arreter()

    # ---------------------------------------------------------- cycle de vie

    def sur_fermeture(self) -> bool:
        """Croix de la fenêtre : on se cache au lieu de quitter.

        Couper le partage parce que la fenêtre gêne serait absurde — une console est
        peut-être en plein téléchargement. L'appli continue donc dans la zone de
        notification, d'où l'on peut la rouvrir ou la quitter pour de bon.

        Renvoyer False annule la fermeture ; si l'icône n'a pas pu être créée, on
        laisse fermer, sinon l'appli deviendrait impossible à arrêter.
        """
        if self.quitte or self.plateau is None or not self.plateau.disponible:
            self._arreter_briques()
            return True
        self.fenetre.hide()
        return False

    def afficher(self) -> None:
        if self.fenetre is not None:
            self.fenetre.show()

    def quitter(self) -> None:
        """Sortie définitive, demandée depuis la zone de notification."""
        self.quitte = True
        self._arreter_briques()
        if self.fenetre is not None:
            self.fenetre.destroy()

    def fermeture(self) -> None:
        """Fenêtre réellement détruite : on coupe tunnel et serveur, sinon cloudflared
        survivrait à l'appli et continuerait d'exposer le dossier."""
        self._arreter_briques()
        if self.plateau is not None:
            self.plateau.arreter()


class PontJs:
    """Seul objet exposé à l'interface (`pywebview.api.*`).

    Volontairement réduit à quatre méthodes et une référence privée : pywebview
    parcourt *récursivement* tous les attributs publics de l'objet qu'on lui confie
    pour en déduire l'API JavaScript. Lui donner l'application entière le ferait
    descendre dans la fenêtre elle-même — et la fenêtre refuse d'être interrogée
    avant d'être affichée, ce qui empêche le démarrage. Le préfixe `_` de
    `_application` est ce qui arrête cette exploration.
    """

    def __init__(self, application: Application):
        self._application = application

    def etat_initial(self) -> None:
        self._application.etat_initial()

    def choisir_dossier(self) -> None:
        self._application.choisir_dossier()

    def demarrer(self) -> None:
        self._application.demarrer()

    def arreter(self) -> None:
        self._application.arreter()

    def lister_fichiers(self, chemin: str = "") -> dict:
        return self._application.lister_fichiers(chemin)

    def creer_dossier(self, chemin: str, nom: str) -> dict:
        return self._application.creer_dossier(chemin, nom)

    def basculer_jaquette(self, chemin: str) -> dict:
        return self._application.basculer_jaquette(chemin)

    def renommer(self, chemin: str, nouveau_nom: str) -> dict:
        return self._application.renommer(chemin, nouveau_nom)

    def supprimer(self, chemin: str) -> dict:
        return self._application.supprimer(chemin)

    def compter_contenu(self, chemin: str) -> dict:
        return self._application.compter_contenu(chemin)

    def choisir_fichiers_a_importer(self, chemin: str) -> None:
        self._application.choisir_fichiers_a_importer(chemin)

    def definir_dossier_courant(self, chemin: str) -> None:
        self._application.definir_dossier_courant(chemin)

    def basculer_tunnel(self, actif: bool) -> None:
        self._application.basculer_tunnel(actif)

    def changer_code(self) -> None:
        self._application.changer_code()

    def accepter_appareil(self, identifiant: str) -> None:
        self._application.accepter_appareil(identifiant)

    def refuser_appareil(self, identifiant: str) -> None:
        self._application.refuser_appareil(identifiant)

    def revoquer_appareil(self, identifiant: str) -> None:
        self._application.revoquer_appareil(identifiant)


def _interface() -> str:
    """HTML de l'interface, police et logo inclus en base64 (aucun fichier externe)."""
    html = ressources.chemin("ui.html").read_text(encoding="utf-8")
    return html.replace("{{POLICE}}", ressources.en_base64("tbj_buffy.ttf")).replace(
        "{{LOGO}}", ressources.en_base64("logo_moonshop_white.png")
    )


def main() -> None:
    # Avant toute chose : sans WebView2, aucune fenêtre ne s'afficherait et l'utilisateur
    # n'aurait pas le moindre message pour comprendre pourquoi.
    if not prerequis.assurer_webview2():
        return

    application = Application()
    application.fenetre = webview.create_window(
        TITRE,
        html=_interface(),
        js_api=PontJs(application),
        width=LARGEUR,
        height=HAUTEUR,
        min_size=(460, 620),
        background_color="#FFFFFF",
    )
    application.fenetre.events.closing += application.sur_fermeture
    application.fenetre.events.closed += application.fermeture

    application.plateau = module_plateau.Plateau(
        sur_ouvrir=application.afficher,
        sur_quitter=application.quitter,
        journal=application._tracer,
    )
    application.plateau.demarrer()

    def au_demarrage() -> None:
        """Branche le glisser-déposer une fois la fenêtre native réellement créée."""
        fenetre_native = getattr(application.fenetre, "native", None)
        if fenetre_native is None:
            return
        application.depot_actif = depot.activer(
            fenetre_native,
            lambda chemins: application.importer(chemins, application.chemin_courant),
        )
        application._pousser()

    webview.start(au_demarrage)


if __name__ == "__main__":
    main()
