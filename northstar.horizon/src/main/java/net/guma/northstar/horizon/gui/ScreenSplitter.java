/*
 * Copyright 2025 Alexis Guma
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
package net.guma.northstar.horizon.gui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import javax.swing.JFrame;
import javax.swing.JPanel;

import net.guma.northstar.horizon.Horizon;
import net.guma.northstar.horizon.Resources;
import net.guma.northstar.horizon.Settings;

/**
 * This is the main memory mapped display window, currently mapped to emulating a ScreenSplitter S-100 display adapter,
 * running at 40 rows by 88 columns, using a 5x8 character ROM (either graphics or scientific sets)
 *
 */
public class ScreenSplitter extends JPanel {

    public static final List<String> BOARD_ADDRESSES = Arrays
            .asList(new String[] { "0K", "8K", "16K", "24K", "32K", "40K", "48K", "56K" });

    public static final int[] FIRMWARE_START = new int[] { 0x0000, 0x2000, 0x4000, 0x6000, 0x8000, 0xA000, 0XC000,
            0xE000 };
    public static final int[] FIRMWARE_END = new int[] { 0x03FF, 0x23FF, 0x43FF, 0x63FF, 0x83FF, 0xA3FF, 0xC3FF,
            0xE3FF };

    public static final int[] DISPLAY_START = new int[] { 0x1000, 0x3000, 0x5000, 0x7000, 0x9000, 0xB000, 0xD000,
            0xF000 };
    public static final int[] DISPLAY_END = new int[] { 0x1FFF, 0x3FFF, 0x5FFF, 0x7FFF, 0x9FFF, 0xBFFF, 0xDFFF,
            0xFFFF };

    public static final int DEFAULT_FIRMWARE_SIZE = 2048;
    private static final int DEFAULT_BOARD_ADDRESS = 7;

    public static final String DEFAULT_CHARSET = "Graphics";

    private static final int CHAR_HEIGHT = 8;
    private static final int CHAR_WIDTH = 5;

    private static final int PAD_HEIGHT = 2;
    private static final int PAD_WIDTH = 2;

    private static final int ROWS = 40;
    private static final int COLUMNS = 88;

    private int boardAddress = DEFAULT_BOARD_ADDRESS;

    private int[] firmware;
    private int[] charset;
    private int[] screen;

    private boolean blink;
    private boolean changingCharset = false;

    private int pixelSize = 2;
    private int pixelHeight;
    private int pixelWidth;

    private boolean renderingDisabled = false;
    private String renderingErrorMsg1;
    private String renderingErrorMsg2;

    /**
     * Create the main ScreenSplitter video display window. At this time we also get access to the foreground and
     * background colors to use, either default ones or ones stored in our main settings INI file.
     */
    public ScreenSplitter() {
        initialize();
        setForeground(Settings.getInitialColor(Settings.SCREEN_FG));
        setBackground(Settings.getInitialColor(Settings.SCREEN_BG));
        int savedSize = Horizon.getSettings().get(Settings.WINDOW, Settings.PIXELSIZE, int.class);
        if (savedSize > 0) {
            pixelSize = savedSize;
        } else {
            pixelSize = 2;
            Horizon.getSettings().put(Settings.WINDOW, Settings.PIXELSIZE, pixelSize);
        }
        configureDimensions();
        setFocusable(true);
    }

    /**
     * Return the current pixel size number
     * 
     * @return the current pixel size
     */
    public int getPixelSize() {
        return pixelSize;
    }

    /**
     * Return the currently selected firmware starting address
     */
    public int getFirmwareStart() {
        return FIRMWARE_START[boardAddress];
    }

    /**
     * Return the currently selected firmware ending address
     */
    public int getFirmwareEnd() {
        return FIRMWARE_END[boardAddress];
    }

    /**
     * Return the currently selected display memory starting address
     */
    public int getDisplayStart() {
        return DISPLAY_START[boardAddress];
    }

