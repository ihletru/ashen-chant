package com.ashenchant.game.touch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.ashenchant.game.HereticSettings
import com.ashenchant.game.engine.NativeEngine
import java.io.File
import kotlin.math.hypot

/**
 * Własna nakładka sterująca Heretic (zastępuje natywne UI rozgrywki).
 *
 * Układ (spec Milosza, 15.10.2026):
 *  - LEWA kolumna: USE w SAMYM dolnym rogu, obok DRUGI SPUST (FIRE2), a NAD
 *    nimi drążek chodzenia (analogFwd / analogSide),
 *  - PRAWA kolumna: FIRE w dolnym rogu (odsunięty od krawędzi o pół średnicy),
 *    NAD nim drążek rozglądania (tempo obrotu; poza okręgiem kręci dalej
 *    samo — patrz niżej),
 *  - górny lewy róg, od lewej: INV, WP-, WP+ (stykają się, bez nachodzenia),
 *  - górny prawy róg, od prawej: MENU (przycisk wsteczny silnika) i SET
 *    (panel ustawień sterowania rysowany przez tę nakładkę),
 *  - pasek ekwipunku (ikony: ◀ ▶ użyj upuść) rozsunięty na boki, w zasięgu
 *    kciuków — PREV/NEXT przy drążku chodzenia, USE/DROP przy drążku
 *    rozglądania; pojawia się po naciśnięciu INV i zamyka po dotknięciu
 *    pustego pola. Ekwipunek
 *    obsługujemy SAMI: natywna grupa ekwipunku silnika jest pod nakładką
 *    bezużyteczna (zasłonięta, pożera dotknięcia), a jej flaga `enabled` nie
 *    wraca do false po `animateOut` (zakomentowane w TouchControls.cpp), więc
 *    nie da się jej wykryć. Stąd PORT_ACT_SHOW_INV nie jest w ogóle używane.
 *
 * OBA drążki (chodzenie i rozglądanie) idą przez ten sam WIRTUALNY ŚRODEK
 * (pomysł Milosza 15.10.2026). Wychylenie liczymy od środka widocznego okręgu,
 * więc palec za okręgiem daje pełne tempo i trwa także wtedy, gdy palec stoi.
 * Gdy palec ZMIENIA KIERUNEK ruchu, wirtualny środek przeskakuje pod palec —
 * dzięki temu zwrot działa od razu, bez przeprowadzania palca przez środek
 * okręgu. Szczegóły i pułapki: klasa `VirtualStick` niżej.
 *
 * Kontrakty ze stosu natywnego:
 *  - doAction(1/0, NativeEngine.PORT_ACT_*) — wciśnięcie/puszczenie akcji,
 *  - PORT_ACT_ATTACK=13, USE=11, NEXT_WEP=16, PREV_WEP=17, INVPREV=21,
 *    INVNEXT=22, INVUSE=19, INVDROP=20.
 *
 * UWAGA: nakładka leży NAD powierzchnią GL (addContentView w GameActivity)
 * i pożera WSZYSTKIE dotknięcia (onTouchEvent zawsze zwraca true) — inaczej
 * przeciekłyby do natywnej warstwy sterowania.
 */
class TouchOverlayView(context: Context) : View(context) {

    private companion object {
        /**
         * Martwa strefa strefy rozglądania — mała, żeby reagowała od razu.
         * ⚠️ Martwa strefa chodzenia jest USTAWIENIEM ([moveDeadzone]), nie stałą.
         */
        const val LOOK_DEADZONE = 0.05f

        /**
         * Rozglądanie idzie trybem JOYSTICK, czyli TEMPEM obrotu na tik.
         * ⚠️ Wymóg Milosza (15.10.2026): ruch ma się ZACZYNAĆ proporcjonalnie
         * w okręgu, ale gdy palec wyjdzie ZA krawędź — obrót ma LEcieć SAM,
         * dopóki palec jest trzymany. Dlatego wychylenie jest przycinane do
         * promienia (`cl`), więc poza okręgiem tempo zostaje na maksimum
         * i trwa — bez podnoszenia palca. Tryb MOUSE (przeciąganie) tego NIE
         * daje: tam obrót kończy się razem z ruchem palca.
         * ⚠️ cmd->yaw to SHORT (16-bitowy kąt: 65536 = 360°) — ponad ±32767
         * wartość się ZAWIJA. Punkt odniesienia z silnika: obrót klawiatury
         * 640 → 123°/s, `lookspeed[0] = 450` → 86°/s.
         */
        /**
         * ⚠️ Charakterystyka jest LINIOWA — świadomie, po dwóch podejściach.
         * Krzywa z wykładnikiem < 1 („mniejsza skala różnic") podnosiła środek
         * zakresu i dawała BEZWŁADNOŚĆ: po cofnięciu palca obrót trzymał tempo
         * aż do okolic środka okręgu, a tam łapał odwrotny kierunek (zgłoszone
         * przez Milosza 15.10.2026: „to niedorzeczne"). Liniowo tempo idzie za
         * palcem natychmiast. Zamiast krzywej zakres ściskamy od góry —
         * patrz LOOK_YAW_FULL / LOOK_PITCH_FULL.
         */
        /**
         * ⚠️ WIRTUALNY ŚRODEK (pomysł Milosza 15.10.2026). Domyślnie wychylenie
         * liczymy od środka WIDOCZNEGO okręgu — więc wyjechanie palcem za okrąg
         * daje pełne tempo i kręci dalej także wtedy, gdy palec stoi. Wirtualny
         * środek NIE podąża za palcem: on PRZESKAKUJE pod palec dopiero w chwili
         * zmiany kierunku ruchu. Dzięki temu zmiana kierunku działa od razu,
         * bez przeprowadzania palca przez środek okręgu (to była wada trybu
         * pozycyjnego), a trzymanie palca nieruchomo nadal kręci.
         */
        const val DIRECTION_MIN_STEP_DP = 4f

        const val LOOK_YAW_FULL = 0.42f
        const val LOOK_PITCH_FULL = 0.38f

    }

