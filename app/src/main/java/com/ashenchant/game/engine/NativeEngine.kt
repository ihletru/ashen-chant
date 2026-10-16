package com.ashenchant.game.engine

import org.libsdl.app.NativeLib

/**
 * Cienki wrapper nad szwem JNI (org.libsdl.app.NativeLib) dla warstwy Kotlin.
 *
 * Izoluje UI od kontraktu binarnego: gdy sygnatury native się zmienią,
 * poprawka jest w jednym miejscu. Wszystkie metody wołają statyczne natywne
 * funkcje NativeLib — działa to tylko w procesie :game, gdzie silnik już
 * przejął wątek (NativeLib.init nie wraca).
 *
 * Kody PORT_ACT_* (Clibs_OpenTouch/port_act_defs.h):
 *  11=USE, 12=JUMP, 13=ATTACK, 16=NEXT_WEP, 17=PREV_WEP, 19=INVUSE,
 *  20=INVDROP, 21=INVPREV, 22=INVNEXT, 25=CONSOLE, 27=SHOW_INV, 30=MAP.
 * Tryby rozglądania: 0=MOUSE (delta), 2=JOYSTICK.
 */
object NativeEngine {

    const val LOOK_MODE_MOUSE = 0
    const val LOOK_MODE_JOYSTICK = 2

    const val PORT_ACT_USE = 11
    const val PORT_ACT_JUMP = 12
    const val PORT_ACT_ATTACK = 13
    const val PORT_ACT_NEXT_WEP = 16
    const val PORT_ACT_PREV_WEP = 17
    const val PORT_ACT_INVUSE = 19
    const val PORT_ACT_INVDROP = 20
    const val PORT_ACT_INVPREV = 21
    const val PORT_ACT_INVNEXT = 22
    const val PORT_ACT_SHOW_INV = 27
    const val PORT_ACT_MAP = 30

    /** Wciśnięcie/puszczenie akcji (state=true → down). */
    @JvmStatic
    fun doAction(pressed: Boolean, action: Int) {
        NativeLib.doAction(if (pressed) 1 else 0, action)
    }

    /** Kanał analogowy ruchu do przodu (-1..1). */
    @JvmStatic
    fun analogFwd(v: Float) {
        NativeLib.analogFwd(v, v)
    }

    /** Kanał analogowy ruchu w bok (strafe, -1..1). */
    @JvmStatic
    fun analogSide(v: Float) {
        NativeLib.analogSide(v, v)
    }

    /** Rozglądanie pionowe (mode + delta). */
    @JvmStatic
    fun analogPitch(mode: Int, v: Float) {
        NativeLib.analogPitch(mode, v, v)
    }

    /** Rozglądanie poziome (mode + delta). */
    @JvmStatic
    fun analogYaw(mode: Int, v: Float) {
        NativeLib.analogYaw(mode, v, v)
    }

    /** Przycisk wstyczny silnika (escape/menu). */
    @JvmStatic
    fun backButton() {
        NativeLib.backButton()
    }

    /** Wykonanie polecenia konsoli silnika (np. „kill"). */
    @JvmStatic
    fun executeCommand(command: String) {
        NativeLib.executeCommand(command)
    }

    /** Informacja o rozmiarze ekranu (silnik renderuje w landscape). */
    @JvmStatic
    fun setScreenSize(width: Int, height: Int) {
        NativeLib.setScreenSize(width, height)
    }
}