    /**
     * Return the currently selected display memory ending address
     */
    public int getDisplayEnd() {
        return DISPLAY_END[boardAddress];
    }

    /**
     * Allows loading the selected ROM/RAM address start range.
     */
    public void loadBoardAddress() {
        if (Horizon.getSettings().get(Settings.SYSTEM, Settings.VIDEO_ADDRESS) == null) {
            // First time we have no parameter yet, default it.
            boardAddress = DEFAULT_BOARD_ADDRESS;
            Horizon.getSettings().put(Settings.SYSTEM, Settings.VIDEO_ADDRESS, boardAddress);
        } else {
            // Existing setting for board address, get (and validate) it.
            boardAddress = Horizon.getSettings().get(Settings.SYSTEM, Settings.VIDEO_ADDRESS, int.class);
            if ((boardAddress < 0) || (boardAddress > 7)) {
                boardAddress = DEFAULT_BOARD_ADDRESS;
                Horizon.getSettings().put(Settings.SYSTEM, Settings.VIDEO_ADDRESS, boardAddress);
            }
        }
    }

    /**
     * Returns the currently selected board address start range for the ROM/RAM
     */
    public int getBoardAddress() {
        return boardAddress;
    }

    /**
     * ScreenSplitter character set allows defining individual rows as ones that blink on and off. Every half second the
     * CPU execution loop flips this blink boolean that we use here to repaint these rows to flash on and off.
     */
    public void flipBlink() {
        blink = !blink;
        repaint();
    }

    /**
     * Read in a character set image and make it available to the display
     * 
     * @param charsetName
     *            is the name of the character set to load
     * @return true if character set valid, else false
     */
    public boolean loadCharset(String charsetName) {
        boolean valid = true;
        try {
            int[] newCharset = Resources.getHexResourceAsBinary(Resources.RESOURCE_CHARSET_DIR + charsetName + ".hex");
            if ((newCharset == null) || (newCharset.length < 1024)) {
                renderingDisabled = true;
                renderingErrorMsg1 = "Character set " + charsetName + " is incomplete";
                renderingErrorMsg2 = null;
                valid = false;
            } else {
                charset = newCharset;
            }
        } catch (Exception e) {
            renderingDisabled = true;
            renderingErrorMsg1 = "Failed reading ScreenSplitter charset " + charsetName;
            renderingErrorMsg2 = e.getMessage();
            valid = false;
        }
        repaint();
        return valid;
    }

    /**
     * Read a byte from the memory mapped range that represents the firmware
     * 
     * @param address
     *            is the firmware location to read from
     * @return the 8-bit value stored at the address in the firmware
     */
    public int readFirmwareByte(int address) {
        return firmware[address - getFirmwareStart()];
    }

    /**
     * Read a byte from the memory mapped range that represents the video screen
     * 
     * @param address
     *            is the screen location to read from
     * @return the 8-bit value stored at the address on the screen
     */
    public int readScreenByte(int address) {
        return screen[address - getDisplayStart()];
    }

    /**
     * Update a byte of screen memory with a new value
     * 
     * @param address
     *            the address of the byte to update
     * @param data
     *            is the 8-bit value to put into the address location
     */
    public void writeScreenByte(int address, int data) {
        int screenAddress = address - getDisplayStart();
        screen[screenAddress] = data;
        repaint();
    }

    /**
     * Called when we are rebooting the Horizon, reloads resources and resets various parameters to an initial state.
     */
    public void reboot() {
        initialize();
    }

