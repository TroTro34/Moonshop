package com.monshop.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Effets sonores de l'interface (clics, menu, écran de chargement, récompense).
 *
 * Les sons sont *synthétisés* à la volée (sinusoïdes + enveloppes) plutôt que lus depuis
 * des fichiers audio : rien à committer dans le dépôt, aucune licence à gérer, et les
 * bruitages restent cohérents entre eux. Chaque son est généré une seule fois puis gardé
 * en cache mémoire (quelques dizaines de Ko au total).
 *
 * La lecture se fait sur un fil dédié : générer/écrire un AudioTrack depuis le fil
 * principal ferait sauter l'animation en cours au moment du clic.
 */
object SoundEffects {

    private const val FREQUENCE = 44100
    const val VOLUME_PAR_DEFAUT = 0.75f

    /** Coupé depuis les réglages : aucun son n'est alors ni généré ni joué. */
    @Volatile
    var actifs: Boolean = true

    /**
     * Volume des bruitages, réglable indépendamment de la musique de fond.
     *
     * Appliqué à la lecture et non à la génération : les échantillons restent en cache
     * tels quels, changer le volume n'oblige donc pas à tout regénérer.
     */
    @Volatile
    var volume: Float = VOLUME_PAR_DEFAUT
        set(valeur) {
            field = valeur.coerceIn(0f, 1f)
        }

    private val fils = Executors.newCachedThreadPool { tache ->
        Thread(tache, "moonshop-sfx").apply { isDaemon = true }
    }
    private val cache = HashMap<String, ShortArray>()

    // ---------- Sons exposés à l'interface ----------

    /** Clic court et sec : boutons, cartes, éléments de liste. */
    fun clic() = jouer("clic") {
        construire(0.07) { t, duree ->
            val enveloppe = attaque(t, 0.004) * exp(-t / (duree * 0.22))
            (sin(2 * PI * 1180 * t) + 0.35 * sin(2 * PI * 2360 * t)) * 0.92 * enveloppe
        }
    }

    /** Glissando montant : ouverture du menu déroulant / d'un panneau. */
    fun menuOuvrir() = jouer("menuOuvrir") {
        construire(0.20) { t, duree ->
            val avancement = t / duree
            val frequence = 480.0 + 620.0 * avancement
            val enveloppe = attaque(t, 0.012) * (1.0 - avancement * 0.85)
            (sin(2 * PI * frequence * t) + 0.25 * sin(2 * PI * frequence * 2 * t)) * 0.78 * enveloppe
        }
    }

    /** Glissando descendant : fermeture du menu / retour en arrière. */
    fun menuFermer() = jouer("menuFermer") {
        construire(0.18) { t, duree ->
            val avancement = t / duree
            val frequence = 980.0 - 520.0 * avancement
            val enveloppe = attaque(t, 0.012) * (1.0 - avancement * 0.9)
            sin(2 * PI * frequence * t) * 0.80 * enveloppe
        }
    }

    /** Arpège montant joué au tout début de l'écran de chargement. */
    fun chargementDebut() = jouer("chargementDebut") {
        arpege(listOf(523.25, 659.25, 783.99, 1046.50), dureeNote = 0.11, tenue = 0.30)
    }

    /** Accord de résolution quand le logo s'ouvre et que l'appli est prête. */
    fun chargementFin() = jouer("chargementFin") {
        accord(listOf(783.99, 1046.50, 1318.51), duree = 0.75)
    }

    /** "Pop" du couvercle qui saute quand le cadeau s'ouvre. */
    fun cadeauOuvert() = jouer("cadeauOuvert") {
        construire(0.13) { t, duree ->
            val avancement = t / duree
            val frequence = 220.0 + 1100.0 * avancement * avancement
            val enveloppe = attaque(t, 0.003) * exp(-t / (duree * 0.28))
            sin(2 * PI * frequence * t) * 0.98 * enveloppe
        }
    }

