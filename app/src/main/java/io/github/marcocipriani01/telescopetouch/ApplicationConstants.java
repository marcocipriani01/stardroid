/*
 * Copyright 2020 Marco Cipriani (@marcocipriani01)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.marcocipriani01.telescopetouch;

/**
 * A home for the application's global constants and preference keys.
 */
public class ApplicationConstants {

    public static final String APP_NAME = "Telescope.Touch";
    // Preference keys
    public static final String AUTO_MODE_PREF = "auto_mode";
    public static final String NO_WARN_MISSING_SENSORS_PREF = "no_warn_missing_sensors";
    public static final String BUNDLE_TARGET_NAME = "target_name";
    public static final String DISABLE_GYRO_PREF = "disable_gyro";
    public static final String REVERSE_MAGNETIC_Z_PREF = "reverse_magnetic_z";
    public static final String ROTATE_HORIZON_PREF = "rotate_horizon";
    public static final String LONGITUDE_PREF = "longitude";
    public static final String LATITUDE_PREF = "latitude";
    public static final String INDI_SERVERS_PREF = "INDI_SERVERS_PREF";
    public static final String INDI_PORT_PREF = "indi_port";
    public static final String NSD_PREF = "network_service_discovery";
    public static final String POLARIS_HEMISPHERE_PREF = "polaris_hemisphere";
    public static final String HEMISPHERE_AUTO = "hemisphere_auto";
    public static final String HEMISPHERE_NORTHERN = "hemisphere_northern";
    //public static final String HEMISPHERE_SOUTHERN = "hemisphere_southern";
    public static final String POLARIS_RETICLE_PREF = "polaris_finder_reticle";
    public static final String RETICLE_SKY_WATCHER = "reticle_skywatcher";
    //public static final String RETICLE_BIG_DIPPER = "reticle_big_dipper";
    public static final String MAGNETIC_DECLINATION_PREF = "use_magnetic_correction";
    public static final String NO_AUTO_LOCATE_PREF = "no_auto_locate";
    public static final String FORCE_GPS_PREF = "force_gps";
    public static final String COMPENSATE_PRECESSION_PREF = "compensate_precession";
    public static final String CATALOG_LIMIT_MAGNITUDE = "catalog_limit_mag";
    public static final String STRETCH_FITS_PREF = "stretch_fits_blob";
    public static final String SHOW_STARS_PREF = "show_stars_catalog";
    public static final String SHOW_DSO_PREF = "show_dso_catalog";
    public static final String SHOW_PLANETS_PREF = "show_planets_catalog";
    public static final String ONLY_VISIBLE_OBJECTS_PREF = "only_visible_catalog";
    public static final String NO_SHOW_CALIBRATION_DIALOG_PREF = "no_calibration_dialog";
    public static final String ALADIN_FORCE = "force_aladin";
    public static final String ALADIN_WELCOME = "aladin_welcome";
    public static final String ALADIN_J2000_NOTE = "aladin_j2000_note";
    public static final String VIZIER_WELCOME = "vizier_welcome";
    public static final String EXIT_ACTION_PREF = "app_exit_action";
    public static final String ACTION_DO_NOTHING = "do_nothing";
    public static final String ACTION_BACKGROUND_ALWAYS = "background_always";
    public static final String ACTION_BACKGROUND_IF_CONNECTED = "background_if_connected";
    public static final String ACTION_DISCONNECT_EXIT = "disconnect_and_exit";
    public static final String PICK_LOCATION_PREF = "pick_location_map";
    public static final String GOTO_DETAILS_LAST_TAB = "goto_details_tab";
    public static final String DARKER_MODE_KEY = "darker_mode";
    public static final String LAST_CALIBRATION_WARNING_PREF = "last_calibration_warning_time";
    public static final String GPS_OFF_NO_DIALOG_PREF = "gps_off_no_dialog";
    public static final String CCD_LOOP_DELAY_PREF = "ccd_loop_delay";
    public static final String JPG_QUALITY_PREF = "jpg_quality";
    public static final String WEB_MANAGER_INFO_PREF = "web_manager_info";
    public static final String WEB_MANAGER_PORT_PREF = "web_manager_port";
    public static final String AUTO_CONNECT_DEVICES_PREF = "auto_connect_devices";
    public static final String SKY_MAP_HIGH_REFRESH_PREF = "sky_map_high_refresh";
    public static final String SKY_MAP_HIGH_REFRESH_INFO_PREF = "sky_map_high_refresh_info";
}