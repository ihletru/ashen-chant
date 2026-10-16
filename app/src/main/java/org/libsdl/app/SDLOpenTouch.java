package org.libsdl.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Message;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.beloko.touchcontrols.ControlInterpreter;
import com.beloko.touchcontrols.TouchSettings;
import com.ashenchant.game.EngineUtils;
import com.ashenchant.game.GamepadConfig;
import com.ashenchant.game.HereticSettings;
import com.ashenchant.game.game.GameActivity;

/**
 * Nasza implementacja haków, które woła zendowany org.libsdl.app2012.SDLActivity.
 *
 * SDLActivity wywołuje te statyczne metody w kluczowych punktach cyklu życia
 * (Setup, RunApplication, surfaceChanged, onTouchEvent, onKey, onPause, onResume,
 * CommandHandler, setBridge). Oryginał (AndroidCore emileb'a) rutował je do
 * pełnego frameworku UI; my łączymy wprost z silnikiem przez NativeLib
 * (NativeLib.init nie wraca — pętla główna silnika przejmuje wątek SDL).
 *
 * Kontrakt launchu z launchera Compose (Intent gry):
 *  - "args"     — argumenty linii poleceń silnika, ZAWSZE zawiera -iwad <ścieżka>
 *  - "game_path"— katalog bazy gry (tu siedzą res/, user_files/)
 *  - "res_div"  — dzielnik rozdzielczości renderowania (1 = pełna)
 */
public class SDLOpenTouch
{
    static final String TAG = "SDLOpenTouch";

    static float resDiv = 1.0f;
    static boolean divDone = false;

    // Silnik renderuje w stałej rozdzielczości landscape przez całą sesję (nie
    // umie zmienić rozmiaru po init); aktywność gry jest zablokowana na
    // sensorLandscape, więc proporcje nie zmieniają się w trakcie gry.
    static int fbWidth = 0;
    static int fbHeight = 0;

    // SDLActivity czyta te pola wprost (przy obracaniu ekranu) — nazwy są kontraktem.
    public static boolean swapMouseXY = false;
    public static boolean invertMouseX = false;
    public static boolean invertMouseY = false;

    static NativeLib engine;

    public static ControlInterpreter controlInterp;

    // Referencji ustawia SDLActivity przez setBridge, żeby ta klasa nie zależała
    // od klas specyficznych dla pakietu SDL.
    static View surfaceView;
    static Runnable audioPauseCallback;
    static Runnable audioResumeCallback;
    static Runnable enableRelativeMouseCallback;

    // GAME_TYPE_HERETIC z Clibs_OpenTouch/idtech1/touch_interface.cpp.
    // Decyduje m.in. o przycisku ekwipunku (inv) i obsłudze broni w UI dotykowym.
    static final int GAME_TYPE_HERETIC = 3;

    // Heretic ma 7 broni + jeden slot zapasu dla koła broni.
    static final int WEAPON_WHEEL_SLOTS = 8;

    public static void setBridge(View surface, Runnable audioPause, Runnable audioResume, Runnable enableRelativeMouse)
    {
        surfaceView = surface;
        audioPauseCallback = audioPause;
        audioResumeCallback = audioResume;
        enableRelativeMouseCallback = enableRelativeMouse;
    }

    public static void onPause(Context context)
    {
        if (audioPauseCallback != null)
            audioPauseCallback.run();
    }

    public static void onResume(Context context)
    {
        if (audioResumeCallback != null)
            audioResumeCallback.run();
    }

    public static void Setup(Activity activity, Intent intent)
    {
        HereticSettings.reloadSettings(activity.getApplicationContext());

        NativeConsoleBox.init(activity);

        // pełny ekran + nie wygaszaj ekranu
        activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setImmersionMode(activity);

        engine = new NativeLib();

        // Ustawienia gamepada pochodzą z modułu opentouch-input (własne prefy).
        TouchSettings.reloadSettings(activity.getApplicationContext());

        controlInterp = new ControlInterpreter(
                engine,
                GamepadConfig.getActions(activity.getResources()),
                TouchSettings.gamePadControlsFile,
                TouchSettings.gamePadEnabled);

        int div = intent.getIntExtra("res_div", 1);
        resDiv = 1.0f / div;
    }