    private val density = resources.displayMetrics.density

    // ---------------------------------------------------------------------
    // Ustawienia sterowania. Ten sam zestaw kluczy co ekran „Controls"
    // w launcherze — panel „SET" w grze edytuje je NA ŻYWO i zapisuje do
    // prefsów, więc wyjście z gry nie jest potrzebne (uwaga Milosza
    // 15.10.2026: „trzeba wyjść z gry by przesunąć suwak").
    // ⚠️ Wszystkie są `var` — panel zmienia je w trakcie gry.
    // ---------------------------------------------------------------------

    /** Czułość GŁÓWNA (mnożnik obu osi), procent. */
    private var masterSensPct: Int = opt(
        HereticSettings.KEY_LOOK_SENS, HereticSettings.LOOK_SENS_DEFAULT,
        HereticSettings.LOOK_SENS_MIN, HereticSettings.LOOK_SENS_MAX,
    )

    /** Czułość obrotu w poziomie, procent. */
    private var turnSensPct: Int = opt(
        HereticSettings.KEY_TURN_SENS, HereticSettings.LOOK_SENS_DEFAULT,
        HereticSettings.LOOK_SENS_MIN, HereticSettings.LOOK_SENS_MAX,
    )

    /** Czułość pochylenia w pionie, procent. */
    private var pitchSensPct: Int = opt(
        HereticSettings.KEY_PITCH_SENS, HereticSettings.LOOK_SENS_DEFAULT,
        HereticSettings.LOOK_SENS_MIN, HereticSettings.LOOK_SENS_MAX,
    )

    /** Ile czułości zostaje przy trzymanym spuście, procent. */
    private var precisionSensPct: Int = opt(
        HereticSettings.KEY_PRECISION_SENS, HereticSettings.PRECISION_SENS_DEFAULT,
        HereticSettings.PRECISION_SENS_MIN, HereticSettings.PRECISION_SENS_MAX,
    )

    /** Martwa strefa drążka chodzenia, procent promienia. */
    private var moveDeadzonePct: Int = opt(
        HereticSettings.KEY_MOVE_DEADZONE, HereticSettings.MOVE_DEADZONE_DEFAULT,
        0, HereticSettings.MOVE_DEADZONE_MAX,
    )

    private var invertPitchOn: Boolean =
        HereticSettings.getBoolOption(context, HereticSettings.KEY_INVERT_PITCH, false)

    private var precisionShootOn: Boolean =
        HereticSettings.getBoolOption(context, HereticSettings.KEY_PRECISION_SHOOT, true)

    /** Mnożnik czułości głównej (100% = 1.0). */
    private val lookScale: Float get() = masterSensPct / 100f
    private val turnScale: Float get() = turnSensPct / 100f
    private val pitchScale: Float get() = pitchSensPct / 100f
    private val precisionScale: Float get() = precisionSensPct / 100f
    private val moveDeadzone: Float get() = moveDeadzonePct / 100f

    /** Czy trzymamy spust — wtedy działa zwolnienie czułości. */
    private var firing = false

    private fun opt(key: String, def: Int, min: Int, max: Int): Int =
        HereticSettings.getIntOption(context, key, def).coerceIn(min, max)

    private enum class Zone {
        NONE, MOVE, LOOK, FIRE, FIRE2, USE, NEXT, PREV, INV, MENU,
        INV_PREV, INV_NEXT, INV_USE, INV_DROP, SET, PANEL,
    }

    /** Obie strefy spustu wołają tę samą akcję silnika. */
    private fun Zone.isFire(): Boolean = this == Zone.FIRE || this == Zone.FIRE2

    /** Wiersz panelu ustawień — rozpoznawany po [kind]. */
    private enum class Row {
        MASTER, TURN, PITCH, AIM_TOGGLE, AIM_SENS, DEADZONE, INVERT, DONE,
    }

    private class PanelRow(
        val kind: Row,
        val label: String,
        val slider: Boolean,
        val min: Int,
        val max: Int,
    ) {
        var left = 0f
        var top = 0f
        var right = 0f
        var bottom = 0f

        /** Tor suwaka (dla `slider = true`). */
        var trackLeft = 0f
        var trackRight = 0f
        var trackY = 0f

        fun contains(x: Float, y: Float): Boolean =
            x >= left && x <= right && y >= top && y <= bottom
    }

    private class TouchState {
        var zone: Zone = Zone.NONE
        var row: PanelRow? = null
    }

    private val pointers = HashMap<Int, TouchState>()

    private data class Circle(
        val cx: Float,
        val cy: Float,
        val r: Float,
        val zone: Zone,
        val label: String,
        val icon: String? = null,
    )

    private val buttons = ArrayList<Circle>()

    /**
     * Pasek ekwipunku rysowany przez NASZĄ nakładkę. Natywna grupa ekwipunku
     * silnika jest bezużyteczna pod nakładką (zasłonięta i pożera dotknięcia),
     * a jej flaga `enabled` nie wraca do false po `animateOut` — więc nie da się
     * jej sensownie wykryć. Dlatego cztery akcje ekwipunku wołamy wprost.
     */
    private val invButtons = ArrayList<Circle>()
    private var invOpen = false

    private var moveStickCx = 0f
    private var moveStickCy = 0f
    private var moveStickR = 0f
    private var moveKnobX = 0f
    private var moveKnobY = 0f
    private var moveActive = false

    private var lookStickCx = 0f
    private var lookStickCy = 0f
    private var lookStickR = 0f
    private var lookKnobX = 0f
    private var lookKnobY = 0f
    private var lookActive = false

