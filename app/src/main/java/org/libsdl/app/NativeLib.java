package org.libsdl.app;

import android.view.KeyEvent;

import com.beloko.touchcontrols.ControlInterface;

/**
 * Szew JNI do glue'u silnika (Clibs_OpenTouch, android_jni_inc.cpp).
 *
 * To JEDYNE miejsce, gdzie Java dotyka silnika. Nazwy klas i metod są kontraktem
 * binarnym: glue rozszerza JAVA_FUNC(x) do Java_org_libsdl_app_NativeLib_x, więc
 * nazwa pakietu, nazwa klasy i sygnatury metod native MUSZĄ tu zostać. Nie wolno
 * ich zmieniać bez przebudowania stosu natywnego.
 *
 * Klasa implementuje też ControlInterface (kontrakt modułu opentouch-input) —
 * dzięki temu ControlInterpreter dostaje ten sam obiekt silnika co główna ścieżka.
 *
 * Uwagi z budowy stosu:
 *  - init(...) NIGDY nie wraca — pętla główna silnika przejmuje wątek SDL.
 *  - native `keypress` NIE jest eksportowane przez ten glue — wołanie go rzuca
 *    UnsatisfiedLinkError. Klawiatura idzie własną ścieżką SDL (SDLActivity).
 *    Dlatego tego deklaratora tu nie ma, a keyPress_if jest no-opem.
 */
public class NativeLib implements ControlInterface {

	// --- cykl życia silnika ---

	/**
	 * Start silnika. Nigdy nie wraca.
	 *
	 * @param graphics_dir katalog na grafikę sterowania (filesDir + "/")
	 * @param options      flagi silnika (0x10 = GAME_OPTION_GLES3)
	 * @param wheelNbr     liczba slotów koła broni
	 * @param args         argumenty linii poleceń (np. -iwad ...)
	 * @param game         identyfikator gry dla UI dotykowego (3 = HERETIC,
	 *                     GAME_TYPE_HERETIC z touch_interface.cpp; steruje
	 *                     m.in. przyciskiem ekwipunku i kołem broni)
	 * @param path         katalog gry (base dir gracza)
	 * @param secondaryPath drugi katalog gry (może być null)
	 * @param logFilename  plik logu silnika
	 * @param nativeLibs   katalog z natywnymi .so (ApplicationInfo.nativeLibraryDir)
	 * @param userFiles    katalog plików użytkownika (save'e, ustawienia)
	 * @param tmpFiles     katalog tymczasowy (cacheDir)
	 * @param sourceDir    sourceDir APK
	 * @param resDir       katalog zasobów silnika (base dir + "/res")
	 */
	public static native int init(String graphics_dir, int options, int wheelNbr,
			String[] args, int game, String path, String secondaryPath,
			String logFilename, String nativeLibs, String userFiles,
			String tmpFiles, String sourceDir, String resDir);

	public static native void setScreenSize(int width, int height);

	public static native void setFramebufferSize(int width, int height, int aspect);

	// --- wejście ---

	public static native boolean touchEvent(int action, int pid, float x, float y);

	/**
	 * Akcja z kontraktu PORT_ACT_* (patrz Clibs_OpenTouch/port_act_defs.h):
	 * 11 = USE, 12 = JUMP, 13 = ATTACK, 16/17 = NEXT/PREV weapon,
	 * 25 = CONSOLE, 27 = SHOW_INV, 30 = MAP.
	 */
	public static native int doAction(int state, int action);

	public static native void backButton();

	// Kanały analogowe ruchu (wartość i surowa).
	public static native void analogFwd(float v, float raw);

	public static native void analogSide(float v, float raw);

	// Kanały analogowe rozglądania (mode + wartość + surowa).
	public static native void analogPitch(int mode, float v, float raw);

	public static native void analogYaw(int mode, float v, float raw);

	public static native void weaponWheelSettings(int useMoveStick, int mode, int autoTimeout);

	// --- konfiguracja / pomocnicze ---

	public static native int audioOverride(int freq, int samples);

	public static native int loadTouchSettings(String filename);

	public static native int saveTouchSettings(String filename);

	public static native int executeCommand(String command);

	public static native int renderControls();

	// --- adapter ControlInterface (kontrakt modułu opentouch-input) ---

	@Override
	public void initTouchControls_if(String pngPath, int width, int height) {
		// Sterowanie dotykowe startuje natywnie w NativeLib.init().
	}

	@Override
	public boolean touchEvent_if(int action, int pid, float x, float y) {
		return touchEvent(action, pid, x, y);
	}

