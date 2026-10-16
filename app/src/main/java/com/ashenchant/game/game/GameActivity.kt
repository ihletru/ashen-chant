package com.ashenchant.game.game

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import com.beloko.touchcontrols.TouchSettings
import com.ashenchant.game.HereticSettings
import com.ashenchant.game.engine.NativeEngine
import com.ashenchant.game.touch.TouchOverlayView
import org.libsdl.app.SDLOpenTouch
import org.libsdl.app2012.SDLActivity

/**
 * Aktywność rozgrywki. Działa w OSOBNYM procesie (:game) — silnik nie umie
 * zainicjalizować się ponownie w tym samym procesie, więc przy niszczeniu
 * zabijamy proces (System.exit).
 *
 * To cienki subclass zendowanego SDL2 SDLActivity: SDL owns the surface i
 * kontekst EGL, a pętla główna silnika działa na wątku SDL
 * (SDLOpenTouch.RunApplication -> NativeLib.init, który nie wraca).
 *
 * Kolejność bibliotek = kolejność loadLibrary — kontrakt z native stack
 * (SDLActivity.getLibraries musi załadować uzdoom PO jego zależnościach).
 */
class GameActivity : SDLActivity() {

    /** Nasza nakładka; null, gdy użytkownik wyłączył własne sterowanie. */
    private var touchOverlay: TouchOverlayView? = null

    /** Czy przełączyliśmy silnik na wyjście bez okna potwierdzenia. */
    private var quickExitConfigured = false

    /**
     * WYJŚCIE Z GRY BEZ OKNA POTWIERDZENIA (zgłoszenie Milosza 15.10.2026:
     * „po quit gra powinna się zakończyć").
     *
     * Silnikowe „QUIT GAME" woła `CCMD(menu_quit)` (`menu/doommenu.cpp`), a to
     * buduje MessageBox z `gameinfo.quitmessages` i dopiero po potwierdzeniu
     * woła `M_Quit()`. Jest jednak CVar `m_quickexit` (domyślnie false,
     * `CVAR_ARCHIVE`) — przy nim `menu_quit` robi `M_Quit()` od razu.
     * ⚠️ Wtedy okno potwierdzenia jest NIEOSIĄGALNE: `M_Quit` → `ST_Endoom`,
     * a `ST_Endoom` KOŃCZY SIĘ `throw CExitEvent` (`startscreen/endoom.cpp:220`),
     * czyli nigdy nie wraca do kodu budującego MessageBox.
     * ⚠️ Nie rusza to sprzętowego Back — `NativeLib` mapuje KEYCODE_BACK na
     * SDL_SCANCODE_ESCAPE (menu), a nie na `menu_quit`.
     * ⚠️ Komendę wysyłamy dopiero, gdy silnik się zgłosi (`setEngineUiActive`),
     * bo wcześniej biblioteka natywna może nie być jeszcze zbindowana.
     */
    private fun configureQuickExit() {
        if (quickExitConfigured) return
        quickExitConfigured = runCatching {
            NativeEngine.executeCommand("m_quickexit 1")
        }.isSuccess
    }

    /** Czy wysłaliśmy już skok do menu (ma polecieć tylko raz). */
    private var menuJumpDone = false

    /**
     * SKOK DO MENU ZAMIAST PĘTLI TYTUŁOWEJ (polecenie Milosza 15.10.2026).
     *
     * Doom po starcie siedzi w pętli atrakcyjnej — ekran tytułowy, demka i strony
     * licencyjne w kółko — i do menu wchodzi się dopiero po tapnięciu. Na telefonie
     * wygląda to jak „gra stoi i nic się nie dzieje".
     *
     * Wysyłamy JEDEN Escape, taki sam, jaki wysyła przycisk MENU
     * (`NativeEngine.backButton` → `mobileBackButton` → `PortableBackButton`).
     * ⚠️ Dopiero po pierwszym zgłoszeniu silnika: `setEngineUiActive(true)` leci
     * z `frameControlsSDLCallback`, gdy `gameUiActive()` zmieni stan, czyli w
     * praktyce w chwili pokazania ekranu tytułowego. Wcześniej Escape trafiłby
     * w ładowanie i mógł zrzucić grę do konsoli.
     * ⚠️ Opóźnienie jest po to, żeby nie wyprzedzić silnika, który jeszcze
     * dogrywa grafikę — i żeby zdążył zniknąć ekran startowy.
     * ⚠️ Tylko RAZ (`menuJumpDone`): późniejsze wejścia do menu są już decyzją
     * gracza i nie wolno mu ich zamykać.
     */
    private fun jumpToMenuSoon() {
        if (menuJumpDone) return
        menuJumpDone = true
        val overlay = touchOverlay ?: return
        overlay.postDelayed({
            runCatching { NativeEngine.backButton() }
        }, MENU_JUMP_DELAY_MS)
    }

    private companion object {
        /** Ile czekamy od pokazania ekranu tytułowego do wysłania Escape. */
        const val MENU_JUMP_DELAY_MS = 2500L
    }

    override fun getLibraries(): Array<String> = arrayOf(
        "hidapi",
        "saffal",
        "openal",
        "zmusic_uz",
        "touchcontrols",
        "SDL2",
        "uzdoom",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Nasza własna nakładka sterująca — JEDYNE sterowanie rozgrywki.
        // Bez przełącznika (decyzja Milosza 15.10.2026: pytanie usunięte
        // z launchera). SDLActivity (ścieżka OPENTOUCH_SDL_EXTRA) robi
        // setContentView(mSurface), więc nakładkę dokładamy addContentView
        // — leży NA powierzchni GL i dostaje zdarzenia dotykowe.
        HereticSettings.reloadSettings(applicationContext)
        touchOverlay = TouchOverlayView(this).also { overlay ->
            addContentView(
                overlay,
                android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    /**
     * Silnik przejął ekran (menu, ekrany tytułowe, intermisja, finał) albo
     * wrócił do rozgrywki - wywoływane z UI thread przez SDLOpenTouch.
     * Gdy nakładka schodzi, puszczamy wszystkie wciśnięte strefy, żeby gra nie
     * dostała "zablokowanego" przycisku (np. chodu) zanim UI ją przykryło.
     */
    fun setEngineUiActive(active: Boolean) {
        val overlay = touchOverlay ?: return
        // Pierwsze zgłoszenie z silnika = biblioteka natywna gotowa.
        configureQuickExit()
        if (active) {
            // Pierwsze „UI silnika aktywne" to ekran tytułowy — stąd skok do menu.
            jumpToMenuSoon()
            // ⚠️ Panel ustawień musi zniknąć RAZEM z nakładką — inaczej zostałby
            // zapauzowany stan, którego gracz nie ma czym zdjąć (panel rysuje
            // nakładka, a ta jest właśnie ukrywana).
            overlay.dismissPanel()
            overlay.resetControls()
            overlay.visibility = View.GONE
        } else {
            overlay.visibility = View.VISIBLE
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        SDLOpenTouch.setImmersionMode(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Immersion trzyma SDLActivity; tu tylko odświeżenie ustawień modułu.
        if (hasFocus) {
            TouchSettings.reloadSettings(applicationContext)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Silnik nie umie wrócić do czystego stanu w tym samym procesie;
        // zabijamy proces, żeby następne uruchomienie startowało od zera.
        System.exit(0)
    }
}
