package com.jtr.app

import com.jtr.app.domain.model.DynamicLine
import com.jtr.app.domain.relations.inverseRelationLabel
import com.jtr.app.domain.relations.mirrorLabelCandidates
import com.jtr.app.domain.relations.reconcileMirrorLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v7.1.43 — RÉCIPROCITÉ DES RELATIONS. La sémantique du miroir vit dans une fonction PURE
 * ([reconcileMirrorLines]) : elle est donc prouvée ici en JVM, sans base ni appareil — le
 * repository ne fait plus que résoudre les cibles et écrire.
 *
 * Couvre les 4 bugs corrigés :
 *  - B1 : les asymétriques produisaient le MÊME type (« enfant » ⇒ « enfant ») ;
 *  - B2 : supprimer la relation côté « enfant » ne retirait pas le miroir « mère » ;
 *  - B3 : passer de « mère » à « père » DÉTRUISAIT le miroir « enfant » d'en face ;
 *  - B4 : un miroir saisi à la main était doublé par le miroir automatique.
 */
class RelationRolesTest {

    private val KNOWN = listOf(
        // P1 (v7.1.43)
        "mother", "father", "parent", "child",
        "manager", "employee", "spouse", "friend", "brother", "sister",
        // P2 (v7.1.44)
        "teacher", "student", "mentor", "mentee", "coach", "player",
        "doctor", "patient", "consultant", "client",
        "partner", "colleague", "classmate", "neighbor", "best_friend",
    )

    /** Les 5 paires ajoutées en P2 : strictement réciproques, sans variante genrée. */
    private val PAIRS = listOf(
        "teacher" to "student",
        "mentor" to "mentee",
        "coach" to "player",
        "doctor" to "patient",
        "consultant" to "client",
    )

    // ── Table de réciprocité ─────────────────────────────────────────────────

    @Test
    fun asymmetricRoles_invertToTheirCounterpart() {
        // B1 : c'est exactement ce qui manquait — « enfant » et « manager » retombaient sur eux-mêmes.
        assertEquals("parent", inverseRelationLabel("child"))
        assertEquals("employee", inverseRelationLabel("manager"))
        assertEquals("manager", inverseRelationLabel("employee"))
        // Ascendants : les variantes genrées ET le neutre s'inversent en « enfant ».
        assertEquals("child", inverseRelationLabel("mother"))
        assertEquals("child", inverseRelationLabel("father"))
        assertEquals("child", inverseRelationLabel("parent"))
    }

    @Test
    fun symmetricRoles_invertToThemselves() {
        listOf(
            "spouse", "brother", "sister",
            "partner", "colleague", "classmate", "neighbor", // P2
        ).forEach {
            assertEquals(it, inverseRelationLabel(it))
            assertEquals(setOf(it), mirrorLabelCandidates(it))
        }
    }

    @Test
    fun p2Pairs_areStrictlyReciprocal() {
        PAIRS.forEach { (a, b) ->
            assertEquals(b, inverseRelationLabel(a))
            assertEquals(a, inverseRelationLabel(b))
            // Involutif : aller-retour = point de départ (aucune variante neutre à la Parent).
            assertEquals(a, inverseRelationLabel(inverseRelationLabel(a)))
            assertEquals(setOf(b), mirrorLabelCandidates(a))
            assertEquals(setOf(a), mirrorLabelCandidates(b))
        }
    }

    @Test
    fun friendAndBestFriend_formOneEquivalenceClass() {
        // Chacun garde son intensité à la CRÉATION…
        assertEquals("friend", inverseRelationLabel("friend"))
        assertEquals("best_friend", inverseRelationLabel("best_friend"))
        // …mais se RECONNAISSENT mutuellement comme miroir (pas de doublon, cf. mother/parent).
        assertEquals(setOf("friend", "best_friend"), mirrorLabelCandidates("friend"))
        assertEquals(setOf("best_friend", "friend"), mirrorLabelCandidates("best_friend"))
    }

    @Test
    fun allKnownRoles_areDistinctKeys() {
        assertEquals(KNOWN.size, KNOWN.toSet().size)
    }