    public static void RunApplication(Activity activity, Intent intent, float displayWidth, float displayHeight)
    {
        String args = intent.getStringExtra("args");
        if (args == null)
            args = "";

        // Rozdzielczość silnika jest stała przez całą sesję: zawsze wymiary
        // landscape powierzchni, niezależnie od orientacji launchu.
        fbWidth = (int) Math.max(displayWidth, displayHeight);
        fbHeight = (int) Math.min(displayWidth, displayHeight);

        // Wymuszamy backend GLES silnika (domyślny Vulkan nie pomieści
        // rysowanego w GL sterowania dotykowego). Framebuffer GLES bierze
        // rozmiar z vid_defwidth/vid_defheight, więc podajemy ustalony landscape.
        args += " +set vid_preferbackend 2 +set vid_fullscreen 1"
                + " +set vid_defwidth " + fbWidth
                + " +set vid_defheight " + fbHeight;

        String[] argsArray = EngineUtils.createArgs(args);

        String gamePath = intent.getStringExtra("game_path");

        int options = 0x10; // GAME_OPTION_GLES3
        int wheelNbr = WEAPON_WHEEL_SLOTS;
        int gameType = GAME_TYPE_HERETIC;

        // Nasza własna nakładka dotykowa (TouchOverlayView w GameActivity) jest
        // JEDYNYM sterowaniem rozgrywki — flaga jest BEZWARUNKOWA (decyzja
        // Milosza 15.10.2026: przełącznik usunięty z launchera). Natywne UI
        // gracza (sticki, przyciski, koło broni) startuje wyłączone — zostaje
        // natywne menu i ekwipunek.
        // Flaga GAME_OPTION_HERETIC_OWN_TOUCH_UI = 0x4000.
        options |= 0x4000;

        String userFiles = gamePath + "/user_files";
        new java.io.File(userFiles).mkdirs();
        String logFilename = userFiles + "/gzdoom_log.txt";
        String tmpFiles = activity.getCacheDir().getAbsolutePath();
        String sourceDir = activity.getApplicationContext().getApplicationInfo().sourceDir;
        String nativeSoPath = activity.getApplicationInfo().nativeLibraryDir;
        String pngFiles = activity.getFilesDir().getAbsolutePath();

        // Grafika sterowania dotykowego (PNG z assets) — musi leżeć przed init.
        EngineUtils.copyPngAssets(activity, pngFiles);

        Log.v(TAG, "native .so path = " + nativeSoPath);
        Log.v(TAG, "gamePath = " + gamePath);
        Log.v(TAG, "userFiles = " + userFiles);
        Log.v(TAG, "fb = " + fbWidth + "x" + fbHeight + " args = " + args);

        NativeLib.audioOverride(0, 0);

        // Jedynie secondaryPath może być null; każdy inny string jest
        // dereferencjowany bezwarunkowo w glue.
        String resDir = gamePath + "/res";

        // Nigdy nie wraca: pętla główna silnika działa na tym (wątku SDL).
        NativeLib.init(pngFiles + "/", options, wheelNbr, argsArray, gameType, gamePath,
                null, logFilename, nativeSoPath, userFiles, tmpFiles, sourceDir, resDir);
    }

    public static boolean surfaceChanged(Context context, SurfaceHolder holder, int width, int height)
    {
        Log.v(TAG, "surfaceChanged: " + width + " x " + height);

        if (resDiv != 1.0f && !divDone)
        {
            holder.setFixedSize((int) ((width * resDiv) + 0.5f), (int) ((height * resDiv) + 0.5f));
            divDone = true;
            return true;
        }

        NativeLib.setScreenSize(width, height);

        if (controlInterp != null)
            controlInterp.setScreenSize(width, height);

        if (enableRelativeMouseCallback != null)
            enableRelativeMouseCallback.run();

        return false;
    }

