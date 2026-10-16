package com.ashenchant.game

import android.content.res.Resources
import com.beloko.touchcontrols.ActionInput
import com.beloko.touchcontrols.ControlConfig
import com.beloko.touchcontrols.ControlConfig.Type

/**
 * Lista akcji gamepada dla ControlInterpreter.
 *
 * Zestaw kodów PORT_ACT_* jest kontraktem z warstwą natywną
 * (Clibs_OpenTouch/port_act_defs.h) — silnik rozpoznaje akcję po numerze,
 * etykieta służy tylko UI konfiguratora. Zestaw = komplet obsługiwanych
 * przez UZDoom akcji (jak w stosie referencyjnym).
 */
object GamepadConfig {

    @JvmStatic
    fun getActions(res: Resources): ArrayList<ActionInput> {
        val actions = ArrayList<ActionInput>()

        actions.add(ActionInput("analog_look_pitch", res.getString(R.string.look_up_down_option), ControlConfig.ACTION_ANALOG_PITCH, Type.ANALOG))
        actions.add(ActionInput("analog_look_yaw", res.getString(R.string.look_left_right_option), ControlConfig.ACTION_ANALOG_YAW, Type.ANALOG))
        actions.add(ActionInput("analog_move_fwd", res.getString(R.string.forward_back_option), ControlConfig.ACTION_ANALOG_FWD, Type.ANALOG))
        actions.add(ActionInput("analog_move_strafe", res.getString(R.string.strafe_option), ControlConfig.ACTION_ANALOG_STRAFE, Type.ANALOG))
        actions.add(ActionInput("attack", res.getString(R.string.attack_option), ControlConfig.PORT_ACT_ATTACK, Type.BUTTON))
        actions.add(ActionInput("attack_alt", res.getString(R.string.attack_alt_option), ControlConfig.PORT_ACT_ALT_ATTACK, Type.BUTTON))
        actions.add(ActionInput("back", res.getString(R.string.move_back_option), ControlConfig.PORT_ACT_BACK, Type.BUTTON))
        actions.add(ActionInput("crouch", res.getString(R.string.crouch_option), ControlConfig.PORT_ACT_DOWN, Type.BUTTON))
        actions.add(ActionInput("custom_0", res.getString(R.string.custom_0_option), ControlConfig.PORT_ACT_CUSTOM_0, Type.BUTTON))
        actions.add(ActionInput("custom_1", res.getString(R.string.custom_1_option), ControlConfig.PORT_ACT_CUSTOM_1, Type.BUTTON))
        actions.add(ActionInput("custom_2", res.getString(R.string.custom_2_option), ControlConfig.PORT_ACT_CUSTOM_2, Type.BUTTON))
        actions.add(ActionInput("custom_3", res.getString(R.string.custom_3_option), ControlConfig.PORT_ACT_CUSTOM_3, Type.BUTTON))
        actions.add(ActionInput("custom_4", res.getString(R.string.custom_4_option), ControlConfig.PORT_ACT_CUSTOM_4, Type.BUTTON))
        actions.add(ActionInput("custom_5", res.getString(R.string.custom_5_option), ControlConfig.PORT_ACT_CUSTOM_5, Type.BUTTON))
        actions.add(ActionInput("fly_down", res.getString(R.string.fly_down_option), ControlConfig.PORT_ACT_FLY_DOWN, Type.BUTTON))
        actions.add(ActionInput("fly_up", res.getString(R.string.fly_up_option), ControlConfig.PORT_ACT_FLY_UP, Type.BUTTON))
        actions.add(ActionInput("fwd", res.getString(R.string.fwd_option), ControlConfig.PORT_ACT_FWD, Type.BUTTON))
        actions.add(ActionInput("inv_drop", res.getString(R.string.inv_drop_option), ControlConfig.PORT_ACT_INVDROP, Type.BUTTON))
        actions.add(ActionInput("inv_next", res.getString(R.string.inv_next_option), ControlConfig.PORT_ACT_INVNEXT, Type.BUTTON))
        actions.add(ActionInput("inv_prev", res.getString(R.string.inv_prev_option), ControlConfig.PORT_ACT_INVPREV, Type.BUTTON))
        actions.add(ActionInput("inv_use", res.getString(R.string.inv_use_option), ControlConfig.PORT_ACT_INVUSE, Type.BUTTON))
        actions.add(ActionInput("jump", res.getString(R.string.jump_option), ControlConfig.PORT_ACT_JUMP, Type.BUTTON))
        actions.add(ActionInput("left", res.getString(R.string.left_option), ControlConfig.PORT_ACT_MOVE_LEFT, Type.BUTTON))
        actions.add(ActionInput("look_left", res.getString(R.string.look_left_option), ControlConfig.PORT_ACT_LEFT, Type.BUTTON))
        actions.add(ActionInput("look_right", res.getString(R.string.look_right_option), ControlConfig.PORT_ACT_RIGHT, Type.BUTTON))
        actions.add(ActionInput("map_down", res.getString(R.string.map_down_option), ControlConfig.PORT_ACT_MAP_DOWN, Type.BUTTON))
        actions.add(ActionInput("map_left", res.getString(R.string.map_left_option), ControlConfig.PORT_ACT_MAP_LEFT, Type.BUTTON))
        actions.add(ActionInput("map_right", res.getString(R.string.map_right_option), ControlConfig.PORT_ACT_MAP_RIGHT, Type.BUTTON))
        actions.add(ActionInput("map_show", res.getString(R.string.map_show_option), ControlConfig.PORT_ACT_MAP, Type.BUTTON))
        actions.add(ActionInput("map_up", res.getString(R.string.map_up_option), ControlConfig.PORT_ACT_MAP_UP, Type.BUTTON))
        actions.add(ActionInput("map_zoomin", res.getString(R.string.map_zoomin_option), ControlConfig.PORT_ACT_MAP_ZOOM_IN, Type.BUTTON))
        actions.add(ActionInput("map_zoomout", res.getString(R.string.map_zoomout_option), ControlConfig.PORT_ACT_MAP_ZOOM_OUT, Type.BUTTON))
        actions.add(ActionInput("menu_back", res.getString(R.string.menu_back_option), ControlConfig.MENU_BACK, Type.MENU))
        actions.add(ActionInput("menu_down", res.getString(R.string.menu_down_option), ControlConfig.MENU_DOWN, Type.MENU))
        actions.add(ActionInput("menu_left", res.getString(R.string.menu_left_option), ControlConfig.MENU_LEFT, Type.MENU))
        actions.add(ActionInput("menu_right", res.getString(R.string.menu_right_option), ControlConfig.MENU_RIGHT, Type.MENU))
        actions.add(ActionInput("menu_select", res.getString(R.string.menu_select_option), ControlConfig.MENU_SELECT, Type.MENU))
        actions.add(ActionInput("menu_up", res.getString(R.string.menu_up_option), ControlConfig.MENU_UP, Type.MENU))
        actions.add(ActionInput("next_weapon", res.getString(R.string.next_weapon_option), ControlConfig.PORT_ACT_NEXT_WEP, Type.BUTTON))
        actions.add(ActionInput("prev_weapon", res.getString(R.string.prev_weapon_option), ControlConfig.PORT_ACT_PREV_WEP, Type.BUTTON))
        actions.add(ActionInput("quick_load", res.getString(R.string.quick_load_option), ControlConfig.PORT_ACT_QUICKLOAD, Type.BUTTON))
        actions.add(ActionInput("quick_save", res.getString(R.string.quick_save_option), ControlConfig.PORT_ACT_QUICKSAVE, Type.BUTTON))
        actions.add(ActionInput("right", res.getString(R.string.right_option), ControlConfig.PORT_ACT_MOVE_RIGHT, Type.BUTTON))
        actions.add(ActionInput("show_keys", res.getString(R.string.show_keys_option), ControlConfig.PORT_ACT_SHOW_KEYS, Type.BUTTON))
        actions.add(ActionInput("show_weap", res.getString(R.string.show_weap_option), ControlConfig.PORT_ACT_SHOW_WEAPONS, Type.BUTTON))
        actions.add(ActionInput("speed", res.getString(R.string.speed_option), ControlConfig.PORT_ACT_SPEED, Type.BUTTON))
        actions.add(ActionInput("strafe_on", res.getString(R.string.strafe_on_option), ControlConfig.PORT_ACT_STRAFE, Type.BUTTON))
        actions.add(ActionInput("use", res.getString(R.string.use_option), ControlConfig.PORT_ACT_USE, Type.BUTTON))

        return actions
    }
}
