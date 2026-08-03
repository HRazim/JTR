package com.jtr.app.domain.relations

import com.jtr.app.domain.model.DynamicLine

/**
 * v7.1.43 — RÉCIPROCITÉ DES RELATIONS : source de vérité UNIQUE de l'inversion.
 *
 * Une relation asymétrique doit produire son INVERSE sur la fiche liée (« enfant » ⇒
 * « parent », « manager » ⇒ « employé »), jamais le même type. Les types symétriques
 * (conjoint, ami) se reflètent à l'identique. Tout label inconnu — libellé personnalisé
 * saisi par l'utilisateur, libellé natif importé (« Assistant »…) — est appliqué
 * TEXTUELLEMENT en miroir : non inversible, donc identité assumée.
 *
 * DEUX notions distinctes, et c'est ce qui rend la synchronisation correcte :
 *  - [inverseRelationLabel] = le label à CRÉER. Toujours NEUTRE : l'inverse d'« enfant »
 *    est « parent », jamais « mère »/« père » (JTR ne modélise pas le genre — le deviner
 *    inventerait une donnée). L'utilisateur reste libre de préciser ensuite.
 *  - [mirrorLabelCandidates] = les labels RECONNUS comme miroir déjà présent. Les variantes
 *    genrées forment une CLASSE D'ÉQUIVALENCE : « mère », « père » et « parent » valent tous
 *    comme miroir d'« enfant ». Sans cela, un miroir « mère » saisi à la main serait doublé
 *    d'un « parent » automatique, et un changement « mère »→« père » détruirait le miroir.
 *
 * EXTENSIBILITÉ : ajouter un couple = ajouter une (ou deux) ligne(s) dans [ROLES]
 * (+ la clé dans `FieldTypes.RELATION` et sa string `relation_type_*`). Aucune autre
 * logique d'inversion n'existe ailleurs dans l'app — ni repository, ni Composable.
 */
private data class RelationRole(
    /** Label du miroir à créer (neutre). */
    val inverse: String,
    /** Labels acceptés comme miroir existant de ce rôle (classe d'équivalence). */
    val mirrorCandidates: Set<String>,
)

/** Table de réciprocité — clés stables de `FieldTypes.RELATION`. */
private val ROLES: Map<String, RelationRole> = mapOf(
    // Asymétriques — ascendant ⇄ descendant. Les variantes genrées s'inversent en « enfant » ;
    // « enfant » s'inverse en « parent » NEUTRE mais reconnaît mère/père comme miroir valide.
    "mother" to RelationRole(inverse = "child", mirrorCandidates = setOf("child")),
    "father" to RelationRole(inverse = "child", mirrorCandidates = setOf("child")),
    "parent" to RelationRole(inverse = "child", mirrorCandidates = setOf("child")),
    "child" to RelationRole(inverse = "parent", mirrorCandidates = setOf("parent", "mother", "father")),

    // Asymétriques — hiérarchie professionnelle.
    "manager" to RelationRole(inverse = "employee", mirrorCandidates = setOf("employee")),
    "employee" to RelationRole(inverse = "manager", mirrorCandidates = setOf("manager")),

    // Symétriques — l'inverse est le type lui-même. Frère/sœur : le genre de la cible étant
    // inconnu, on conserve le type source (frère⇒frère) plutôt que d'inventer « frère/sœur ».
    "spouse" to RelationRole(inverse = "spouse", mirrorCandidates = setOf("spouse")),
    "friend" to RelationRole(inverse = "friend", mirrorCandidates = setOf("friend")),
    "brother" to RelationRole(inverse = "brother", mirrorCandidates = setOf("brother")),
    "sister" to RelationRole(inverse = "sister", mirrorCandidates = setOf("sister")),
)

/** Le label à CRÉER en miroir (neutre). Type inconnu ou libellé libre ⇒ lui-même. */
fun inverseRelationLabel(label: String): String = ROLES[label]?.inverse ?: label

/** Les labels RECONNUS comme miroir de [label] (idempotence + suppression). */
fun mirrorLabelCandidates(label: String): Set<String> = ROLES[label]?.mirrorCandidates ?: setOf(label)

/**
 * Réconcilie les lignes de relation d'UNE fiche cible avec les relations que la fiche
 * SOURCE pointe vers elle. Fonction PURE (aucun accès base) — c'est ici que vit toute la
 * sémantique du miroir ; le repository ne fait plus que lire/écrire.
 *
 * @param targetLines lignes actuelles de la CIBLE.
 * @param sourceId identifiant stable de la source (clé du miroir).
 * @param sourceName nom affiché de la source (valeur du miroir + repli de reconnaissance
 *        pour les miroirs HÉRITÉS, sans `linkedPersonId`).
 * @param currentLabels labels des relations source → cible APRÈS édition.
 * @param previousLabels labels AVANT édition (vide à la création).
 * @return les nouvelles lignes de la cible, ou `null` si rien ne change.
 *
 * Règles :
 *  - AJOUT : chaque label courant sans miroir reconnu ([mirrorLabelCandidates]) ajoute
 *    [inverseRelationLabel]. Un miroir équivalent déjà là n'est jamais doublé.
 *  - RETRAIT : seuls les miroirs de labels DISPARUS sont retirés, et uniquement s'ils ne
 *    sont plus justifiés par un label courant. Un miroir encore justifié survit (changer
 *    « mère » en « père » ne détruit pas l'« enfant » d'en face).
 *  - Le retrait est VOLONTAIREMENT limité à la famille des labels disparus : une ligne de la
 *    cible pointant vers la source sans miroir correspondant (relation IMPORTÉE, que l'import
 *    ne réciproque pas par design) n'est JAMAIS supprimée par une édition sans rapport.
 */
fun reconcileMirrorLines(
    targetLines: List<DynamicLine>,
    sourceId: String,
    sourceName: String,
    currentLabels: Set<String>,
    previousLabels: Set<String>,
): List<DynamicLine>? {
    // Une ligne de la cible « pointe vers la source » par IDENTIFIANT ; repli par nom
    // uniquement pour les miroirs hérités (sans linkedPersonId), comme la navigation.
    fun pointsToSource(line: DynamicLine): Boolean =
        line.linkedPersonId == sourceId ||
            (line.linkedPersonId == null && sourceName.isNotBlank() &&
                line.value.trim().equals(sourceName, ignoreCase = true))

    val justified = currentLabels.flatMapTo(mutableSetOf()) { mirrorLabelCandidates(it) }
    val obsolete = (previousLabels - currentLabels)
        .flatMapTo(mutableSetOf()) { mirrorLabelCandidates(it) } - justified

    val result = targetLines.filterNot { pointsToSource(it) && it.label in obsolete }.toMutableList()
    currentLabels.forEach { label ->
        val candidates = mirrorLabelCandidates(label)
        if (result.none { pointsToSource(it) && it.label in candidates }) {
            result += DynamicLine(
                value = sourceName,
                label = inverseRelationLabel(label),
                linkedPersonId = sourceId,
            )
        }
    }
    return result.takeIf { it != targetLines }
}
