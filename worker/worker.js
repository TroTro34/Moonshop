/**
 * Annuaire Moonshop — le seul point fixe du système.
 *
 * Moonshop srv (PC) y dépose « code -> adresse du tunnel du moment » à chaque Launch ;
 * l'appli Android y retrouve l'adresse à jour derrière le code que l'utilisateur a tapé
 * une seule fois. C'est ce qui permet à l'URL du tunnel de changer à chaque démarrage
 * sans que personne n'ait rien à ressaisir.
 *
 * Déployé une fois par le développeur : les utilisateurs ne créent aucun compte et
 * n'ont pas connaissance de ce service.
 *
 *   POST /publish  {code, url, url_locale}  X-Moonshop-Key + X-Moonshop-Secret  -> 200
 *   POST /retirer  {code}                   X-Moonshop-Key + X-Moonshop-Secret  -> 200
 *   GET  /resolve?code=XXXXXX                        -> 200 {url, url_locale} | 404
 *
 * Un code appartient au PC qui l'a publié le premier : l'empreinte de son secret est
 * gardée à côté de l'adresse, et toute écriture ultérieure doit présenter le même
 * secret. Sans cela, la clé de publication étant la même dans toutes les copies de
 * l'exe, n'importe quel porteur pouvait détourner le code d'un autre vers son propre
 * serveur — et faire installer ses fichiers sur la console d'en face.
 *
 * Deux adresses sont publiées : celle du tunnel, joignable de partout, et celle du PC
 * sur son réseau local. La console essaie la locale en premier — quand les deux
 * appareils sont sur le même wifi, passer par Cloudflare ferait sortir puis revenir
 * chaque octet, pour un débit plafonné par l'upload de la box.
 */

// Doit correspondre à CLE_PUBLICATION dans srv/annuaire.py. Cette clé voyage dans
// l'exe : elle n'écarte que les écritures automatisées de passage. Ce qui distingue
// un PC d'un autre, c'est le secret ci-dessous, propre à chaque machine.
const CLE_ATTENDUE = "moonshop-srv";

/** Empreinte hexadécimale du secret d'un PC. Seule l'empreinte est stockée. */
async function empreinte(secret) {
  const condensat = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(secret));
  return [...new Uint8Array(condensat)].map((o) => o.toString(16).padStart(2, "0")).join("");
}

// 24 h : une entrée survit largement à une session de partage, et disparaît d'elle-même
// si le PC ne revient jamais. Rafraîchie à chaque Launch.
const DUREE_DE_VIE = 86400;

// Même alphabet que côté PC (ni I, ni O, ni 0, ni 1).
const MOTIF_CODE = /^[A-HJ-NP-Z2-9]{6}$/;

const json = (donnees, statut = 200) =>
  new Response(JSON.stringify(donnees), {
    status: statut,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });

function adresseValide(url) {
  try {
    const analysee = new URL(url);
    // HTTPS seulement : l'adresse transite jusqu'à la console, et le jeton secret
    // est dans le chemin.
    return analysee.protocol === "https:" && analysee.hostname.length <= 253;
  } catch {
    return false;
  }
}

/**
 * L'adresse locale est en HTTP simple (pas de certificat possible pour une IP privée)
 * et n'a de sens que sur un réseau domestique : on n'accepte donc que des IP privées,
 * pour ne pas transformer l'annuaire en redirecteur vers n'importe quoi.
 */
function adresseLocaleValide(url) {
  if (!url) return false;
  try {
    const analysee = new URL(url);
    if (analysee.protocol !== "http:") return false;
    const octets = analysee.hostname.split(".").map(Number);
    if (octets.length !== 4 || octets.some((o) => !Number.isInteger(o) || o < 0 || o > 255)) {
      return false;
    }
    const [a, b] = octets;
    return a === 10 || (a === 192 && b === 168) || (a === 172 && b >= 16 && b <= 31);
  } catch {
    return false;
  }
}

/**
 * Limite le nombre de résolutions par adresse IP.
 *
 * Deviner un code demanderait des milliards d'essais, donc l'énumération n'est pas une
 * menace crédible — en revanche elle épuiserait le quota gratuit du Worker, et couperait
 * l'accès à tous les utilisateurs légitimes. C'est cette panne-là qu'on écarte.
 *
 * Tolère l'absence de la liaison : un déploiement sans elle continue de fonctionner
 * plutôt que d'échouer sur chaque requête.
 */
