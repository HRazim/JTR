package com.jtr.app.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Remplace `Dispatchers.Main` par un [TestDispatcher] le temps d'un test, et le rend ensuite.
 *
 * Pourquoi une règle plutôt que `@Before`/`@After` : la règle encadre AUSSI les `@Before` et
 * `@After` de la classe. Un ViewModel construit dans un `@Before` trouve donc déjà un Main
 * installé, et rien ne peut toucher Main après qu'il a été rendu.
 *
 * Pourquoi [UnconfinedTestDispatcher] et non `StandardTestDispatcher` : les tests de ViewModel
 * de ce projet sondent des [kotlinx.coroutines.flow.StateFlow] en temps réel (le flux de
 * recherche passe par `flowOn(Dispatchers.Default)` et un `debounce`), ils n'avancent pas un
 * temps virtuel. Il leur faut donc un dispatcher qui exécute INLINE, comme le
 * `Dispatchers.Unconfined` qu'ils utilisaient — mais un vrai dispatcher de test, adossé à un
 * scheduler, seule façon de savoir quand ses coroutines sont terminées.
 *
 * C'est ce que corrige [finished] : `Dispatchers.Unconfined` ne laissait aucune prise sur les
 * coroutines encore vivantes (un `stateIn(..., WhileSubscribed)` survit au test qui l'a créé).
 * Elles lisaient alors `Dispatchers.Main` pendant que le test suivant le remplaçait, ce que
 * `TestMainDispatcher` refuse — d'où l'échec intermittent « Dispatchers.Main is used
 * concurrently with setting it ». Le scheduler est vidé AVANT de rendre Main : plus de
 * coroutine en vol, plus de course.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        // Draine ce que le test laisse derrière lui (y compris les arrêts différés des
        // collecteurs `WhileSubscribed`, via le temps virtuel du scheduler).
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }
}