    /**
     * Allow switching between the size of each pixel in the display, currently really only allowing values of 1 or 2.
     * Given a changed value, recompute the size of the video window, as well as the size of the enclosing main frame.
     * 
     * @param size,
     *            either 1 (small) or 2 (large)
     */
    public void resize(int size) {
        // Nothing to do if no change in size.
        if (pixelSize == size) {
            return;
        }

        // Capture the original size of the video window.
        int origHeight = getHeight();
        int origWidth = getWidth();

        // Choose the new size and recompute the new cell and window sizes.
        pixelSize = size;
        configureDimensions();

        // Now resize the main window frame that encloses our video window.
        // Easiest way is to size up and down by the amount the video window changed.
        JFrame frame = Horizon.getWorkspace().getMainFrame();
        int newHeight = frame.getHeight() + (getHeight() - origHeight);
        int newWidth = frame.getWidth() + (getWidth() - origWidth);
        frame.setPreferredSize(new Dimension(newWidth, newHeight));
        frame.pack();
    }

    /**
     * Calculate the height and width of a cell in pixels, as well as the overall dimensions of the video window, based
     * on the current pixel size we have chosen.
     */
    private void configureDimensions() {
        pixelHeight = (CHAR_HEIGHT * pixelSize) + PAD_HEIGHT;
        pixelWidth = (CHAR_WIDTH * pixelSize) + PAD_WIDTH;
        setSize((COLUMNS * pixelWidth), (ROWS * pixelHeight));
        setPreferredSize(getSize());
    }

    /**
     * Perform the basic initialization needed to get the ScreenSplitter working. Called both when we are first starting
     * up and whenever the system is rebooted.
     */
    private void initialize() {
        renderingDisabled = false;

        loadBoardAddress();
        screen = new int[(getDisplayEnd() - getDisplayStart()) + 1];
        // Not really important, but make screen contain random characters, just
        // like the real thing, until we boot up and clear the screen memory.
        Random screenRandom = new Random();
        for (int i = 0; i < screen.length; i++) {
            screen[i] = screenRandom.nextInt(256);
        }

        // Read in the current selected character set, graphics or scientific
        String charset = Horizon.getSettings().get(Settings.WINDOW, Settings.CHARSET);
        // If none previously selected, use default
        if (charset == null) {
            charset = DEFAULT_CHARSET;
            Horizon.getSettings().put(Settings.WINDOW, Settings.CHARSET, charset);
        }
        loadCharset(charset);

        // Read in the window library firmware, and adjust to reflect the selected ROM/ROM start addresses
        // A value of X# is replaced with ROM start address, Y# is replaced with RAM start address.
        char romAddressStart = String.format("%02X", (boardAddress * 0x20)).charAt(0);
        char videoAddressStart = String.format("%02X", ((boardAddress * 0x20) + 0x10)).charAt(0);
        List<String> hexList = null;
        try {
            hexList = Resources.getHexResourceAsList(Resources.RESOURCE_WINDOW_PACKAGE);
            for (int hexCnt = 0; hexCnt < hexList.size(); hexCnt++) {
                if (hexList.get(hexCnt).startsWith("X")) {
                    // Value that needs actual ROM position
                    hexList.set(hexCnt, hexList.get(hexCnt).replace('X', romAddressStart));
                }
                if (hexList.get(hexCnt).startsWith("Y")) {
                    // Value that needs actual RAM position
                    hexList.set(hexCnt, hexList.get(hexCnt).replace('Y', videoAddressStart));
                }
            }

            // Now convert the adjusted ROM hex values into the final binary integer array.
            try {
                firmware = Resources.convertHexListToBinary(hexList);
            } catch (Exception e) {
                renderingDisabled = true;
                renderingErrorMsg1 = "Failed converting ScreenSplitter firmware hex values to binary";
                renderingErrorMsg2 = e.getMessage();
                firmware = new int[DEFAULT_FIRMWARE_SIZE];
            }
        } catch (Exception e) {
            renderingDisabled = true;
            renderingErrorMsg1 = "Failed reading ScreenSplitter firmware " + Resources.RESOURCE_WINDOW_PACKAGE;
            renderingErrorMsg2 = e.getMessage();
            firmware = new int[DEFAULT_FIRMWARE_SIZE];
        }
    }

