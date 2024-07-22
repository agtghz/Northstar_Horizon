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
package net.guma.northstar.horizon.gui;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.guma.northstar.horizon.Horizon;
import net.guma.northstar.horizon.Settings;
import net.guma.northstar.horizon.console.HorizonTextConsole;

/**
 * Creates and manages the main window interface, including both keyboard input, an output window with either the
 * ScreenSplitter or raw text console, and the menu bar options.
 */
public class Workspace {

    private static final String MAIN_TITLE = "Northstar Horizon";
    public static final String SCREENSPLITTER_OUTPUT = "ScreenSplitter";
    public static final String CONSOLE_OUTPUT = "Text Console";

    public boolean isConsoleOut = false;
    private JFrame frame;
    private JMenu disk4 = new JMenu();
    private int pasteCountdown = 0;
    private JScrollPane scrollPane = null;
    private JTextPane textPane = null;
    private HorizonTextConsole textConsole;
    private JTabbedPane tabbedPane;

    /**
     * Generate and display the entire window to manage the running system. This includes a menu with all the options
     * needed, and a video window to render the ScreenSplitter display (or text console) and accept input.
     */
    public Workspace() {
        // Create the main window interface, starting with tabs for the two output windows
        frame = new JFrame(MAIN_TITLE + " - Starting");
        tabbedPane = new JTabbedPane();
        frame.add(tabbedPane);

        // Create ScreenSplitter output pane and add it as a tab
        ScreenSplitter screenPane = Horizon.getScreenSplitter();
        tabbedPane.addTab(SCREENSPLITTER_OUTPUT, null, screenPane, null);

        // Create console output pane and add it as a tab
        textPane = new JTextPane();
        textConsole = new HorizonTextConsole(textPane);
        scrollPane = new JScrollPane(textPane);
        tabbedPane.addTab(CONSOLE_OUTPUT, scrollPane);

        // Add listener for tab change
        tabbedPane.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if (tabbedPane.getSelectedComponent() == screenPane) {
                    // ScreenSplitter selected
                    textPane.setVisible(false);
                    setConsoleOut(false);
                    Horizon.getSettings().put(Settings.SYSTEM, Settings.CONSOLE_OUT, false);
                } else if (tabbedPane.getSelectedIndex() == 1) {
                    // Console selected
                    textPane.setVisible(true);
                    textPane.grabFocus();
                    setConsoleOut(true);
                    Horizon.getSettings().put(Settings.SYSTEM, Settings.CONSOLE_OUT, true);
                }
            }
        });

        // Add menus, settings, to main window
        frame.setJMenuBar(MainMenu.getMainWindowMenu(disk4));
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        // Position window at the last saved coordinates
        int frameCol = Horizon.getSettings().get(Settings.WINDOW, Settings.COLUMN, int.class);
        int frameRow = Horizon.getSettings().get(Settings.WINDOW, Settings.ROW, int.class);
        frame.setLocation(frameCol, frameRow);

        // Size the window to perfectly fit the video display
        frame.pack();
        Insets insets = frame.getInsets();
        int frameWidth = insets.left + insets.right + Horizon.getScreenSplitter().getWidth();
        int frameHeight = insets.top + insets.bottom + Horizon.getScreenSplitter().getHeight();
        frame.setPreferredSize(new Dimension(frameWidth, frameHeight));

        // Add main window listener, allowing us to trap closing it when there are pending writes,
        // halting the CPU, as well as saving a few additional parameters such as window position
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                // If any pending writes, ask if user really means it.
                boolean reallyExit = true;
                if (Horizon.getFloppyController().anyPendingWrites()) {
                    int confirm = JOptionPane.showOptionDialog(frame,
                            "One or more drives have pending writes, do you really want to exit?", "Exit Confirmation",
                            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
                    if (confirm == JOptionPane.NO_OPTION) {
                        reallyExit = false;
                    }
                }
                if (reallyExit) {
                    Horizon.flagForStop();

                    // Only save position if in the visible range
                    int row = Double.valueOf(frame.getLocationOnScreen().getX()).intValue();
                    int col = Double.valueOf(frame.getLocationOnScreen().getY()).intValue();
                    if ((row >= 0) && (col >= 0)) {
                        Horizon.getSettings().put(Settings.WINDOW, Settings.COLUMN, row);
                        Horizon.getSettings().put(Settings.WINDOW, Settings.ROW, col);
                    }

                    frame.dispose();
                }
            }
        });

        // Set up keyboard event handling to capture key presses into a buffer
        KeyAdapter keyListener = createKeyListener();
        screenPane.addKeyListener(keyListener);
        textPane.addKeyListener(keyListener);
        tabbedPane.addKeyListener(keyListener);
        frame.addKeyListener(keyListener);

        // Show time
        frame.setVisible(true);

        // If currently display text console rather than ScreenSplitter, switch the display to it instead.
        setConsoleOut(Horizon.getSettings().get(Settings.SYSTEM, Settings.CONSOLE_OUT, boolean.class));
        if (isConsoleOut()) {
            tabbedPane.setSelectedComponent(scrollPane);
            textPane.grabFocus();
        } else {
            tabbedPane.setSelectedComponent(screenPane);
            screenPane.grabFocus();
        }
    }

    /**
     * Creates the key listener that watches for key presses into the screen or console windows
     * 
     * @return the key listener
     */
    private KeyAdapter createKeyListener() {
        KeyAdapter keyListener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                // Parallel port gets instant key value when key pressed down
                if ((event.getKeyChar()) < 256) {
                    // Normal keys
                    Horizon.getInputOutput().setLastKey(Integer.valueOf(event.getKeyChar()));
                } else if (event.getKeyChar() == 65535) {
                    // Control keys
                    int key = 0;
                    if (event.getKeyCode() == 19) {
                        key = 148;
                    }
                    if (key > 0) {
                        Horizon.getInputOutput().setLastKey(Integer.valueOf(key));
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent arg0) {
                // Parallel port current key gets cleared on key up
                // Horizon.getInputOutput().setLastKey(null);
            }

            @Override
            public void keyTyped(KeyEvent event) {
                if (event.isControlDown()) {
                    if (event.getKeyChar() == 22) {
                        // Control-V Paste
                        Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
                        synchronized (Horizon.getInputOutput().getKeyBuffer()) {
                            try {
                                char[] chr = ((String) cb.getData(DataFlavor.stringFlavor)).toCharArray();
                                for (int i = 0; i < chr.length; i++) {
                                    Horizon.getInputOutput().getKeyBuffer().add(Integer.valueOf(chr[i]));
                                }
                                // If running at normal speed, temporarily run at full speed,
                                // if not already, to allow paste to quickly finish. Once we
                                // finish pasting we will restore back to the original speed.
                                if (!Horizon.isFullSpeed()) {
                                    pasteCountdown = chr.length;
                                    Horizon.setFullSpeed(true);
                                }
                            } catch (Exception ex) {}
                        }
                    } else if (event.getKeyChar() == 3) {
                        // Control-C break, but only if not copying selection
                        if (!textConsole.isSelected()) {
                            Horizon.getInputOutput().getKeyBuffer().add(Integer.valueOf(3));
                        }
                    } else {
                        // All other control keys are ignored by us here.
                    }
                } else if ((event.getKeyChar()) < 256) {
                    Integer keyCode = Integer.valueOf(event.getKeyChar());
                    synchronized (Horizon.getInputOutput().getKeyBuffer()) {
                        Horizon.getInputOutput().getKeyBuffer().add(keyCode);
                    }
                }
            }
        };
        return keyListener;
    }

    /**
     * Switches the main window display area to showing the text console instead of the ScreenSplitter video display.
     */
    public void displayConsole() {
        Horizon.getScreenSplitter().setVisible(false);
        scrollPane.setLocation(Horizon.getScreenSplitter().getLocation());
        scrollPane.setPreferredSize(Horizon.getScreenSplitter().getPreferredSize());
        scrollPane.setBackground(Settings.getInitialColor(Settings.CONSOLE_BG));
        scrollPane.setForeground(Settings.getInitialColor(Settings.CONSOLE_FG));
        scrollPane.setVisible(true);
        textPane.setBackground(scrollPane.getBackground());
        textPane.setForeground(scrollPane.getForeground());
        textPane.getCaret().setVisible(true);
        textPane.grabFocus();
    }

    /**
     * Switches the main window display area to showing the ScreenSplitter video display rather than the text console.
     */
    public void displayScreenSplitter() {
        scrollPane.setVisible(false);
        Horizon.getScreenSplitter().setVisible(true);
        Horizon.getScreenSplitter().grabFocus();
    }

    /**
     * @return the main window container
     */
    public JFrame getMainFrame() {
        return frame;
    }

    /**
     * Returns the next sequential key in the captured buffer. Should not be called unless we previously checked to
     * confirm at least one key was in the buffer. Used by serial port input routine.
     * 
     * @return the next key in the buffer
     */
    public int getNextKey() {
        int nextKey;
        synchronized (Horizon.getInputOutput().getKeyBuffer()) {
            nextKey = Horizon.getInputOutput().getKeyBuffer().removeFirst().intValue();
        }
        // If pasting data, and forced maximum speed so that we finish faster,
        // set it back down if we have finished the paste.
        if (pasteCountdown > 0) {
            pasteCountdown--;
            if (pasteCountdown == 0) {
                Horizon.setFullSpeed(false);
            }
        }
        return nextKey;
    }

    /**
     * Provides access to the JTextPane that contains either the ScreenSplitter video display or the text console.
     * 
     * @return
     */
    public JTextPane getTextPane() {
        return textPane;
    }

    /**
     * Indicates whether or not we are showing the text console, rather than the ScreenSplitter video display.
     * 
     * @return true if text console is visible
     */
    public boolean isConsoleOut() {
        return isConsoleOut;
    }

    /**
     * Identifies whether or not at least one key has been captured in the input buffer ready to be processes.
     * 
     * @return true of keyboard buffer has data
     */
    public boolean keyAvailable() {
        boolean isAvail = false;
        if (Horizon.getInputOutput().getKeyBuffer() != null) {
            synchronized (Horizon.getInputOutput().getKeyBuffer()) {
                isAvail = !Horizon.getInputOutput().getKeyBuffer().isEmpty();
            }
        }
        return isAvail;
    }

    /**
     * Called when we are rebooting our environment. Make sure input buffer is cleared out of any residue.
     */
    public void reboot() {
        Horizon.getInputOutput().getKeyBuffer().clear();
        Horizon.getInputOutput().setLastKey(null);
        pasteCountdown = 0;
    }

    /**
     * Override the flag that indicates whether or not we output to the standard console rather than just to the video
     * display screen.
     * 
     * @param isConsoleOut
     *            true/false console output
     */
    public void setConsoleOut(boolean isConsoleOut) {
        this.isConsoleOut = isConsoleOut;
        if (Horizon.getWorkspace() != null) {
            if (isConsoleOut) {
                Horizon.getWorkspace().displayConsole();
            } else {
                Horizon.getWorkspace().displayScreenSplitter();
            }
        }
    }

    /**
     * Allows enabling or disabling the 4th floppy drive menu choice. The single density controller only supports 3, as
     * opposed to the double density which allows for the 4th one.
     * 
     * @param enabled
     */
    public void setDisk4Enabled(boolean enabled) {
        disk4.setVisible(enabled);
    }

    /**
     * Provides access to the text console
     * 
     * @return a handle to the text console window
     */
    public HorizonTextConsole getTextConsole() {
        return textConsole;
    }

    /**
     * Update the main window title to include status information on whether the CPU is paused or running, and if
     * running whether full speed or normal speed.
     */
    public void updateTitle() {
        if (Horizon.isPaused()) {
            frame.setTitle(MAIN_TITLE + " - Paused");
        } else {
            if (Horizon.isFullSpeed()) {
                frame.setTitle(MAIN_TITLE + " - Running full speed");
            } else {
                frame.setTitle(MAIN_TITLE + " - Running normal speed");
            }
        }

    }
}