    @Test
    fun customLabel_isMirroredVerbatim() {
        // Libellé personnalisé ou natif importé : non inversible ⇒ identité assumée.
        assertEquals("Cousin", inverseRelationLabel("Cousin"))
        assertEquals(setOf("Cousin"), mirrorLabelCandidates("Cousin"))
    }

    @Test
    fun genderedVariants_formOneEquivalenceClass() {
        // Le miroir d'« enfant » est le neutre « parent », mais mère/père comptent aussi.
        assertEquals(setOf("parent", "mother", "father"), mirrorLabelCandidates("child"))
        assertEquals(setOf("child"), mirrorLabelCandidates("mother"))
        assertEquals(setOf("child"), mirrorLabelCandidates("father"))
    }

    @Test
    fun inverse_isAlwaysRecognizedAsItsOwnMirror() {
        // Invariant de cohérence de la table : ce qu'on CRÉE doit être RECONNU au tour suivant,
        // sinon chaque sauvegarde ajouterait un doublon.
        KNOWN.forEach { label ->
            assertTrue(
                "inverse($label) doit appartenir à mirrorLabelCandidates($label)",
                inverseRelationLabel(label) in mirrorLabelCandidates(label)
            )
        }
    }

    // ── Réconciliation des miroirs ───────────────────────────────────────────

    private fun reconcile(
        target: List<DynamicLine> = emptyList(),
        current: Set<String> = emptySet(),
        previous: Set<String> = emptySet(),
    ) = reconcileMirrorLines(
        targetLines = target,
        sourceId = "johan",
        sourceName = "Johan",
        currentLabels = current,
        previousLabels = previous,
    )

    private fun mirror(label: String) =
        DynamicLine(value = "Johan", label = label, linkedPersonId = "johan")

    @Test
    fun b1_childRelation_createsParentMirror_notChild() {
        // LE BUG SIGNALÉ : Johan « enfant : Maxime » ⇒ Maxime affiche « parent », pas « enfant ».
        val lines = reconcile(current = setOf("child"))!!
        assertEquals(1, lines.size)
        assertEquals("parent", lines.single().label)
        assertEquals("Johan", lines.single().value)
        assertEquals("johan", lines.single().linkedPersonId)
    }

    @Test
    fun b1_managerRelation_createsEmployeeMirror_andBack() {
        assertEquals("employee", reconcile(current = setOf("manager"))!!.single().label)
        assertEquals("manager", reconcile(current = setOf("employee"))!!.single().label)
    }

    @Test
    fun fatherRelation_stillCreatesChildMirror() {
        // Non-régression : les miroirs DÉJÀ corrects avant v7.1.43 le restent.
        assertEquals("child", reconcile(current = setOf("father"))!!.single().label)
        assertEquals("child", reconcile(current = setOf("mother"))!!.single().label)
    }

    @Test
    fun symmetricRelation_mirrorsIdentically() {
        assertEquals("friend", reconcile(current = setOf("friend"))!!.single().label)
        assertEquals("spouse", reconcile(current = setOf("spouse"))!!.single().label)
    }

    @Test
    fun b2_removingRelation_removesItsMirror() {
        // Johan « enfant : Maxime » (miroir « parent » chez Maxime), puis Johan supprime la ligne.
        val lines = reconcile(target = listOf(mirror("parent")), current = emptySet(), previous = setOf("child"))
        assertEquals(emptyList<DynamicLine>(), lines)
    }

    @Test
    fun b2_removingChildRelation_alsoRemovesGenderedMirror() {
        // Le miroir d'en face peut être « mère » (saisi à la main) : même classe d'équivalence,
        // donc il est bien retiré — c'était la donnée fantôme de B2.
        val lines = reconcile(target = listOf(mirror("mother")), current = emptySet(), previous = setOf("child"))
        assertEquals(emptyList<DynamicLine>(), lines)
    }

    @Test
    fun b3_switchingMotherToFather_keepsTheChildMirror() {
        // B3 : l'ancien code supprimait le miroir après l'avoir « déjà vu » ⇒ relation perdue.
        val target = listOf(mirror("child"))
        assertNull(reconcile(target = target, current = setOf("father"), previous = setOf("mother")))
    }