    /**
     * If set to true, the main screen window will show a rotating set of all of the current character set images.
     * Otherwise if false will display the normal contents of the video window.
     * 
     * @param changingCharset
     *            true if character set being changed and we need to display those characters
     */
    public void setChangingCharset(boolean changingCharset) {
        this.changingCharset = changingCharset;
    }

    /**
     * This is where the magic happens. We map the entire 40x88 area of the main window JPanel. Each character has a
     * pattern in the character set ROM in a 5x8 area, and we pad out the character to fit into a larger cell that looks
     * good given the specific resolution and pixel size. An extra bit in each character set row flags a row as
     * blinking, and we have a mechanism to do so every half second or so similar to the actual hardware. Characters 128
     * and up are the same as below, but are rendered in inverted colors.
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (renderingDisabled) {
            // Render disabled due to earlier error, display an error message instead
            Font currentFont = g.getFont();
            Float scale = 1F;
            switch (pixelSize) {
            case 2:
                scale = 1.4F;
                break;
            case 3:
                scale = 1.8F;
                break;
            }
            Font newFont = currentFont.deriveFont(currentFont.getSize() * scale);
            g.setFont(newFont);
            g.drawString("Error rendering ScreenSplitter display:", 10, 40);
            g.drawString(renderingErrorMsg1, 10, 80);
            if (renderingErrorMsg2 != null) {
                g.drawString(renderingErrorMsg2, 10, 120);
            }

        } else {
            // Rendering is running, draw the current state of the screen, unless we are changing the character set, in
            // which case we display a screen full of all of the characters.
            int cellchar;
            int charsetCounter = 0;
            boolean showCharset = changingCharset;
            try {
                for (int row = 0; row < ROWS; row++) {
                    for (int col = 0; col < COLUMNS; col++) {

                        // Compute values for character at a particular row and column position
                        if (showCharset) {
                            // Previewing character set, show next numeric character
                            cellchar = charsetCounter;
                            charsetCounter = (charsetCounter + 1) & 0xff;
                        } else {
                            // Rendering the actual screen, show character at this memory address
                            cellchar = screen[(row * COLUMNS) + col];
                        }

                        boolean inverse = cellchar > 127;
                        cellchar = cellchar & 0x7F;
                        int charStart = cellchar * 8;
                        int pixelCol = col * pixelWidth;
                        int pixelRow = (row * pixelHeight) + PAD_HEIGHT;

                        // Scan through this cell for all rows including padding
                        for (int charRow = -1; charRow < 8; charRow++) {
                            int rowPixels;
                            if (charRow < 0) {
                                rowPixels = 0;
                            } else {
                                rowPixels = charset[charStart + charRow];
                            }

                            // Factor in blink for those character rows that need it
                            boolean blinkOff = !blink && ((rowPixels & 0x20) > 0);

                            // For this row, go through each column with padding
                            for (int charCol = 5; charCol >= 0; charCol--) {
                                boolean setPixel;
                                if (blinkOff) {
                                    setPixel = inverse;
                                } else {
                                    if (charCol > 4) {
                                        setPixel = false;
                                    } else {
                                        setPixel = (rowPixels & 0x01) > 0;
                                        rowPixels = rowPixels >> 1;
                                    }
                                    if (inverse) {
                                        setPixel = !setPixel;
                                    }
                                }

                                // Draw this pixel if appropriate
                                if (setPixel) {
                                    g.fillRect(pixelCol + (charCol * pixelSize), pixelRow + (charRow * pixelSize),
                                            pixelSize, pixelSize);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // If error, capture it so it can be displayed instead of trying to render the screen in the future.
                // The only way this will be cleared is if we restart the processor or quit the program.
                renderingDisabled = true;
                renderingErrorMsg1 = e.getMessage();
                renderingErrorMsg2 = e.getStackTrace()[0].toString();

            }
        }
    }
}