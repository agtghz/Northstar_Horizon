/*
 * Copyright 2024 Alexis Guma
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
package net.guma.northstar.horizon;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.ini4j.Wini;

/**
 * Provides mechanism to save and restore various settings to an INI file
 */
public class Settings {
    public static final String INI_FILE = "horizon.ini";

    public static final String SYSTEM = "SYSTEM";
    public static final String AUTO_COMMIT = "AutoCommit";
    public static final String FULL_SPEED = "FullSpeed";
    public static final String CONSOLE_OUT = "ConsoleOut";
    public static final String VIDEO_ADDRESS = "VideoAddress";

    public static final String WINDOW = "WINDOW";

    public static final String SCREEN_BG = "ScreenBGColor";
    public static final String SCREEN_FG = "ScreenFGColor";
    public static final String CONSOLE_BG = "ConsoleBGColor";
    public static final String CONSOLE_FG = "ConsoleFGColor";

    public static final String CONSOLE_FONT_NAME = "FontName";
    public static final String CONSOLE_FONT_SIZE_SMALL = "SmallFontSize";
    public static final String CONSOLE_FONT_SIZE_MEDIUM = "MediumFontSize";
    public static final String CONSOLE_FONT_SIZE_LARGE = "LargeFontSize";

    public static final String COLUMN = "WindowColumn";
    public static final String ROW = "WindowRow";
    public static final String CHARSET = "Charset";
    public static final String PIXELSIZE = "PixelSize";

    public static final Color DEFAULT_CONSOLE_FG = Color.BLACK;
    public static final Color DEFAULT_CONSOLE_BG = Color.WHITE;
    public static final Color DEFAULT_SCREEN_FG = Color.WHITE;
    public static final Color DEFAULT_SCREEN_BG = Color.BLACK;

    public static final String DRIVES = "DRIVES";
    public static final String DRIVE1_FILE = "Drive1File";
    public static final String DRIVE2_FILE = "Drive2File";
    public static final String DRIVE3_FILE = "Drive3File";
    public static final String DRIVE4_FILE = "Drive4File";
    public static final String DRIVE1_READONLY = "Drive1RO";
    public static final String DRIVE2_READONLY = "Drive2RO";
    public static final String DRIVE3_READONLY = "Drive3RO";
    public static final String DRIVE4_READONLY = "Drive4RO";

    public static final Map<Integer, String> DRIVE_FILE_MAP = new HashMap<Integer, String>();
    static {
        DRIVE_FILE_MAP.put(Integer.valueOf(1), DRIVE1_FILE);
        DRIVE_FILE_MAP.put(Integer.valueOf(2), DRIVE2_FILE);
        DRIVE_FILE_MAP.put(Integer.valueOf(3), DRIVE3_FILE);
        DRIVE_FILE_MAP.put(Integer.valueOf(4), DRIVE4_FILE);
    }

    public static final Map<Integer, String> DRIVE_READONLY_MAP = new HashMap<Integer, String>();
    static {
        DRIVE_READONLY_MAP.put(Integer.valueOf(1), DRIVE1_READONLY);
        DRIVE_READONLY_MAP.put(Integer.valueOf(2), DRIVE2_READONLY);
        DRIVE_READONLY_MAP.put(Integer.valueOf(3), DRIVE3_READONLY);
        DRIVE_READONLY_MAP.put(Integer.valueOf(4), DRIVE4_READONLY);
    }

    private Wini iniSettings;

    /**
     * Gets a string value for an option in a particular section
     * 
     * @param sectionName
     *            is the section in the INI to get the value from
     * @param optionName
     *            is the option whose value to return
     */
    public String get(Object sectionName, Object optionName) {
        return iniSettings.get(sectionName, optionName);
    }

    /**
     * Gets a value for an option in a particular section of the requested object type
     * 
     * @param sectionName
     *            is the section in the INI to get the value from
     * @param optionName
     *            is the option whose value to return
     * @param clazz
     *            is the object class the value is returned as
     */
    public <T> T get(Object sectionName, Object optionName, Class<T> clazz) {
        return iniSettings.get(sectionName, optionName, clazz);
    }

    /**
     * Sets and saves an INI setting with a particular name and value
     * 
     * @param sectionName
     *            is the section in the INI to put the value into
     * @param optionName
     *            is the name to assign a value to
     * @param value
     *            is the value to assign to the name
     */
    public void put(String sectionName, String optionName, Object value) {
        iniSettings.put(sectionName, optionName, value);
        writeSettings();
    }

    /**
     * Read all INI file settings from previous run.
     */
    public void readSettings() {
        try {
            File iniFile = new File(INI_FILE);
            iniFile.createNewFile();
            iniSettings = new Wini(iniFile);
        } catch (IOException ioe) {
            Horizon.displayErrorMessage("Unable to read INI settings: " + ioe.getMessage());
        }
    }

    /**
     * Save all INI file settings for next time.
     */
    public void writeSettings() {
        try {
            iniSettings.store();
        } catch (IOException ioe) {
            Horizon.displayErrorMessage("Unable to save INI settings: " + ioe.getMessage());
        }
    }

    /**
     * For the foreground or background color, for either the ScreenSplitter or Console, get any previous color setting
     * saved in the INI file and put that into our current color settings. If there are none yet in the INI file, then
     * default them to a hard-coded value.
     * 
     * @param colorSection
     *            which color, foreground or background, to retrieve
     * @return the color to set the foreground or background to
     */
    public static final Color getInitialColor(String colorSection) {
        Color color = null;
        int colorRGB = Horizon.getSettings().get(WINDOW, colorSection, int.class);
        if (colorRGB == 0) {
            // Color does not exist in saved settings, start off with a default value
            switch (colorSection) {
            case SCREEN_FG:
                color = DEFAULT_SCREEN_FG;
                break;
            case SCREEN_BG:
                color = DEFAULT_SCREEN_BG;
                break;
            case CONSOLE_FG:
                color = DEFAULT_CONSOLE_FG;
                break;
            case CONSOLE_BG:
                color = DEFAULT_CONSOLE_BG;
                break;
            }
            // Save this default in the settings for next time
            if (color != null) {
                Horizon.getSettings().put(WINDOW, colorSection, color.getRGB());
            }
        } else {
            // Color exists in saved settings, use it
            color = new Color(colorRGB);
        }
        return color;
    }
}