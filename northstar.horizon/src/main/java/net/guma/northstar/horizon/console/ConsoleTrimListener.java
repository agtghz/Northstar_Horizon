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
package net.guma.northstar.horizon.console;

import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;

/*
 * Listener to Enforce the maximum lines in a console window.
 * 
 * Excess lines will be removed from the beginning of the window.
 */
public class ConsoleTrimListener implements DocumentListener {
    private static final int MAX_CONSOLE_LINES = 5000;

    public ConsoleTrimListener() {}

    @Override
    public void changedUpdate(DocumentEvent e) {}

    @Override
    public void removeUpdate(DocumentEvent arg0) {}

    /**
     * Handle insertion of new text into the Document
     * 
     * @param docEvent
     *            is the document event that leads us here.
     */
    @Override
    public void insertUpdate(final DocumentEvent docEvent) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                Document rootDocument = docEvent.getDocument();
                synchronized (rootDocument) {
                    Element rootDoc = rootDocument.getDefaultRootElement();
                    synchronized (rootDoc) {
                        while (rootDoc.getElementCount() > MAX_CONSOLE_LINES) {
                            Element line = rootDoc.getElement(0);
                            int endOffset = line.getEndOffset();
                            try {
                                rootDocument.remove(0, endOffset);
                            } catch (BadLocationException ble) {
                                System.out.println(ble);
                            }
                        }
                    }
                }
            }
        });
    }
}