    /** Fanfare de récompense : arpège rapide + accord final tenu (installation terminée). */
    fun recompense() = jouer("recompense") {
        val montee = arpege(listOf(523.25, 659.25, 783.99, 1046.50, 1318.51), dureeNote = 0.085, tenue = 0.22)
        val final = accord(listOf(1046.50, 1318.51, 1568.00), duree = 0.9)
        concatener(montee, final, chevauchement = (FREQUENCE * 0.06).toInt())
    }

    // ---------- Fabrication des échantillons ----------

    /** Fondu d'attaque : évite le "clac" d'un signal qui démarre net à pleine amplitude. */
    private fun attaque(t: Double, duree: Double): Double = min(1.0, t / duree)

    private fun construire(duree: Double, echantillon: (t: Double, duree: Double) -> Double): ShortArray {
        val total = (duree * FREQUENCE).toInt()
        val sortie = ShortArray(total)
        for (i in 0 until total) {
            val t = i / FREQUENCE.toDouble()
            var valeur = echantillon(t, duree)
            // Fondu de sortie sur les 5 derniers millisecondes, même raison que l'attaque.
            val restant = (total - i) / FREQUENCE.toDouble()
            if (restant < 0.005) valeur *= restant / 0.005
            sortie[i] = (valeur.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return sortie
    }

    /** Suite de notes qui se déclenchent l'une après l'autre mais résonnent ensemble. */
    private fun arpege(notes: List<Double>, dureeNote: Double, tenue: Double): ShortArray {
        val duree = dureeNote * (notes.size - 1) + tenue
        return construire(duree) { t, _ ->
            var valeur = 0.0
            notes.forEachIndexed { indice, frequence ->
                val debut = indice * dureeNote
                if (t >= debut) {
                    val age = t - debut
                    valeur += sin(2 * PI * frequence * age) * 0.46 * attaque(age, 0.005) * exp(-age / (tenue * 0.45))
                }
            }
            valeur
        }
    }

    /** Plusieurs notes jouées ensemble, avec une décroissance douce. */
    private fun accord(notes: List<Double>, duree: Double): ShortArray = construire(duree) { t, d ->
        var valeur = 0.0
        notes.forEach { frequence ->
            valeur += sin(2 * PI * frequence * t) * 0.42 * attaque(t, 0.008) * exp(-t / (d * 0.5))
        }
        valeur
    }

    private fun concatener(premier: ShortArray, second: ShortArray, chevauchement: Int): ShortArray {
        val decalage = (premier.size - chevauchement).coerceAtLeast(0)
        val sortie = ShortArray(decalage + second.size)
        premier.copyInto(sortie, 0, 0, min(premier.size, sortie.size))
        for (i in second.indices) {
            val cible = decalage + i
            val somme = sortie[cible] + second[i]
            sortie[cible] = somme.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return sortie
    }

    // ---------- Lecture ----------

    private fun jouer(cle: String, generateur: () -> ShortArray) {
        if (!actifs || volume <= 0f) return
        try {
            fils.execute {
                var piste: AudioTrack? = null
                try {
                    val echantillons = synchronized(cache) { cache.getOrPut(cle, generateur) }
                    piste = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                // USAGE_MEDIA et non USAGE_ASSISTANCE_SONIFICATION : cette
                                // dernière route le son vers le flux « système » de
                                // l'appareil, souvent bien plus bas que le volume média et
                                // réglé par un autre curseur. Les bruitages suivent
                                // désormais le même volume que la musique du jeu.
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(FREQUENCE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(echantillons.size * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                    piste.write(echantillons, 0, echantillons.size)
                    // Racine carrée : un gain linéaire rend la moitié basse du
                    // curseur quasi inaudible, l'oreille percevant le volume de
                    // façon logarithmique. À 30 %, le son reste franchement audible.
                    piste.setVolume(sqrt(volume.coerceIn(0f, 1f)))
                    piste.play()
                    // MODE_STATIC : tout est déjà en mémoire, il suffit d'attendre la fin
                    // de la lecture avant de libérer la piste (sinon le son est coupé net).
                    Thread.sleep((echantillons.size * 1000L / FREQUENCE) + 60)
                } catch (e: Exception) {
                    // Un bruitage raté ne doit jamais faire tomber l'appli.
                } finally {
                    try { piste?.release() } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {
        }
    }
}