    /**
     * WIRTUALNY ŚRODEK drążka — wspólny dla chodzenia i rozglądania
     * (Milosz 15.10.2026: „teraz chodzenie ustaw tak samo jak obracanie").
     *
     * Wychylenie liczymy od środka WIDOCZNEGO okręgu, więc wyjechanie palcem za
     * okrąg daje pełne tempo i trwa także wtedy, gdy palec stoi. Środek NIE
     * podąża za palcem — PRZESKAKUJE pod palec w chwili zmiany kierunku ruchu,
     * dzięki czemu zwrot działa od razu, bez przeprowadzania palca przez środek.
     *
     * ⚠️ Kierunek oceniamy na OKNIE ruchu (`acc`), nie na pojedynczym zdarzeniu:
     * przy powolnym ruchu palec idzie 2–3 px na zdarzenie i próg nigdy nie
     * byłby przekroczony (to był realny błąd — patrz `docs/stack-techniczny.md`).
     */
    private class VirtualStick {
        var originX = 0f
        var originY = 0f
        private var lastX = 0f
        private var lastY = 0f
        private var prevX = 0f
        private var prevY = 0f
        private var accX = 0f
        private var accY = 0f

        /** Wychylenie od wirtualnego środka — czytane po `deflect`. */
        var dx = 0f
        var dy = 0f

        fun reset(cx: Float, cy: Float, x: Float, y: Float) {
            originX = cx
            originY = cy
            lastX = x
            lastY = y
            prevX = 0f
            prevY = 0f
            accX = 0f
            accY = 0f
            dx = 0f
            dy = 0f
        }

        fun deflect(x: Float, y: Float, minStep: Float) {
            accX += x - lastX
            accY += y - lastY
            lastX = x
            lastY = y
            if (hypot(accX, accY) > minStep) {
                val hadPrev = hypot(prevX, prevY) > 0f
                // Kąt między oknami ruchu > 90° ⇒ zmiana kierunku.
                if (hadPrev && (accX * prevX + accY * prevY) < 0f) {
                    originX = x
                    originY = y
                }
                prevX = accX
                prevY = accY
                accX = 0f
                accY = 0f
            }
            dx = x - originX
            dy = y - originY
        }
    }

    private val moveStick = VirtualStick()
    private val lookStick = VirtualStick()

