package com.monshop.app

import org.json.JSONArray

data class CatalogItem(
    val nom: String,
    val cheminServeur: String,
    val image: String?,       // chemin serveur de l'image, ou null
    val description: String,  // vide si non renseignée
    val categorie: String,
    /** Faux : ne pas chercher d'illustration pour ce fichier. Un document ou une
     *  sauvegarde héritait sinon de la jaquette d'un jeu au nom ressemblant. */
    val avecJaquette: Boolean = true
)

fun parseCatalogue(json: String): List<CatalogItem> {
    val array = JSONArray(json)
    val result = mutableListOf<CatalogItem>()
    for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        result.add(
            CatalogItem(
                nom = obj.getString("nom"),
                cheminServeur = obj.getString("chemin_serveur"),
                image = if (obj.isNull("image")) null else obj.optString("image", null),
                description = obj.optString("description", ""),
                categorie = obj.optString("categorie", "Misc"),
                // Absent des catalogues plus anciens : illustration par défaut.
                avecJaquette = obj.optBoolean("jaquette", true)
            )
        )
    }
    return result
}
