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
package net.guma.northstar.horizon.console;

import java.awt.Font;
import java.io.ByteArrayOutputStream;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import net.guma.northstar.horizon.Horizon;
import net.guma.northstar.horizon.Settings;

/*
 * Route output stream data to a buffer and ultimately to the appropriate console window.
 */
public class ConsoleOutputStream extends ByteArrayOutputStream {

    private static final String EOL = System.getProperty("line.separator");
    private static final String DEFAULT_FONT_NAME = "Courier New";
    private static final int DEFAULT_FONT_SIZE_SMALL = 12;
    private static final int DEFAULT_FONT_SIZE_MEDIUM = 20;
    private static final int DEFAULT_FONT_SIZE_LARGE = 28;

    private SimpleAttributeSet attributeSet = new SimpleAttributeSet();
    private StringBuffer textBuffer = new StringBuffer(80);
    private JTextComponent textComponent;
    private boolean atFirstLine = true;

    /*
     * Create an output stream. We will get one for the normal text output and for error out
     * 
     * @param textComponent is the JTextComponent we will output to
     */
    public ConsoleOutputStream(JTextComponent textComponent) {
        this.textComponent = textComponent;

        // Get font name, either saved from before, or set to a default
        String fontName = Horizon.getSettings().get(Settings.WINDOW, Settings.CONSOLE_FONT_NAME);
        if (fontName == null) {
            fontName = DEFAULT_FONT_NAME;
            Horizon.getSettings().put(Settings.WINDOW, Settings.CONSOLE_FONT_NAME, fontName);
        }

        // Set font, including size that is associated with the current screen pixel size
        Font font = new Font(fontName, 0, getMatchingFontSize());
        setFont(font);
    }

    /**
     * For the current video pixel size (1, 2, or 3), return the font size for the text console window. This differs for
     * each of the 3 video pixel sizes. This info is either already in the saved settings, or is defaulted if missing.
     * 
     * @return the appropriate font size for the text consoles
     */
    public int getMatchingFontSize() {
        String sizeOption;
        int defaultSize;
        switch (Horizon.getScreenSplitter().getPixelSize()) {
        case 1:
            sizeOption = Settings.CONSOLE_FONT_SIZE_SMALL;
            defaultSize = DEFAULT_FONT_SIZE_SMALL;
            break;
        case 3:
            sizeOption = Settings.CONSOLE_FONT_SIZE_LARGE;
            defaultSize = DEFAULT_FONT_SIZE_LARGE;
            break;
        default:
            sizeOption = Settings.CONSOLE_FONT_SIZE_MEDIUM;
            defaultSize = DEFAULT_FONT_SIZE_MEDIUM;
            break;
        }

        int fontSize = Horizon.getSettings().get(Settings.WINDOW, sizeOption, int.class);
        if (fontSize == 0) {
            fontSize = defaultSize;
            Horizon.getSettings().put(Settings.WINDOW, sizeOption, fontSize);
        }

        return fontSize;
    }

    /**
     * For the current video pixel size (1, 2, or 3), save it by the matching name in the settings file
     * 
     * @return the appropriate font size for the text consoles
     */
    public void setMatchingFontSize(int fontSize) {
        String sizeOption;
        switch (Horizon.getScreenSplitter().getPixelSize()) {
        case 1:
            sizeOption = Settings.CONSOLE_FONT_SIZE_SMALL;
            break;
        case 3:
            sizeOption = Settings.CONSOLE_FONT_SIZE_LARGE;
            break;
        default:
            sizeOption = Settings.CONSOLE_FONT_SIZE_MEDIUM;
            break;
        }
        Horizon.getSettings().put(Settings.WINDOW, sizeOption, fontSize);
    }

    /**
     * Applies a new font to the output stream, repainting existing text to match
     * 
     * @param newFont
     *            is the new font to apply
     */
    public void setFont(Font font) {
        textComponent.setFont(font);
        StyleConstants.setFontFamily(attributeSet, font.getFamily());
        StyleConstants.setFontSize(attributeSet, font.getSize());
    }

    /*
     * Override flush method to append the text data to the text buffer, and ultimately to the output stream.
     */
    @Override
    public void flush() {
        String message = toString();

        // Nothing to do if document is already empty
        if (message.length() == 0)
            return;

        Document textDocument = textComponent.getDocument();
        synchronized (textDocument) {
            textBuffer.setLength(0);

            if (EOL.equals(message)) {
                // End-of-line just appends that blank line
                textBuffer.append(message);

            } else {
                // All other text appends the message
                textBuffer.append(message);

                // And then routes everything up to this point to the output window
                synchronized (textDocument) {

                    if (atFirstLine && textDocument.getLength() != 0) {
                        textBuffer.insert(0, "\n");
                    }
                    atFirstLine = false;

                    try {
                        textDocument.insertString(textDocument.getLength(), textBuffer.toString(), attributeSet);
                        textComponent.setCaretPosition(textDocument.getLength());
                    } catch (BadLocationException ble) {}
                }

                // And we are done with this buffer, so clear it for next time
                textBuffer.setLength(0);
            }
        }

        reset();
    }
}