async function tropDeRequetes(requete, env) {
  if (!env.LIMITE_RESOLUTION) return false;
  const ip = requete.headers.get("CF-Connecting-IP") || "inconnue";
  try {
    const { success } = await env.LIMITE_RESOLUTION.limit({ key: ip });
    return !success;
  } catch {
    return false;
  }
}

export default {
  async fetch(requete, env) {
    const url = new URL(requete.url);

    if (url.pathname === "/resolve" && requete.method === "GET") {
      const code = (url.searchParams.get("code") || "").toUpperCase();
      if (!MOTIF_CODE.test(code)) return json({ erreur: "code invalide" }, 400);

      if (await tropDeRequetes(requete, env)) {
        return json({ erreur: "trop de tentatives" }, 429);
      }

      const brut = await env.ANNUAIRE.get(`code:${code}`);
      if (!brut) return json({ erreur: "hors ligne" }, 404);
      // Les entrées écrites par les versions précédentes contiennent l'URL seule,
      // pas un objet : on les accepte encore pour ne casser aucun code déjà publié.
      try {
        const entree = JSON.parse(brut);
        return json({ url: entree.url, url_locale: entree.url_locale || "" });
      } catch {
        return json({ url: brut, url_locale: "" });
      }
    }

    if (requete.method === "POST" && (url.pathname === "/publish" || url.pathname === "/retirer")) {
      if (requete.headers.get("X-Moonshop-Key") !== CLE_ATTENDUE) {
        return json({ erreur: "non autorise" }, 403);
      }

      let corps;
      try {
        corps = await requete.json();
      } catch {
        return json({ erreur: "corps illisible" }, 400);
      }

      const code = String(corps.code || "").toUpperCase();
      if (!MOTIF_CODE.test(code)) return json({ erreur: "code invalide" }, 400);

      // Le secret identifie la machine ; sans lui, impossible de savoir à qui
      // appartient le code. Les versions de l'exe antérieures à l'appairage n'en
      // envoient pas : elles sont refusées, et c'est voulu.
      const secret = requete.headers.get("X-Moonshop-Secret") || "";
      if (secret.length < 16) return json({ erreur: "secret manquant" }, 403);
      const marque = await empreinte(secret);

      const existant = await env.ANNUAIRE.get(`code:${code}`);
      let proprietaire = null;
      if (existant) {
        try {
          proprietaire = JSON.parse(existant).proprietaire || null;
        } catch {
          proprietaire = null;
        }
      }
      // Une entrée sans propriétaire vient d'une version précédente : le premier PC
      // à repasser dessus l'adopte, pour ne pas casser les codes déjà en service.
      if (proprietaire && proprietaire !== marque) {
        return json({ erreur: "code deja pris par une autre machine" }, 403);
      }

      if (url.pathname === "/retirer") {
        await env.ANNUAIRE.delete(`code:${code}`);
        return json({ ok: true });
      }

      // L'adresse locale est facultative : un PC peut très bien n'être joignable
      // que par le tunnel (réseau d'entreprise, IP publique côté machine…).
      const locale = adresseLocaleValide(corps.url_locale) ? corps.url_locale : "";

      // L'inverse l'est aussi : qui coupe le tunnel ne publie qu'une adresse locale,
      // et son code ne fonctionne alors que sur son propre réseau. C'est un choix
      // délibéré, pas une erreur — mais publier une entrée vide n'aurait aucun sens.
      const publique = adresseValide(corps.url) ? corps.url : "";
      if (!publique && !locale) return json({ erreur: "adresse invalide" }, 400);
      const entree = JSON.stringify({ url: publique, url_locale: locale, proprietaire: marque });

      await env.ANNUAIRE.put(`code:${code}`, entree, { expirationTtl: DUREE_DE_VIE });
      return json({ ok: true, ttl: DUREE_DE_VIE, url_locale: Boolean(locale) });
    }

    return json({ erreur: "route inconnue" }, 404);
  },
};