    public static boolean onTouchEvent(MotionEvent event)
    {
        return controlInterp != null && controlInterp.onTouchEvent(event);
    }

    public static boolean onKey(int keyCode, KeyEvent event)
    {
        int source = event.getSource();
        // Prawy przycisk myszy nie może być traktowany jak back.
        if ((source == InputDevice.SOURCE_MOUSE) || (source == InputDevice.SOURCE_MOUSE_RELATIVE))
        {
            return true;
        }

        // Back zawsze działa jak escape.
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (event.getAction() == KeyEvent.ACTION_DOWN)
            {
                NativeLib.backButton();
            }
            return true;
        }

        if (controlInterp == null)
            return false;

        if (event.getAction() == KeyEvent.ACTION_DOWN)
        {
            return controlInterp.onKeyDown(keyCode, event);
        }
        else if (event.getAction() == KeyEvent.ACTION_UP)
        {
            return controlInterp.onKeyUp(keyCode, event);
        }

        return false;
    }

    // Wysyłane przez natywną nadpisaną exit() (Clibs_OpenTouch
    // android_jni_inc.cpp) przy zakończeniu silnika; proces musi umrzeć, żeby
    // następne uruchomienie startowało z czystym stanem natywnym.
    static final int COMMAND_EXIT_APP = 0x8007;

    // Wysyłane przez kanał wibracji (haptyka obrażeń z naszej łatki silnika).
    // msg.obj to Integer z czasem w milisekundach.
    static final int COMMAND_VIBRATE = 0x8005;

    // Silnik przejął ekran (menu, ekrany tytułowe, intermisja, finał) albo
    // wrócił do rozgrywki. msg.obj to Integer: 1 = UI silnika aktywne
    // (nakładka dotykowa ma zejść spod palca), 0 = powrót do gry.
    static final int COMMAND_ENGINE_UI = 0x8009;

    public static boolean CommandHandler(Activity activity, Message msg)
    {
        if (msg.arg1 == COMMAND_EXIT_APP)
        {
            Log.v(TAG, "COMMAND_EXIT_APP: finishing");
            activity.finish();
            android.os.Process.killProcess(android.os.Process.myPid());
            return true;
        }
        if (msg.arg1 == COMMAND_VIBRATE)
        {
            // Pref czytamy wprost z dysku: proces :game nie startuje z
            // odświeżonym HereticSettings, więc statyczne pole bywa stare.
            if (HereticSettings.getBoolOption(activity, "vibrate", true))
            {
                int ms = (msg.obj instanceof Integer) ? (Integer) msg.obj : 30;
                try
                {
                    Vibrator v;
                    if (Build.VERSION.SDK_INT >= 31)
                    {
                        VibratorManager vm = (VibratorManager)
                            activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                        v = vm.getDefaultVibrator();
                    }
                    else
                    {
                        v = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
                    }
                    if (v != null && v.hasVibrator())
                    {
                        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                    }
                }
                catch (Exception e)
                {
                    Log.w(TAG, "vibrate failed", e);
                }
            }
            return true;
        }
        if (msg.arg1 == COMMAND_ENGINE_UI)
        {
            // Własna nakładka dotykowa nie może zasłaniać natywnego UI gry:
            // w trakcie menu/ekranu schodzi, po powrocie do rozgrywki wraca.
            if (activity instanceof GameActivity)
            {
                boolean engineUi = (msg.obj instanceof Integer) && ((Integer) msg.obj) != 0;
                ((GameActivity) activity).setEngineUiActive(engineUi);
            }
            return true;
        }
        return false;
    }

    public static void setImmersionMode(Activity activity)
    {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }
}
