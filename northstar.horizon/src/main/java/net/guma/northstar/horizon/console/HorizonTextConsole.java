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

import java.awt.Color;
import java.awt.Font;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.PrintStream;

import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import net.guma.northstar.horizon.Horizon;
import net.guma.northstar.horizon.Settings;

/*
 * Text console to catch output going to the console rather than the ScreenSplitter.
 */
public class HorizonTextConsole {

    private DocumentListener docListener;
    private JTextComponent textData;
    private ConsoleOutputStream standardOut;
    private ConsoleOutputStream errorOut;

    /**
     * Creates the text console handler.
     * 
     * 
     * @param textData
     *            the output JTextPane we will output to
     */
    public HorizonTextConsole(JTextComponent textData) {
        this.textData = textData;
        textData.setEditable(false);

        standardOut = new ConsoleOutputStream(textData);
        System.setOut(new PrintStream(standardOut, true));

        errorOut = new ConsoleOutputStream(textData);
        System.setErr(new PrintStream(errorOut, true));

        docListener = new ConsoleTrimListener();
        textData.getDocument().addDocumentListener(docListener);

        // Set initial console colors, anything saved previously or default if nothing yet
        setForeground(Settings.getInitialColor(Settings.CONSOLE_FG));
        setBackground(Settings.getInitialColor(Settings.CONSOLE_BG));

        // Cursor seems to disappear sometimes when focus lost, so bring it back each time focus is regained.
        textData.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent arg0) {
                textData.getCaret().setVisible(true);
            }

            @Override
            public void focusLost(FocusEvent arg0) {}
        });
    }

    /**
     * Returns the current font assigned to the text console
     * 
     * @return the current font
     */
    public Font getFont() {
        return textData.getFont();
    }

    /**
     * Changes the font name of the console text window
     * 
     * @param fontName
     *            is the new new font name to apply
     */
    public void setFontName(String fontName) {
        Font oldFont = textData.getFont();
        Font newFont = new Font(fontName, oldFont.getStyle(), oldFont.getSize());
        Horizon.getSettings().put(Settings.WINDOW, Settings.CONSOLE_FONT_NAME, fontName);
        setFont(newFont);
    }

    /**
     * Changes the font of the console text window
     * 
     * @param font
     *            is the new new font to apply
     */
    public void setFont(Font font) {
        applyNewFont(font);
    }

    /**
     * Changes the font size of the console text window
     * 
     * @param fontSize
     *            is the new font size to apply
     */
    public void setFontSize(int fontSize) {
        Font oldFont = textData.getFont();
        Font newFont = new Font(oldFont.getName(), oldFont.getStyle(), fontSize);
        applyNewFont(newFont);
        standardOut.setMatchingFontSize(fontSize);
        errorOut.setMatchingFontSize(fontSize);
    }

    /**
     * Returns the font size of the console text window matching the current screen pixel size
     * 
     * @return the current console window font size
     */
    public int getMatchingFontSize() {
        return standardOut.getMatchingFontSize();
    }

    /**
     * Applies a new font to the console text window, repainting existing text to match
     * 
     * @param newFont
     *            is the new font to apply
     */
    public void applyNewFont(Font newFont) {
        // First change the font
        standardOut.setFont(newFont);
        errorOut.setFont(newFont);

        // Then reprint the current contents in the new size
        String text = textData.getText();
        textData.setText("");
        System.out.println(text);
    }

    /**
     * Indicates whether or not a text region is selected. Allows us to differentiate between a Control-C for a break as
     * opposed to a copy. Since we have already copied by the time we call this method, we can also clear any previous
     * highlight.
     * 
     * @return true if region of text is highlighted for selection
     */
    public boolean isSelected() {
        String selectedText = textData.getSelectedText();
        boolean hilighted = ((selectedText != null) && (selectedText.length() > 0));
        if (hilighted) {
            textData.getHighlighter().removeAllHighlights();
            textData.setCaretPosition(textData.getDocument().getLength());
        }
        return hilighted;
    }

    /**
     * Sets the background color of the text console window
     * 
     * @param backgroundColor
     *            is the background color to use
     */
    public void setBackground(Color backgroundColor) {
        textData.setBackground(backgroundColor);
    }

    /**
     * Returns the current console background color
     *
     * @return the background color
     */
    public Color getBackground() {
        return textData.getBackground();
    }

    /**
     * Sets the foreground color of the text console window
     * 
     * @param foregroundColor
     *            is the foreground color to use
     */
    public void setForeground(Color foregroundColor) {
        textData.setForeground(foregroundColor);
    }

    /**
     * Returns the current console foreground color
     *
     * @return the foreground color
     */

    public Color getForeground() {
        return textData.getForeground();
    }
}