    @Test
    fun b3_switchingParentToMother_keepsTheChildMirror() {
        val target = listOf(mirror("child"))
        assertNull(reconcile(target = target, current = setOf("mother"), previous = setOf("parent")))
    }

    @Test
    fun b4_existingEquivalentMirror_isNeverDuplicated() {
        // Johan « enfant : Maxime » alors que Maxime porte DÉJÀ « mère : Johan » (saisi à la main).
        val target = listOf(mirror("mother"))
        assertNull(reconcile(target = target, current = setOf("child")))
        // …et l'inverse : Johan « mère : Maxime » quand Maxime porte déjà « enfant : Johan ».
        assertNull(reconcile(target = listOf(mirror("child")), current = setOf("mother")))
    }

    @Test
    fun unrelatedBackReference_isNeverDeletedByAnEditThatDoesNotConcernIt() {
        // Une relation IMPORTÉE (l'import ne réciproque pas, par design) pointe vers Johan sans
        // miroir correspondant : ajouter « ami » ne doit pas l'emporter.
        val imported = DynamicLine(value = "Johan", label = "mother", linkedPersonId = "johan")
        val lines = reconcile(target = listOf(imported), current = setOf("friend"))!!
        assertEquals(listOf("mother", "friend"), lines.map { it.label })
    }

    @Test
    fun linesTowardOtherPeople_areUntouched() {
        val other = DynamicLine(value = "Léa", label = "sister", linkedPersonId = "lea")
        val lines = reconcile(target = listOf(other), current = setOf("child"))!!
        assertEquals(2, lines.size)
        assertTrue(other in lines)
    }

    @Test
    fun legacyMirror_withoutLinkedId_isRecognizedByName() {
        // Miroir HÉRITÉ (pré-v7.1.6, sans linkedPersonId) : reconnu par le nom → ni doublon,
        // ni suppression manquée.
        val legacy = DynamicLine(value = "johan", label = "child") // casse différente volontaire
        assertNull(reconcile(target = listOf(legacy), current = setOf("mother")))
        assertEquals(
            emptyList<DynamicLine>(),
            reconcile(target = listOf(legacy), current = emptySet(), previous = setOf("mother"))
        )
    }

    @Test
    fun multipleRelationsTowardSameTarget_eachKeepsItsMirror() {
        // Deux relations vers la même fiche : retirer l'une ne doit pas emporter l'autre.
        val target = listOf(mirror("child"), mirror("friend"))
        val lines = reconcile(target = target, current = setOf("friend"), previous = setOf("mother", "friend"))!!
        assertEquals(listOf("friend"), lines.map { it.label })
    }

    @Test
    fun p2_asymmetricPair_mirrorsToItsCounterpart() {
        assertEquals("student", reconcile(current = setOf("teacher"))!!.single().label)
        assertEquals("teacher", reconcile(current = setOf("student"))!!.single().label)
        assertEquals("patient", reconcile(current = setOf("doctor"))!!.single().label)
        assertEquals("client", reconcile(current = setOf("consultant"))!!.single().label)
    }

    @Test
    fun p2_symmetricRole_mirrorsIdentically() {
        assertEquals("neighbor", reconcile(current = setOf("neighbor"))!!.single().label)
        assertEquals("partner", reconcile(current = setOf("partner"))!!.single().label)
    }

    @Test
    fun p2_bestFriendAndFriend_neverProduceTwoLines() {
        // La cible a déjà « ami : Johan » ; Johan déclare « meilleur ami » ⇒ aucune 2ᵉ ligne.
        assertNull(reconcile(target = listOf(mirror("friend")), current = setOf("best_friend")))
        // …et symétriquement.
        assertNull(reconcile(target = listOf(mirror("best_friend")), current = setOf("friend")))
    }

    @Test
    fun p2_removingFriendship_removesEitherIntensity() {
        assertEquals(
            emptyList<DynamicLine>(),
            reconcile(target = listOf(mirror("best_friend")), current = emptySet(), previous = setOf("friend"))
        )
    }

    @Test
    fun noChange_returnsNull_soNoUselessWrite() {
        assertNull(reconcile(target = listOf(mirror("friend")), current = setOf("friend"), previous = setOf("friend")))
        assertNull(reconcile())
    }
}