    /**
     * PANEL USTAWIEŃ W GRZE (Milosz 15.10.2026: „trzeba wyjść z gry by
     * przesunąć suwak - nie wiem czy to nie będzie stresować ludzi").
     *
     * Ten sam zestaw co ekran „Controls" w launcherze, ale na wierzchu
     * rozgrywki — bez wychodzenia z gry. Otwarcie panelu PAUZUJE grę
     * (`executeCommand("pause")` → `CCMD(pause)` w `g_game.cpp`; przy zamknięciu
     * wysyłamy to samo, bo komenda PRZEŁĄCZA stan).
     * ⚠️ Skutek uboczny: gdy gracz sam zapauzował przez MENU silnika, otwarcie
     * panelu OD pauzuje. Rzadkie i samo się naprawia przy zamknięciu.
     * ⚠️ Cały wiersz suwaka jest chwytliwy (wartość liczona z pozycji X), nie
     * tylko gałka — na kciuk jest to dużo pewniejsze niż trafianie w kółko.
     */
    private var panelOpen = false
    private val panelRows = ArrayList<PanelRow>()
    private var panelLeft = 0f
    private var panelTop = 0f
    private var panelRight = 0f
    private var panelBottom = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 12f * density
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    // Teksty panelu ustawień — osobne farby, bo mają inny align i kolor.
    private val panelTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFCC66.toInt()
        textAlign = Paint.Align.LEFT
    }
    private val panelLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
    }
    private val panelValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFCC66.toInt()
        textAlign = Paint.Align.RIGHT
    }
    private val panelDonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF14120F.toInt()
        textAlign = Paint.Align.CENTER
    }

    /** Cache ikon z katalogu PNG-ów silnika (klucz = nazwa bez rozszerzenia). */
    private val iconCache = HashMap<String, Bitmap?>()

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        keepScreenOn = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutControls(w, h)
    }

    private fun layoutControls(w: Int, h: Int) {
        buttons.clear()
        val pad = 24f * density
        val gap = 12f * density

        val stickR = 64f * density

        // PRAWY spust odsunięty od drążka w stronę środka ekranu o pół średnicy
        // FIRE (spec Milosza 15.10.2026) — drążek zostaje w narożniku.
        // ⚠️ Lewa strona rządzi się inną regułą: USE wraca w sam róg, a na jego
        // dawnym miejscu stoi drugi spust (patrz niżej).
        val fireR = 46f * density
        val shift = fireR

        // LEWA kolumna: USE w SAMYM rogu, obok DRUGI SPUST, a drążek chodzenia
        // nad nimi (pomysł Milosza 15.10.2026: „fire button both sides - to jest
        // genialne"). Dwa spusty pozwalają strzelać kciukiem, który akurat nie
        // prowadzi celowania — ten sam wzorzec co w referencji, gdzie obok
        // `attack` istnieje `attack2` (RectF(3,5,6,8) w siatce 26×16).
        val useR = 40f * density
        val useCx = pad + useR
        val useCy = h - useR - pad

        val fire2Cx = pad + useR * 2f + gap + fireR
        val fire2Cy = h - fireR - pad

        moveStickR = stickR
        moveStickCx = pad + stickR
        // ⚠️ Drążek liczony od NAJWYŻSZEGO elementu dolnego rzędu (FIRE2 ma ten
        // sam promień co FIRE), dzięki temu oba drążki wypadają na tej samej
        // wysokości — inaczej chodzenie i rozglądanie byłyby na różnych
        // poziomach, co widać i przeszkadza.
        moveStickCy = h - pad - fireR * 2f - gap - moveStickR
        moveKnobX = moveStickCx
        moveKnobY = moveStickCy

        // PRAWA kolumna: FIRE w dolnym rogu, drążek rozglądania NAD nim.
        val fireCx = w - pad - stickR - shift
        val fireCy = h - fireR - pad

        lookStickR = stickR
        lookStickCx = w - pad - stickR
        lookStickCy = fireCy - fireR - gap - lookStickR
        lookKnobX = lookStickCx
        lookKnobY = lookStickCy

        // Górny lewy róg, od lewej: INV, WP-, WP+. Stykają się (środki co 2r).
        val topR = 34f * density
        val topCy = topR + pad
        val invCx = pad + topR
        val prevCx = invCx + topR * 2f
        val nextCx = invCx + topR * 4f

        // MENU — górny prawy róg; SET (ustawienia sterowania) na lewo od niego.
        val menuR = 28f * density
        val menuCx = w - menuR - pad
        val menuCy = menuR + pad

        val setR = 28f * density
        val setCx = menuCx - menuR - gap - setR
        val setCy = menuCy

        buttons.add(Circle(invCx, topCy, topR, Zone.INV, "INV"))
        buttons.add(Circle(prevCx, topCy, topR, Zone.PREV, "WP-"))
        buttons.add(Circle(nextCx, topCy, topR, Zone.NEXT, "WP+"))
        buttons.add(Circle(useCx, useCy, useR, Zone.USE, "USE"))
        buttons.add(Circle(fireCx, fireCy, fireR, Zone.FIRE, "FIRE"))
        // Drugi spust — lewy kciuk. Ta sama akcja co FIRE (PORT_ACT_ATTACK).
        buttons.add(Circle(fire2Cx, fire2Cy, fireR, Zone.FIRE2, "FIRE"))
        buttons.add(Circle(menuCx, menuCy, menuR, Zone.MENU, "MENU"))
        buttons.add(Circle(setCx, setCy, setR, Zone.SET, "SET"))

        layoutPanel(w, h)

        // Pasek ekwipunku — rozsunięty na boki, żeby był w zasięgu kciuków:
        // PREV/NEXT przy drążku chodzenia, USE/DROP przy drążku rozglądania.
        // ⚠️ Ikony: WŁASNE, wygenerowane modelem w stylu gry (`tp_*.png` w assets,
        // kopiowane do filesDir). NIE używamy `inventory_*.png` / `inv.png` z
        // pakietu — to jedna ikona-placeholder (identyczny md5 dla pięciu nazw).
        invButtons.clear()
        val invR = 38f * density
        val invGap = 12f * density

        val invPrevCx = moveStickCx + moveStickR + invGap + invR
        invButtons.add(Circle(invPrevCx, moveStickCy, invR, Zone.INV_PREV, "PREV", "tp_prev"))
        invButtons.add(
            Circle(invPrevCx + invR * 2f + invGap, moveStickCy, invR, Zone.INV_NEXT, "NEXT", "tp_next")
        )

        val invUseCx = lookStickCx - lookStickR - invGap - invR
        invButtons.add(Circle(invUseCx, lookStickCy, invR, Zone.INV_USE, "USE", "tp_use"))
        invButtons.add(
            Circle(invUseCx - invR * 2f - invGap, lookStickCy, invR, Zone.INV_DROP, "DROP", "tp_drop")
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawStick(canvas, moveStickCx, moveStickCy, moveStickR, moveKnobX, moveKnobY, moveActive)
        drawStick(canvas, lookStickCx, lookStickCy, lookStickR, lookKnobX, lookKnobY, lookActive)

        for (b in buttons) {
            // INV świeci, gdy pasek ekwipunku jest otwarty.
            val pressed = pointers.values.any { it.zone == b.zone } || (b.zone == Zone.INV && invOpen)
            drawButton(canvas, b, pressed)
        }

        if (invOpen) {
            for (b in invButtons) {
                val pressed = pointers.values.any { it.zone == b.zone }
                drawButton(canvas, b, pressed)
            }
        }

        if (panelOpen) drawPanel(canvas)
    }

    // ---------------------------------------------------------------------
    // Panel ustawień sterowania (rysowany na wierzchu rozgrywki).
    // ---------------------------------------------------------------------

    /** Układa panel w DWÓCH kolumnach — w poziomie na wysokość wchodzą 4 wiersze. */
    private fun layoutPanel(w: Int, h: Int) {
        panelRows.clear()
        val pad = 24f * density
        val inner = 16f * density
        val colGap = 16f * density
        val rowGap = 8f * density
        val titleH = 28f * density

        val panelW = minOf(w - 2f * pad, 720f * density)
        val colW = (panelW - 2f * inner - colGap) / 2f

        // Gdyby ekran był niski, ściskamy wiersze, żeby panel nie wyszedł za kant.
        val maxH = h - 2f * pad
        var rowH = 46f * density
        val needed = titleH + 4f * rowH + 3f * rowGap + 2f * inner
        if (needed > maxH) {
            rowH = ((maxH - titleH - 3f * rowGap - 2f * inner) / 4f)
                .coerceAtLeast(26f * density)
        }

        panelLeft = (w - panelW) / 2f
        panelRight = panelLeft + panelW
        panelTop = (h - (titleH + 4f * rowH + 3f * rowGap + 2f * inner)) / 2f
        panelBottom = panelTop + titleH + 4f * rowH + 3f * rowGap + 2f * inner

        val leftCol = panelLeft + inner
        val rightCol = leftCol + colW + colGap
        val firstY = panelTop + inner + titleH

        fun add(kind: Row, label: String, slider: Boolean, min: Int, max: Int, colX: Float, idx: Int) {
            val r = PanelRow(kind, label, slider, min, max)
            r.left = colX
            r.right = colX + colW
            r.top = firstY + idx * (rowH + rowGap)
            r.bottom = r.top + rowH
            r.trackLeft = r.left
            r.trackRight = r.right
            r.trackY = r.bottom - 12f * density
            panelRows.add(r)
        }

        add(Row.MASTER, "Overall", true, HereticSettings.LOOK_SENS_MIN, HereticSettings.LOOK_SENS_MAX, leftCol, 0)
        add(Row.TURN, "Turning", true, HereticSettings.LOOK_SENS_MIN, HereticSettings.LOOK_SENS_MAX, leftCol, 1)
        add(Row.PITCH, "Up / down", true, HereticSettings.LOOK_SENS_MIN, HereticSettings.LOOK_SENS_MAX, leftCol, 2)
        add(Row.INVERT, "Invert up/down", false, 0, 0, leftCol, 3)

        add(Row.AIM_TOGGLE, "Slow aim on fire", false, 0, 0, rightCol, 0)
        add(Row.AIM_SENS, "Aim speed", true, HereticSettings.PRECISION_SENS_MIN, HereticSettings.PRECISION_SENS_MAX, rightCol, 1)
        add(Row.DEADZONE, "Stick dead zone", true, 0, HereticSettings.MOVE_DEADZONE_MAX, rightCol, 2)
        add(Row.DONE, "Done", false, 0, 0, rightCol, 3)
    }

    /** Aktualna wartość wiersza (procent) — tylko dla `slider = true`. */
    private fun rowValue(r: PanelRow): Int = when (r.kind) {
        Row.MASTER -> masterSensPct
        Row.TURN -> turnSensPct
        Row.PITCH -> pitchSensPct
        Row.AIM_SENS -> precisionSensPct
        Row.DEADZONE -> moveDeadzonePct
        else -> 0
    }

    /** Wiersz jest nieaktywny, gdy zwolnienie czułości jest wyłączone. */
    private fun rowEnabled(r: PanelRow): Boolean = r.kind != Row.AIM_SENS || precisionShootOn

    private fun rowIsOn(r: PanelRow): Boolean = when (r.kind) {
        Row.INVERT -> invertPitchOn
        Row.AIM_TOGGLE -> precisionShootOn
        else -> false
    }

    private fun panelRowAt(x: Float, y: Float): PanelRow? =
        panelRows.firstOrNull { it.contains(x, y) }

    private fun drawPanel(canvas: Canvas) {
        // Przygaszenie rozgrywki — panel ma być czytelny na każdym tle.
        fillPaint.color = 0xB0000000.toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)

        val radius = 14f * density
        fillPaint.color = 0xF0141210.toInt()
        canvas.drawRoundRect(
            RectF(panelLeft, panelTop, panelRight, panelBottom), radius, radius, fillPaint,
        )
        strokePaint.color = 0x66FFCC66.toInt()
        strokePaint.strokeWidth = 2f * density
        canvas.drawRoundRect(
            RectF(panelLeft, panelTop, panelRight, panelBottom), radius, radius, strokePaint,
        )

        panelTitlePaint.textSize = 15f * density
        canvas.drawText(
            "Controls",
            panelLeft + 16f * density,
            panelTop + 16f * density + 20f * density,
            panelTitlePaint,
        )

        for (r in panelRows) {
            val on = rowEnabled(r)
            val alpha = if (on) 0xFF else 0x55
            panelLabelPaint.alpha = alpha
            panelValuePaint.alpha = alpha
            panelLabelPaint.textSize = 12f * density
            panelValuePaint.textSize = 12f * density

            val textY = r.top + 15f * density

            if (r.slider) {
                canvas.drawText(r.label, r.left, textY, panelLabelPaint)
                canvas.drawText("${rowValue(r)}%", r.right, textY, panelValuePaint)

                strokePaint.color = ((alpha / 3) shl 24) or 0x00FFFFFF
                strokePaint.strokeWidth = 4f * density
                canvas.drawLine(r.trackLeft, r.trackY, r.trackRight, r.trackY, strokePaint)

                val t = ((rowValue(r) - r.min).toFloat() / (r.max - r.min).toFloat())
                    .coerceIn(0f, 1f)
                val kx = r.trackLeft + t * (r.trackRight - r.trackLeft)

                fillPaint.color = (alpha shl 24) or 0x00FFCC66
                canvas.drawLine(r.trackLeft, r.trackY, kx, r.trackY, fillPaint)
                canvas.drawCircle(kx, r.trackY, 9f * density, fillPaint)
                strokePaint.color = ((alpha / 2) shl 24) or 0x00FFFFFF
                strokePaint.strokeWidth = 2f * density
                canvas.drawCircle(kx, r.trackY, 9f * density, strokePaint)
            } else if (r.kind == Row.DONE) {
                fillPaint.color = 0x66FFCC66.toInt()
                canvas.drawRoundRect(RectF(r.left, r.top, r.right, r.top + 34f * density), 8f * density, 8f * density, fillPaint)
                panelDonePaint.textSize = 14f * density
                canvas.drawText(
                    "Done",
                    (r.left + r.right) / 2f,
                    r.top + 23f * density,
                    panelDonePaint,
                )
            } else {
                canvas.drawText(r.label, r.left, textY, panelLabelPaint)

                // Przełącznik: pill z kropką po prawej stronie wiersza.
                val pillR = 12f * density
                val pillCx = r.right - pillR
                val pillCy = r.top + 10f * density
                fillPaint.color = if (rowIsOn(r)) 0xCCFFCC66.toInt() else 0x55000000
                canvas.drawRoundRect(
                    RectF(pillCx - pillR * 1.7f, pillCy - pillR, pillCx + pillR * 1.7f, pillCy + pillR),
                    pillR, pillR, fillPaint,
                )
                fillPaint.color = if (rowIsOn(r)) 0xFF14120F.toInt() else 0xAAFFFFFF.toInt()
                canvas.drawCircle(
                    if (rowIsOn(r)) pillCx + pillR * 0.7f else pillCx - pillR * 0.7f,
                    pillCy, pillR * 0.75f, fillPaint,
                )
            }
        }
    }

    private fun drawButton(canvas: Canvas, b: Circle, pressed: Boolean) {
        fillPaint.color = if (pressed) 0xAA8C2F2F.toInt() else 0x44000000
        strokePaint.color = 0x88FFFFFF.toInt()
        canvas.drawCircle(b.cx, b.cy, b.r, fillPaint)
        canvas.drawCircle(b.cx, b.cy, b.r, strokePaint)

        val bmp = b.icon?.let { icon(it) }
        if (bmp != null) {
            val half = b.r * 0.62f
            val dst = RectF(b.cx - half, b.cy - half, b.cx + half, b.cy + half)
            canvas.drawBitmap(bmp, null, dst, iconPaint)
        } else {
            canvas.drawText(
                b.label,
                b.cx,
                b.cy - (textPaint.descent() + textPaint.ascent()) / 2f,
                textPaint,
            )
        }
    }

    /**
     * Ikona z katalogu PNG-ów silnika. `EngineUtils.copyPngAssets` kopiuje je
     * z assets do `filesDir` przy starcie gry, więc czytamy leniwie i NIE
     * cache'ujemy braku — przy pierwszej klatce plików może jeszcze nie być.
     */
    private fun icon(name: String): Bitmap? {
        if (iconCache.containsKey(name)) return iconCache[name]
        val f = File(context.filesDir, "$name.png")
        if (!f.exists()) return null
        val bmp = BitmapFactory.decodeFile(f.absolutePath)
        iconCache[name] = bmp
        return bmp
    }

    private fun drawStick(canvas: Canvas, cx: Float, cy: Float, r: Float, kx: Float, ky: Float, active: Boolean) {
        fillPaint.color = 0x33000000
        strokePaint.color = 0x66FFFFFF
        canvas.drawCircle(cx, cy, r, fillPaint)
        canvas.drawCircle(cx, cy, r, strokePaint)
        fillPaint.color = if (active) 0x88FFCC66.toInt() else 0x55FFFFFF
        canvas.drawCircle(kx, ky, r * 0.42f, fillPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val id = event.getPointerId(idx)
                val x = event.getX(idx)
                val y = event.getY(idx)
                val st = TouchState().apply { zone = hitTest(x, y) }

                // Dotknięcie poza panelem zamyka go (obok przycisku „Done").
                if (panelOpen && st.zone == Zone.NONE) {
                    closePanel()
                    return true
                }

                // Dotknięcie pustego pola zamyka pasek ekwipunku (jak natywne
                // „tapnięcie poza") i nie robi nic więcej.
                if (invOpen && st.zone == Zone.NONE) {
                    invOpen = false
                    invalidate()
                    return true
                }

                pointers[id] = st
                when (st.zone) {
                    Zone.MOVE -> {
                        moveActive = true
                        // Wirtualny środek startuje w środku widocznego okręgu.
                        moveStick.reset(moveStickCx, moveStickCy, x, y)
                        updateMoveStick(x, y)
                    }
                    Zone.LOOK -> {
                        lookActive = true
                        lookStick.reset(lookStickCx, lookStickCy, x, y)
                        updateLookStick(x, y)
                    }
                    Zone.FIRE, Zone.FIRE2 -> {
                        firing = true
                        NativeEngine.doAction(true, NativeEngine.PORT_ACT_ATTACK)
                    }
                    Zone.USE -> NativeEngine.doAction(true, NativeEngine.PORT_ACT_USE)
                    Zone.NEXT -> NativeEngine.doAction(true, NativeEngine.PORT_ACT_NEXT_WEP)
                    Zone.PREV -> NativeEngine.doAction(true, NativeEngine.PORT_ACT_PREV_WEP)
                    Zone.INV -> invOpen = !invOpen
                    Zone.MENU -> NativeEngine.backButton()
                    Zone.INV_PREV -> NativeEngine.doAction(true, NativeEngine.PORT_ACT_INVPREV)
                    Zone.INV_NEXT -> NativeEngine.doAction(true, NativeEngine.PORT_ACT_INVNEXT)
                    Zone.INV_USE -> NativeEngine.doAction(true, NativeEngine.PORT_ACT_INVUSE)
                    Zone.INV_DROP -> NativeEngine.doAction(true, NativeEngine.PORT_ACT_INVDROP)
                    Zone.SET -> {
                        // SET jest tapnięciem, nie trzymaniem — nie zapisujemy
                        // wskaźnika, żeby otwarcie panelu nie zostawiło strefy.
                        pointers.remove(id)
                        openPanel()
                    }
                    Zone.PANEL -> {
                        val row = panelRowAt(x, y)
                        st.row = row
                        if (row != null) onPanelPress(row, x)
                    }
                    Zone.NONE -> {}
                }
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val st = pointers[id] ?: continue
                    val x = event.getX(i)
                    val y = event.getY(i)
                    when (st.zone) {
                        Zone.MOVE -> updateMoveStick(x, y)
                        Zone.LOOK -> updateLookStick(x, y)
                        Zone.PANEL -> st.row?.let { onPanelDrag(it, x) }
                        else -> {}
                    }
                }
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val idx = event.actionIndex
                val id = event.getPointerId(idx)
                val st = pointers.remove(id)
                if (st != null) {
                    when (st.zone) {
                        Zone.MOVE -> releaseMoveStick()
                        Zone.LOOK -> releaseLookStick()
                        Zone.FIRE, Zone.FIRE2 -> {
                            NativeEngine.doAction(false, NativeEngine.PORT_ACT_ATTACK)
                            // ⚠️ `firing` zdejmujemy TYLKO, gdy puszczony jest
                            // OSTATNI spust — inaczej zwolnienie jednego z dwóch
                            // wyłączałoby precyzyjne celowanie, choć gracz wciąż
                            // trzyma drugi. `pointers` nie zawiera już puszczonego.
                            firing = pointers.values.any { it.zone.isFire() }
                        }
                        Zone.USE -> NativeEngine.doAction(false, NativeEngine.PORT_ACT_USE)
                        Zone.NEXT -> NativeEngine.doAction(false, NativeEngine.PORT_ACT_NEXT_WEP)
                        Zone.PREV -> NativeEngine.doAction(false, NativeEngine.PORT_ACT_PREV_WEP)
                        Zone.INV_PREV -> NativeEngine.doAction(false, NativeEngine.PORT_ACT_INVPREV)
                        Zone.INV_NEXT -> NativeEngine.doAction(false, NativeEngine.PORT_ACT_INVNEXT)
                        Zone.INV_USE -> NativeEngine.doAction(false, NativeEngine.PORT_ACT_INVUSE)
                        Zone.INV_DROP -> NativeEngine.doAction(false, NativeEngine.PORT_ACT_INVDROP)
                        // Ustawienia zapisujemy dopiero po puszczeniu palca —
                        // nie co zdarzenie ruchu (prefsy to plik na dysku).
                        Zone.PANEL -> persistSettings()
                        else -> {}
                    }
                }
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    resetControls()
                }
                invalidate()
            }
        }
        return true
    }

    /**
     * Puść wszystkie wciśnięte strefy i wyzeruj drążki. Wołane, gdy nakładka
     * ma zejść spod palca (silnik otwiera własne UI) — inaczej gra dostałaby
     * utknięty przycisk (np. chód) albo ciągły obrót kamery.
     */
    fun resetControls() {
        var fireWasDown = false
        pointers.values.forEach { s ->
            when (s.zone) {
                Zone.FIRE, Zone.FIRE2 -> {
                    fireWasDown = true
                    NativeEngine.doAction(false, NativeEngine.PORT_ACT_ATTACK)
                }
                Zone.USE -> NativeEngine.doAction(false, NativeEngine.PORT_ACT_USE)
                Zone.NEXT -> NativeEngine.doAction(false, NativeEngine.PORT_ACT_NEXT_WEP)
                Zone.PREV -> NativeEngine.doAction(false, NativeEngine.PORT_ACT_PREV_WEP)
                Zone.INV_PREV -> NativeEngine.doAction(false, NativeEngine.PORT_ACT_INVPREV)
                Zone.INV_NEXT -> NativeEngine.doAction(false, NativeEngine.PORT_ACT_INVNEXT)
                Zone.INV_USE -> NativeEngine.doAction(false, NativeEngine.PORT_ACT_INVUSE)
                Zone.INV_DROP -> NativeEngine.doAction(false, NativeEngine.PORT_ACT_INVDROP)
                else -> {}
            }
        }
        if (fireWasDown) firing = false
        pointers.clear()
        invOpen = false
        releaseMoveStick()
        releaseLookStick()
        invalidate()
    }

    /**
     * Najbliższy kontroler wygrywa (odległość liczona względem własnego
     * promienia dotyku), więc strefy mogą na siebie nachodzić bez pomyłek.
     * Poza kontrolerami ekran jest niemy — dotknięcia nadal są pożerane.
     */
    private fun hitTest(x: Float, y: Float): Zone {
        // Panel ustawień zasłania rozgrywkę — dopóki jest otwarty, nic poza nim
        // nie odpowiada (inaczej gracz chodziłby, ustawiając czułość).
        if (panelOpen) {
            return if (panelRowAt(x, y) != null) Zone.PANEL else Zone.NONE
        }

        var best = Zone.NONE
        var bestScore = 1f

        fun consider(zone: Zone, cx: Float, cy: Float, hitR: Float) {
            val score = hypot(x - cx, y - cy) / hitR
            if (score <= 1f && score < bestScore) {
                bestScore = score
                best = zone
            }
        }

        consider(Zone.MOVE, moveStickCx, moveStickCy, moveStickR * 1.6f)
        consider(Zone.LOOK, lookStickCx, lookStickCy, lookStickR * 1.6f)
        for (b in buttons) consider(b.zone, b.cx, b.cy, b.r * 1.25f)
        if (invOpen) for (b in invButtons) consider(b.zone, b.cx, b.cy, b.r * 1.25f)

        return best
    }

    // ---------------------------------------------------------------------
    // Obsługa panelu ustawień.
    // ---------------------------------------------------------------------

    /**
     * Otwarcie panelu: puszczamy wszystko, co trzyma gracz (inaczej zostałby
     * utknięty chód albo ciągły obrót), i PAUZUJEMY grę — ustawianie czułości
     * z żywym potworem obok nie ma sensu.
     */
    private fun openPanel() {
        resetControls()
        panelOpen = true
        NativeEngine.executeCommand("pause")
        invalidate()
    }

    /**
     * Zamknięcie panelu, gdy silnik przejmuje ekran (nakładka schodzi).
     * ⚠️ Bez tego gra zostałaby ZAPAUZOWANA bez widocznego panelu — gracz nie
     * miałby czym zdjąć pauzy, bo panel rysuje nasza nakładka, a ta jest GONE.
     */
    fun dismissPanel() {
        if (panelOpen) closePanel()
    }

    /** Zamknięcie panelu: zapis ustawień i zdjęcie pauzy (ta sama komenda). */
    private fun closePanel() {
        panelOpen = false
        persistSettings()
        NativeEngine.executeCommand("pause")
        invalidate()
    }

    private fun onPanelPress(row: PanelRow, x: Float) {
        when (row.kind) {
            Row.DONE -> closePanel()
            Row.AIM_TOGGLE -> {
                precisionShootOn = !precisionShootOn
                invalidate()
            }
            Row.INVERT -> {
                invertPitchOn = !invertPitchOn
                invalidate()
            }
            else -> if (rowEnabled(row)) {
                applySlider(row, x)
                invalidate()
            }
        }
    }

    private fun onPanelDrag(row: PanelRow, x: Float) {
        if (row.slider && rowEnabled(row)) {
            applySlider(row, x)
            invalidate()
        }
    }

    /** Wartość z pozycji X — cały tor jest chwytliwy, nie tylko gałka. */
    private fun applySlider(row: PanelRow, x: Float) {
        val span = row.trackRight - row.trackLeft
        val t = ((x - row.trackLeft) / span).coerceIn(0f, 1f)
        val v = (row.min + t * (row.max - row.min)).toInt().coerceIn(row.min, row.max)
        when (row.kind) {
            Row.MASTER -> masterSensPct = v
            Row.TURN -> turnSensPct = v
            Row.PITCH -> pitchSensPct = v
            Row.AIM_SENS -> precisionSensPct = v
            Row.DEADZONE -> moveDeadzonePct = v
            else -> {}
        }
    }

    /** Zapis WSZYSTKICH ustawień sterowania — ten sam zestaw kluczy co launcher. */
    private fun persistSettings() {
        HereticSettings.setIntOption(context, HereticSettings.KEY_LOOK_SENS, masterSensPct)
        HereticSettings.setIntOption(context, HereticSettings.KEY_TURN_SENS, turnSensPct)
        HereticSettings.setIntOption(context, HereticSettings.KEY_PITCH_SENS, pitchSensPct)
        HereticSettings.setIntOption(context, HereticSettings.KEY_PRECISION_SENS, precisionSensPct)
        HereticSettings.setIntOption(context, HereticSettings.KEY_MOVE_DEADZONE, moveDeadzonePct)
        HereticSettings.setBoolOption(context, HereticSettings.KEY_INVERT_PITCH, invertPitchOn)
        HereticSettings.setBoolOption(context, HereticSettings.KEY_PRECISION_SHOOT, precisionShootOn)
    }

    private fun updateMoveStick(x: Float, y: Float) {
        // Ten sam wirtualny środek co w rozglądaniu: wychylenie od widocznego
        // okręgu, a przy zmianie kierunku środek przeskakuje pod palec.
        moveStick.deflect(x, y, DIRECTION_MIN_STEP_DP * density)
        val dx = moveStick.dx
        val dy = moveStick.dy
        val dist = hypot(dx, dy)
        val cl = if (dist > moveStickR) moveStickR / dist else 1f
        moveKnobX = moveStickCx + dx * cl
        moveKnobY = moveStickCy + dy * cl

        val speed = deflection((dist / moveStickR), moveDeadzone)
        val ux = if (dist > 0f) dx / dist else 0f
        val uy = if (dist > 0f) dy / dist else 0f
        // Do przodu (y ujemny na ekranie) = dodatni FWD.
        NativeEngine.analogFwd(-uy * speed)
        NativeEngine.analogSide(ux * speed)
    }

    /**
     * Wychylenie 0..1 po odjęciu martwej strefy, przeskalowane do 0..1.
     * ⚠️ LINIOWO — bez krzywej. Krzywa z wykładnikiem < 1 podnosiła środek
     * zakresu i dawała BEZWŁADNOŚĆ: po cofnięciu palca tempo trzymało się
     * aż do okolic środka okręgu, a tam łapało odwrotny kierunek
     * (Milosz 15.10.2026: „to niedorzeczne"). Liniowo tempo idzie za palcem
     * natychmiast — tak ma być.
     */
    private fun deflection(mag: Float, deadzone: Float): Float =
        ((mag - deadzone) / (1f - deadzone)).coerceIn(0f, 1f)

    private fun releaseMoveStick() {
        moveActive = false
        moveKnobX = moveStickCx
        moveKnobY = moveStickCy
        NativeEngine.analogFwd(0f)
        NativeEngine.analogSide(0f)
    }

    /**
     * Rozglądanie drążkiem: wychylenie daje TEMPO obrotu, a nie obrót.
     * Wychylenie przycinamy do promienia, więc gdy palec wyjdzie za okrąg,
     * tempo zostaje na maksimum i obrót LEci SAM, dopóki palec jest trzymany —
     * tego wymagał Milosz (15.10.2026).
     * ⚠️ ZNAK — ustalony pomiarem na urządzeniu: dodatnie `cmd->yaw` = obrót
     * w LEWO (`G_AddViewAngle` robi `LocalViewAngle -= yaw`, a cmd->yaw =
     * +posłana wartość), więc „prawo = +x" trzeba ZANEGOWAĆ. Pochylenie idzie
     * BEZ negacji — Milosz zgłosił zamienione tylko lewo/prawo, a po odwróceniu
     * obu kanałów góra/dół się zamieniły, czyli pitch był poprawny.
     */
    /**
     * Rozglądanie z WIRTUALNYM środkiem (pomysł Milosza 15.10.2026).
     * Środek startuje w środku widocznego okręgu, więc wychylenie = wychylenie
     * palca względem okręgu (poza okręgiem pełne tempo i kręci dalej, także gdy
     * palec stoi). Gdy palec ZMIENIA KIERUNEK ruchu, środek przeskakuje pod
     * palec — dzięki temu zwrot działa natychmiast, bez przeprowadzania palca
     * przez środek okręgu.
     * ⚠️ ZNAK — ustalony pomiarem: dodatnie `cmd->yaw` = obrót w LEWO
     * (`G_AddViewAngle` robi `LocalViewAngle -= yaw`), więc yaw ZANEGOWANY,
     * pitch bez negacji.
     */
    private fun updateLookStick(x: Float, y: Float) {
        // Wirtualny środek: wychylenie od widocznego okręgu, a przy zmianie
        // kierunku ruchu środek przeskakuje pod palec (patrz VirtualStick).
        lookStick.deflect(x, y, DIRECTION_MIN_STEP_DP * density)
        val dx = lookStick.dx
        val dy = lookStick.dy
        val dist = hypot(dx, dy)
        val cl = if (dist > lookStickR) lookStickR / dist else 1f
        lookKnobX = lookStickCx + dx * cl
        lookKnobY = lookStickCy + dy * cl

        val rate = deflection((dist / lookStickR), LOOK_DEADZONE)
        val ux = if (dist > 0f) dx / dist else 0f
        val uy = if (dist > 0f) dy / dist else 0f

        // Czułość: główna (suwak w grze) × oś × zwolnienie przy strzale.
        val aim = lookScale * (if (firing && precisionShootOn) precisionScale else 1f)

        NativeEngine.analogYaw(
            NativeEngine.LOOK_MODE_JOYSTICK,
            -ux * rate * LOOK_YAW_FULL * aim * turnScale,
        )
        NativeEngine.analogPitch(
            NativeEngine.LOOK_MODE_JOYSTICK,
            uy * rate * LOOK_PITCH_FULL * aim * pitchScale * (if (invertPitchOn) -1f else 1f),
        )
    }

    private fun releaseLookStick() {
        lookActive = false
        lookKnobX = lookStickCx
        lookKnobY = lookStickCy
        NativeEngine.analogYaw(NativeEngine.LOOK_MODE_JOYSTICK, 0f)
        NativeEngine.analogPitch(NativeEngine.LOOK_MODE_JOYSTICK, 0f)
    }
}