	@Override
	public void keyPress_if(int down, int qkey, int unicode) {
		// No-op: klawisze z klawiatury sprzętowej trafiają do silnika własną
		// ścieżką SDL (SDLActivity.onNativeKeyDown). Glue naszego stosu nie
		// eksportuje natywnego `keypress` — wołanie rzuca UnsatisfiedLinkError.
	}

	@Override
	public void doAction_if(int state, int action) {
		doAction(state, action);
	}

	@Override
	public void analogFwd_if(float v) {
		analogFwd(v, v);
	}

	@Override
	public void analogSide_if(float v) {
		analogSide(v, v);
	}

	@Override
	public void analogPitch_if(int mode, float v) {
		analogPitch(mode, v, v);
	}

	@Override
	public void analogYaw_if(int mode, float v) {
		analogYaw(mode, v, v);
	}

	@Override
	public void setTouchSettings_if(float alpha, float strafe, float fwd, float pitch, float yaw, int other) {
		// Skalowanie patrzenia/ruchu sterowania dotykowego konfiguruje natywne
		// UI silnika; nie ma tu co przekazywać.
	}

	@Override
	public void quickCommand_if(String command) {
		executeCommand(command);
	}

	// --- mapa klawiszy: Android keyCode -> SDL scancode (USB HID 0x07).
	// Używana przez własne mostki klawiaturowe; silnik dostaje klawisze własną
	// ścieżką SDL, ta mapa jest dla jawnego mapowania w obu naturach UI. ---

	public static final int SDL_SCANCODE_A = 4;
	public static final int SDL_SCANCODE_1 = 30;
	public static final int SDL_SCANCODE_0 = 39;
	public static final int SDL_SCANCODE_RETURN = 40;
	public static final int SDL_SCANCODE_ESCAPE = 41;
	public static final int SDL_SCANCODE_BACKSPACE = 42;
	public static final int SDL_SCANCODE_TAB = 43;
	public static final int SDL_SCANCODE_SPACE = 44;
	public static final int SDL_SCANCODE_MINUS = 45;
	public static final int SDL_SCANCODE_EQUALS = 46;
	public static final int SDL_SCANCODE_COMMA = 54;
	public static final int SDL_SCANCODE_PERIOD = 55;
	public static final int SDL_SCANCODE_SLASH = 56;
	public static final int SDL_SCANCODE_F1 = 58;
	public static final int SDL_SCANCODE_UP = 82;
	public static final int SDL_SCANCODE_DOWN = 81;
	public static final int SDL_SCANCODE_LEFT = 80;
	public static final int SDL_SCANCODE_RIGHT = 79;

	public int mapKey(int acode, int unicode) {
		if (acode >= KeyEvent.KEYCODE_A && acode <= KeyEvent.KEYCODE_Z) {
			return SDL_SCANCODE_A + (acode - KeyEvent.KEYCODE_A);
		}
		if (acode >= KeyEvent.KEYCODE_1 && acode <= KeyEvent.KEYCODE_9) {
			return SDL_SCANCODE_1 + (acode - KeyEvent.KEYCODE_1);
		}
		switch (acode) {
			case KeyEvent.KEYCODE_0: return SDL_SCANCODE_0;
			case KeyEvent.KEYCODE_DPAD_CENTER:
			case KeyEvent.KEYCODE_ENTER: return SDL_SCANCODE_RETURN;
			case KeyEvent.KEYCODE_ESCAPE:
			case KeyEvent.KEYCODE_BACK: return SDL_SCANCODE_ESCAPE;
			case KeyEvent.KEYCODE_SPACE: return SDL_SCANCODE_SPACE;
			case KeyEvent.KEYCODE_DEL: return SDL_SCANCODE_BACKSPACE;
			case KeyEvent.KEYCODE_DPAD_UP: return SDL_SCANCODE_UP;
			case KeyEvent.KEYCODE_DPAD_DOWN: return SDL_SCANCODE_DOWN;
			case KeyEvent.KEYCODE_DPAD_LEFT: return SDL_SCANCODE_LEFT;
			case KeyEvent.KEYCODE_DPAD_RIGHT: return SDL_SCANCODE_RIGHT;
			case KeyEvent.KEYCODE_PERIOD: return SDL_SCANCODE_PERIOD;
			case KeyEvent.KEYCODE_COMMA: return SDL_SCANCODE_COMMA;
			case KeyEvent.KEYCODE_SLASH: return SDL_SCANCODE_SLASH;
			case KeyEvent.KEYCODE_MINUS: return SDL_SCANCODE_MINUS;
			case KeyEvent.KEYCODE_EQUALS: return SDL_SCANCODE_EQUALS;
			case KeyEvent.KEYCODE_F1: return SDL_SCANCODE_F1;
			default:
				if (unicode > 0 && unicode < 128) {
					return Character.toLowerCase(unicode);
				}
		}
		return 0;
	}
}
