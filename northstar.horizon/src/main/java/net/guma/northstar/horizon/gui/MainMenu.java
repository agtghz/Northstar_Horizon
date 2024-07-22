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

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import javax.swing.colorchooser.ColorSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import net.guma.northstar.horizon.Horizon;
import net.guma.northstar.horizon.Resources;
import net.guma.northstar.horizon.Settings;
import net.guma.northstar.horizon.floppy.FloppyDrive;

/**
 * Creates the menu we display in our main window. This class is entirely static, and the only way in is to invoke the
 * getMainWindowMenu() static final method to generate and return the entire menu.
 */
public abstract class MainMenu {

    private static final String MENU_SYSTEM = "System";
    private static final String ACTION_REBOOT = "Reboot";
    private static final String ACTION_AUTO_COMMIT = "Auto Commit Writes";
    private static final String ACTION_FULL_SPEED = "Full Speed";
    private static final String ACTION_PAUSED = "Paused";
    private static final String ACTION_EXIT = "Exit";

    private static final String MENU_DISKS = "Disks";
    private static final String MENU_DISK_1 = "Disk 1";
    private static final String MENU_DISK_2 = "Disk 2";
    private static final String MENU_DISK_3 = "Disk 3";
    private static final String MENU_DISK_4 = "Disk 4";
    private static final String DRIVE_EMPTY = "Empty";
    private static final String MENU_CREATE_DISK = "Create Disk";

    private static final String DENSITY_SSSD = "SS/SD";
    private static final String DENSITY_SSDD = "SS/DD";
    private static final String DENSITY_DSDD = "DS/DD";

    private static final String MENU_DISPLAY = "Display";
    private static final String MENU_DISPLAY_SCREEN = "ScreenSplitter";
    private static final String MENU_DISPLAY_CONSOLE = "Text Console";

    private static final String CHARACTER_SET = "Character Set";

    private static final String MENU_COLORS = "Colors";
    private static final String ACTION_FOREGROUND = "Foreground";
    private static final String ACTION_BACKGROUND = "Background";
    private static final String COLOR_CHOOSER_SWATCHES = "Swatches";

    private static final String PIXEL_SIZE = "Pixel Size";
    private static final String FONT_SIZE_SMALL = "Small";
    private static final String FONT_SIZE_MEDIUM = "Medium";
    private static final String FONT_SIZE_LARGE = "Large";

    private static final String[] FONT_SIZES = { " 10 ", " 12 ", " 14 ", " 16 ", " 18 ", " 20 ", " 22 ", " 24 ", " 26 ",
            " 28 ", " 30 ", " 32 ", " 36 " };

    private static final String ACTION_VIDEO_ADDRESS = "ScreenSplitter Address";

    private static final String MENU_HELP = "Help";
    private static final String INFO = "Info";
    private static final String ABOUT = "About";

    private static final String OK_BUTTON_TEXT = "OK";
    private static final String CANCEL_BUTTON_TEXT = "Cancel";
    private static final String RESET_BUTTON_TEXT = "Reset";

    private static Color colorOnEntry = null;

    /**
     * Generates the entire set of menu items and ties them to a new menu bar that we return. This can then be attached
     * to the main window we will display.
     * 
     * @return the menu bar with all the menu choices implemented.
     */
    public static final JMenuBar getMainWindowMenu(JMenu disk4) {
        // Create a menu bar
        JMenuBar frameMenuBar = new JMenuBar();

        // Add menu items to menu bar
        frameMenuBar.add(addSystemMenuOptions());
        frameMenuBar.add(addDiskMenuOptions(disk4));
        frameMenuBar.add(addDisplayMenuOptions());
        frameMenuBar.add(addHelpMenuOptions());

        return frameMenuBar;
    }

    /**
     * Create the System menu and associated options
     * 
     * @return the menu containing the System options
     */
    private static JMenu addSystemMenuOptions() {
        // System options menu
        JMenu systemMenu = new JMenu(MENU_SYSTEM);

        // Reboot option
        JMenuItem rebootItem = new JMenuItem(ACTION_REBOOT);
        rebootItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (event.getActionCommand().equals(ACTION_REBOOT)) {
                    Horizon.flagForReboot();
                }
            }
        });
        systemMenu.add(rebootItem);

        // Option to auto commit disk changes to file system or not
        JCheckBox autoCommit = new JCheckBox(ACTION_AUTO_COMMIT);
        autoCommit.setToolTipText("Allow automatic commit of emulate disk image changes");
        autoCommit.setSelected(Horizon.getSettings().get(Settings.SYSTEM, Settings.AUTO_COMMIT, boolean.class));
        autoCommit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                Horizon.getSettings().put(Settings.SYSTEM, Settings.AUTO_COMMIT, autoCommit.isSelected());
                MenuSelectionManager.defaultManager().clearSelectedPath();
            }
        });
        systemMenu.add(autoCommit);

        // Option to run as fast as we can or as close to 4Mhz standard
        JCheckBox fullSpeed = new JCheckBox(ACTION_FULL_SPEED);
        fullSpeed.setToolTipText("Choose between normal and full speed operation");
        fullSpeed.setSelected(Horizon.getSettings().get(Settings.SYSTEM, Settings.FULL_SPEED, boolean.class));
        fullSpeed.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                Horizon.getSettings().put(Settings.SYSTEM, Settings.FULL_SPEED, fullSpeed.isSelected());
                Horizon.setFullSpeed(fullSpeed.isSelected());
                MenuSelectionManager.defaultManager().clearSelectedPath();
            }
        });
        systemMenu.add(fullSpeed);

        // Option to pause or un-pause the CPU
        JCheckBox paused = new JCheckBox(ACTION_PAUSED);
        paused.setToolTipText("Allows pausing the CPU execution");
        paused.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                Horizon.setPaused(paused.isSelected());
                MenuSelectionManager.defaultManager().clearSelectedPath();
            }
        });
        systemMenu.add(paused);

        // Update paused indicator when menu opens
        systemMenu.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                paused.setSelected(Horizon.isPaused());
            }
        });

        // Option to exit emulation
        JMenuItem exitItem = new JMenuItem(ACTION_EXIT);
        exitItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                Horizon.getWorkspace().getMainFrame().dispatchEvent(
                        new WindowEvent(Horizon.getWorkspace().getMainFrame(), WindowEvent.WINDOW_CLOSING));
            }
        });
        systemMenu.add(exitItem);

        return systemMenu;
    }

    /**
     * Create the Disk menu and associated options
     * 
     * @return the menu containing the Disk options
     */
    private static JMenu addDiskMenuOptions(JMenu disk4) {
        // Disk selection menu
        JMenu diskMenu = new JMenu(MENU_DISKS);
        JMenu disk1 = new JMenu(MENU_DISK_1);
        JMenu disk2 = new JMenu(MENU_DISK_2);
        JMenu disk3 = new JMenu(MENU_DISK_3);

        diskMenu.add(disk1);
        diskMenu.add(disk2);
        diskMenu.add(disk3);

        addDiskSelection(1, disk1);
        addDiskSelection(2, disk2);
        addDiskSelection(3, disk3);

        // Disk 4 is only valid for double density controller operation. We pass
        // it in here, thus allowing it to be enabled or disabled elsewhere as
        // needed to support the specific controller we are running with.
        if (disk4 != null) {
            disk4.setText(MENU_DISK_4);
            diskMenu.add(disk4);
            addDiskSelection(4, disk4);
        }

        // And option to create various format blank disk images
        JMenu createDiskMenu = new JMenu(MENU_CREATE_DISK);
        createDiskMenu.add(addNewDiskSelection(DENSITY_SSSD, FloppyDrive.SSSD_SIZE));
        createDiskMenu.add(addNewDiskSelection(DENSITY_SSDD, FloppyDrive.SSDD_SIZE));
        createDiskMenu.add(addNewDiskSelection(DENSITY_DSDD, FloppyDrive.DSDD_SIZE));
        diskMenu.add(createDiskMenu);

        return diskMenu;
    }

    /**
     * Adds a selection menu item to the disks menu for a particular numbered drive. This allows picking the inserted
     * disk for that drive, flagging the drive read only or not, as well as confirming a commit of any pending writes
     * that have not been automatically written out to the local file system.
     * 
     * @param driveNumber
     *            which drive to manage, 1 to 3.
     * @param menu
     *            the main drives menu we will attach our new item to
     */
    private static final void addDiskSelection(int driveNumber, JMenu menu) {
        JMenuItem displayedFileName = new JMenuItem(DRIVE_EMPTY);
        JPanel readOnlyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JCheckBox readOnly = new JCheckBox("Read Only");
        readOnly.setToolTipText("Check to disable write to this drive");
        readOnlyPanel.add(readOnly);

        // Create button that appears when data written to disk in memory has
        // not been committed to the actual file system image.
        JPanel commitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton commitPending = new JButton("Changes Pending, Commit?");
        commitPending.setToolTipText("Commit unwritten data");
        commitPending.setForeground(Color.RED);
        commitPanel.add(commitPending);

        // Add action when pressing the commit data button
        commitPending.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                FloppyDrive floppy = Horizon.getFloppyController().getFloppy(driveNumber);
                if (floppy.isCommitPending()) {
                    floppy.commitChanges();
                    commitPanel.setVisible(false);
                    MenuSelectionManager.defaultManager().clearSelectedPath();
                }
            }
        });

        // Add mouse listener to this specific floppy settings. This allows us
        // to correctly update the various options just before they display so
        // that we accurately represent the current state of the floppy.
        menu.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                FloppyDrive floppy = Horizon.getFloppyController().getFloppy(driveNumber);
                String currentImage = floppy.getFileName();
                if (currentImage == null) {
                    displayedFileName.setText(DRIVE_EMPTY);
                    readOnly.setSelected(false);
                    readOnly.setEnabled(false);
                    commitPanel.setVisible(false);
                } else {
                    displayedFileName.setText(currentImage);
                    readOnly.setSelected(floppy.isWriteProtect());
                    readOnly.setEnabled(true);
                    commitPanel.setVisible(floppy.isCommitPending());
                }
                commitPanel.getParent().revalidate();
                commitPanel.getParent().doLayout();
            }
        });

        // Add action to the read only button to enable or disable writing to this drive.
        readOnly.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                Horizon.getFloppyController().getFloppy(driveNumber).setWriteProtect(readOnly.isSelected());
                Horizon.getSettings().put(Settings.DRIVES, Settings.DRIVE_READONLY_MAP.get(driveNumber),
                        readOnly.isSelected());
            }
        });

        // Finally everything gets added to this specific floppy menu item
        addFileChooser(driveNumber, displayedFileName);
        menu.add(displayedFileName);
        menu.add(readOnlyPanel);
        menu.add(commitPanel);
    }

    /**
     * Links a file chooser dialog to a floppy drive menu item.
     * 
     * @param driveNumber
     *            which drive number to link to, 1 to 3.
     * @param menuItem
     *            the specific drive menu item to link to
     */
    private static final void addFileChooser(int driveNumber, JMenuItem fileChooserItem) {
        fileChooserItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                UIManager.put("FileChooser.readOnly", Boolean.TRUE);
                JFileChooser fileChooser = new JFileChooser();
                if (fileChooserItem.getText() != DRIVE_EMPTY) {
                    File currentFile = new File(fileChooserItem.getText());
                    fileChooser.setSelectedFile(currentFile);
                }
                fileChooser.setCurrentDirectory(Paths.get(".", "disks").toFile());
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                fileChooser.setMultiSelectionEnabled(false);
                fileChooser.setDragEnabled(false);
                FileFilter fileFilter = new FileNameExtensionFilter("Northstar Disk Image", FloppyDrive.FILE_EXTENSION);
                fileChooser.setFileFilter(fileFilter);
                fileChooser.setAcceptAllFileFilterUsed(false);
                JButton emptyFileButton = new JButton("Eject");
                fileChooser.setAccessory(emptyFileButton);
                emptyFileButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        fileChooser.setSelectedFile(null);
                        fileChooser.approveSelection();
                    }
                });
                switch (fileChooser.showOpenDialog(Horizon.getWorkspace().getMainFrame())) {
                case JFileChooser.APPROVE_OPTION:
                    File selectedFile = fileChooser.getSelectedFile();
                    // If any pending writes, ask if user really means to
                    // load a new image and discard those changes.
                    boolean reallySwitch = true;
                    if (Horizon.getFloppyController().getFloppy(driveNumber).isCommitPending()) {
                        int confirm = JOptionPane.showOptionDialog(Horizon.getWorkspace().getMainFrame(),
                                "This drive has pending writes, do you really want to discard them?",
                                "Replace Current Image Confirmation", JOptionPane.YES_NO_OPTION,
                                JOptionPane.QUESTION_MESSAGE, null, null, null);
                        if (confirm == JOptionPane.NO_OPTION) {
                            reallySwitch = false;
                        }
                    }
                    if (reallySwitch) {
                        String filePath;
                        if (selectedFile == null) {
                            filePath = null;
                        } else {
                            filePath = selectedFile.getPath();

                            // Convert to relative path if possible
                            try {
                                String relativePath = Paths.get(System.getProperty("user.dir"))
                                        .relativize(Paths.get(filePath)).toString();
                                filePath = relativePath;
                            } catch (Exception e) {
                                // Can't be relative, keep absolute
                            }
                        }

                        int[] diskData = FloppyDrive.readDisk(filePath);
                        Horizon.getFloppyController().getFloppy(driveNumber).insertDisk(filePath, diskData);
                        Horizon.getSettings().put(Settings.DRIVES, Settings.DRIVE_FILE_MAP.get(driveNumber), filePath);
                        Horizon.getSettings().put(Settings.DRIVES, Settings.DRIVE_READONLY_MAP.get(driveNumber),
                                Horizon.getFloppyController().getFloppy(driveNumber).isWriteProtect());
                    }
                    break;
                }
            }
        });
    }

    /**
     * Add a menu choice to create a new empty disk image of the proper size and density
     * 
     * @param type
     *            type of entry (SS/SD, SS/DD, DS/DD)
     * @param size
     *            size of empty file to create, matches file type
     * @return a new menu item to create this size of disk image
     */
    private static final JMenuItem addNewDiskSelection(String type, int size) {
        JMenuItem newDiskItem = new JMenuItem(type);

        newDiskItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Create blank " + type + " disk");
                fileChooser.setCurrentDirectory(Paths.get(".", "disks").toFile());
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                fileChooser.setMultiSelectionEnabled(false);
                FileFilter fileFilter = new FileNameExtensionFilter("Northstar Disk Image", FloppyDrive.FILE_EXTENSION);
                fileChooser.setFileFilter(fileFilter);
                fileChooser.setAcceptAllFileFilterUsed(false);
                switch (fileChooser.showSaveDialog(Horizon.getWorkspace().getMainFrame())) {
                case JFileChooser.APPROVE_OPTION:
                    File newFile = fileChooser.getSelectedFile();
                    if (newFile != null) {
                        if (!(newFile.getName().toLowerCase())
                                .endsWith("." + FloppyDrive.FILE_EXTENSION.toLowerCase())) {
                            newFile = new File(
                                    newFile.getAbsoluteFile() + "." + FloppyDrive.FILE_EXTENSION.toLowerCase());
                        }
                        boolean create = true;
                        if (newFile.exists()) {
                            int confirm = JOptionPane.showOptionDialog(Horizon.getWorkspace().getMainFrame(),
                                    "This file already exists, overwrite it?", "Replace Existing File",
                                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
                            if (confirm == JOptionPane.NO_OPTION) {
                                create = false;
                            } else {
                                newFile.delete();
                            }
                        }

                        if (create) {
                            try {
                                newFile.createNewFile();
                                byte[] diskBytes = new byte[size];
                                Files.write(newFile.toPath(), diskBytes, StandardOpenOption.WRITE);
                            } catch (IOException ioe) {
                                Horizon.displayErrorMessage(
                                        "Unable to create new file " + newFile.getName() + ": " + ioe.getMessage());
                            }
                        }
                    }
                    break;
                }
            }
        });

        return newDiskItem;
    }

    /**
     * Create the Display menu and associated options
     * 
     * @return the menu containing the Display options
     */
    private static JMenu addDisplayMenuOptions() {
        // Various display related menu
        JMenu displayMenu = new JMenu(MENU_DISPLAY);

        // Options for the ScreenSplitter display
        JMenu screenMenu = new JMenu(MENU_DISPLAY_SCREEN);
        displayMenu.add(screenMenu);

        // Add all available ScreenSplitter character sets
        JMenu charset = new JMenu(CHARACTER_SET);
        screenMenu.add(charset);
        ButtonGroup charsetGroup = new ButtonGroup();
        try {
            List<String> charsetNames = Resources.getResourceAsList(Resources.RESOURCE_CHARSET_LIST);
            for (String charsetName : charsetNames) {
                charset.add(getCharsetSelectionButton(charsetGroup, charsetName));
            }
        } catch (Exception e) {
            charset.add(getCharsetSelectionButton(charsetGroup, ScreenSplitter.DEFAULT_CHARSET));
        }

        // Allow selecting background/foreground video window colors
        JMenu screenColorsMenu = new JMenu(MENU_COLORS);
        screenColorsMenu.add(addColorChooserMenu(ACTION_FOREGROUND, Settings.SCREEN_FG));
        screenColorsMenu.add(addColorChooserMenu(ACTION_BACKGROUND, Settings.SCREEN_BG));
        screenMenu.add(screenColorsMenu);

        // Pixel size can be small, medium (default), or large
        JMenu pixelSize = new JMenu(PIXEL_SIZE);
        screenMenu.add(pixelSize);
        ButtonGroup pixelSizeGroup = new ButtonGroup();
        JRadioButton smallSize = getPixelSizeSelectionButton(pixelSizeGroup, 1, FONT_SIZE_SMALL);
        JRadioButton mediumSize = getPixelSizeSelectionButton(pixelSizeGroup, 2, FONT_SIZE_MEDIUM);
        JRadioButton largeSize = getPixelSizeSelectionButton(pixelSizeGroup, 3, FONT_SIZE_LARGE);
        pixelSize.add(smallSize);
        pixelSize.add(mediumSize);
        pixelSize.add(largeSize);

        // Options for the console display
        JMenu consoleMenu = new JMenu(MENU_DISPLAY_CONSOLE);
        displayMenu.add(consoleMenu);

        // Allow selecting background/foreground console window colors
        JMenu consoleColorsMenu = new JMenu(MENU_COLORS);
        consoleColorsMenu.add(addColorChooserMenu(ACTION_FOREGROUND, Settings.CONSOLE_FG));
        consoleColorsMenu.add(addColorChooserMenu(ACTION_BACKGROUND, Settings.CONSOLE_BG));
        consoleColorsMenu.add(consoleColorsMenu);
        consoleMenu.add(consoleColorsMenu);

        // Add option to select console font name
        consoleMenu.add(addFontNameSelection());

        // Allow option to select console font size
        consoleMenu.add(addFontSizeSelection());

        // Select initially selected pixel size based on previous settings
        int selectedPixelSize = Horizon.getSettings().get(Settings.WINDOW, Settings.PIXELSIZE, int.class);
        switch (selectedPixelSize) {
        case 1:
            smallSize.setSelected(true);
            break;
        case 3:
            largeSize.setSelected(true);
            break;
        default:
            mediumSize.setSelected(true);
            break;
        }

        // Option to select memory range for ScreenSplitter board. Default is highest, 56k
        // This must match whatever OS output logic is set up for, as well as an BASIC code.
        JMenu videoAddress = new JMenu(ACTION_VIDEO_ADDRESS);
        videoAddress.setVisible(false); // DISABLED, NO REAL NEED TO CHANGE THIS SO EASILY
        displayMenu.add(videoAddress);
        ButtonGroup videoAddressGroup = new ButtonGroup();
        for (String boardAddress : ScreenSplitter.BOARD_ADDRESSES) {
            videoAddress.add(getVideoAddressButton(videoAddressGroup, boardAddress));
        }

        return displayMenu;
    }

    /**
     * Returns radio button that allows picking between the two currently supported ScreenSplitter character sets, the
     * Graphics one or the Scientific one.
     * 
     * @param charsetGroup
     *            the button group that this radio button ties to
     * @param charsetName
     *            the specific character set name for this button, either Graphics or Scientific
     * @return the radio button to allow choosing this character set.
     */
    private static final JRadioButton getCharsetSelectionButton(ButtonGroup charsetGroup, String charsetName) {
        // Create the button with an appropriate label and put it into the group
        JRadioButton charsetButton = new JRadioButton(charsetName);
        charsetButton.setName(charsetName);
        charsetGroup.add(charsetButton);

        // Select initially selected button based on previous settings
        String selectedCharset = Horizon.getSettings().get(Settings.WINDOW, Settings.CHARSET);
        if ((selectedCharset != null) && selectedCharset.equals(charsetName)) {
            charsetButton.setSelected(true);
        }

        // When hovering over a graphics character name, change the entire display screen to showing all of the
        // characters for that set. When we leave the button in any way, restore the display back.
        charsetButton.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {}

            @Override
            public void mousePressed(MouseEvent e) {}

            @Override
            public void mouseReleased(MouseEvent e) {
                // Selected a character set, go back to showing actual screen contents
                Horizon.getScreenSplitter().setChangingCharset(false);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                // Entering the character set menu item, load that set and show it on the screen
                Horizon.getScreenSplitter().loadCharset(charsetName);
                Horizon.getScreenSplitter().setChangingCharset(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // Existing the character set, restore back to the original and show the normal screen again
                Horizon.getScreenSplitter().setChangingCharset(false);
                String origCharsetName = Horizon.getSettings().get(Settings.WINDOW, Settings.CHARSET);
                Horizon.getScreenSplitter().loadCharset(origCharsetName);
            }
        });

        // Add listener to this button so that it can change the current character set
        charsetButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (Horizon.getScreenSplitter().loadCharset(charsetName)) {
                    Horizon.getSettings().put(Settings.WINDOW, Settings.CHARSET, charsetName);
                } else {
                    // Invalid selection, set back to last known good button
                    String selectedCharset = Horizon.getSettings().get(Settings.WINDOW, Settings.CHARSET);
                    Enumeration<AbstractButton> buttons = charsetGroup.getElements();
                    while (buttons.hasMoreElements()) {
                        AbstractButton button = buttons.nextElement();
                        if (button.getName().equals(selectedCharset)) {
                            button.setSelected(true);
                            break;
                        }
                    }
                }
                MenuSelectionManager.defaultManager().clearSelectedPath();

            }
        });
        return charsetButton;
    }

    /**
     * Create a menu item that allows selecting the color for the foreground or background of the main video text
     * window.
     * 
     * @param colorArea
     *            either Foreground or Background to modify
     * @return the menu item that allows changing the particular area color
     */
    private static final JMenuItem addColorChooserMenu(String colorArea, String setting) {
        JColorChooser chooser = new JColorChooser();
        chooser.setPreviewPanel(new JPanel());
        chooser.setName(setting);

        // Create menu item to manage color for the foreground or background
        AbstractColorChooserPanel[] chooserPanels = chooser.getChooserPanels();
        for (AbstractColorChooserPanel chooserPanel : chooserPanels) {
            if (!chooserPanel.getDisplayName().equals(COLOR_CHOOSER_SWATCHES)) {
                chooser.removeChooserPanel(chooserPanel);
            }
        }

        // Listen for new color selection and activate it, but do not save it permanently yet.
        chooser.getSelectionModel().addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                switch (chooser.getName()) {
                case Settings.SCREEN_FG:
                    Horizon.getScreenSplitter().setForeground(chooser.getColor());
                    break;
                case Settings.SCREEN_BG:
                    Horizon.getScreenSplitter().setBackground(chooser.getColor());
                    break;
                case Settings.CONSOLE_FG:
                    Horizon.getWorkspace().getTextConsole().setForeground(chooser.getColor());
                    break;
                case Settings.CONSOLE_BG:
                    Horizon.getWorkspace().getTextConsole().setBackground(chooser.getColor());
                    break;
                }
            }
        });

        // Listen for OK button, makes current selection permanent
        ActionListener okListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                Horizon.getSettings().put(Settings.WINDOW, chooser.getName(), chooser.getColor().getRGB());
                MenuSelectionManager.defaultManager().clearSelectedPath();
            }
        };

        // Listen for Cancel button, discards current changes
        ActionListener cancelListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                // Restore the currently active color to the one saved on entry to the dialog
                switch (chooser.getName()) {
                case Settings.SCREEN_FG:
                    Horizon.getScreenSplitter().setForeground(colorOnEntry);
                    break;
                case Settings.SCREEN_BG:
                    Horizon.getScreenSplitter().setBackground(colorOnEntry);
                    break;
                case Settings.CONSOLE_FG:
                    Horizon.getWorkspace().getTextConsole().setForeground(colorOnEntry);
                    break;
                case Settings.CONSOLE_BG:
                    Horizon.getWorkspace().getTextConsole().setBackground(colorOnEntry);
                    break;
                }
                MenuSelectionManager.defaultManager().clearSelectedPath();
            }
        };

        // Create the actual color chooser widget
        JDialog dialog = JColorChooser.createDialog(null, colorArea + " Color", false, chooser, okListener,
                cancelListener);

        // Need the lower level button pane for individual button access
        Container contentPane = dialog.getContentPane();
        JPanel buttonPanel = null;
        for (Component c : contentPane.getComponents()) {
            if (c instanceof JPanel) {
                buttonPanel = (JPanel) c;
            }
        }

        // Need to map an event to the Reset button, which is not normally
        // directly accessible. Search for the buttons until we find it, then
        // tie a custom action event to it so that we can add our own behavior.
        // Also a good time to add tool tips to all the buttons.
        if (buttonPanel != null) {
            for (Component b : buttonPanel.getComponents()) {
                if (b instanceof JButton) {
                    JButton button = (JButton) b;
                    if (button.getText().equals(RESET_BUTTON_TEXT)) {
                        button.setToolTipText("Restore " + colorArea.toLowerCase() + " color to default");
                        button.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent event) {
                                // Update the appropriate display color to the proper default
                                Color defaultColor = null;
                                switch (chooser.getName()) {
                                case Settings.SCREEN_FG:
                                    defaultColor = Settings.DEFAULT_SCREEN_FG;
                                    Horizon.getScreenSplitter().setForeground(defaultColor);
                                    break;
                                case Settings.SCREEN_BG:
                                    defaultColor = Settings.DEFAULT_SCREEN_BG;
                                    Horizon.getScreenSplitter().setBackground(defaultColor);
                                    break;
                                case Settings.CONSOLE_FG:
                                    defaultColor = Settings.DEFAULT_CONSOLE_FG;
                                    Horizon.getWorkspace().getTextConsole().setForeground(defaultColor);
                                    break;
                                case Settings.CONSOLE_BG:
                                    defaultColor = Settings.DEFAULT_CONSOLE_BG;
                                    Horizon.getWorkspace().getTextConsole().setBackground(defaultColor);
                                    break;
                                }

                                // Update the correct saved setting as well to the default color
                                if (defaultColor != null) {
                                    Horizon.getSettings().put(Settings.WINDOW, chooser.getName(),
                                            defaultColor.getRGB());
                                }

                                MenuSelectionManager.defaultManager().clearSelectedPath();
                            }
                        });
                        break;
                    } else if (button.getText().equals(OK_BUTTON_TEXT)) {
                        button.setToolTipText("Save currently selected " + colorArea.toLowerCase() + " color");
                    } else if (button.getText().equals(CANCEL_BUTTON_TEXT)) {
                        button.setToolTipText("Discard changes to " + colorArea.toLowerCase() + " color");
                    }
                }
            }
        }

        // Return this menu item color selection menu
        JMenuItem colorSelectionItem = new JMenu(colorArea);
        colorSelectionItem.add(dialog.getContentPane());

        // Add listener to color selection so we can select current color on entry.
        // This color is saved so that if we cancel that color is restored back.
        colorSelectionItem.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                colorOnEntry = null;
                switch (chooser.getName()) {
                case Settings.SCREEN_FG:
                    colorOnEntry = Horizon.getScreenSplitter().getForeground();
                    break;
                case Settings.SCREEN_BG:
                    colorOnEntry = Horizon.getScreenSplitter().getBackground();
                    break;
                case Settings.CONSOLE_FG:
                    colorOnEntry = Horizon.getWorkspace().getTextConsole().getForeground();
                    break;
                case Settings.CONSOLE_BG:
                    colorOnEntry = Horizon.getWorkspace().getTextConsole().getBackground();
                    break;
                }
                if (colorOnEntry != null) {
                    ColorSelectionModel selectionModel = chooser.getSelectionModel();
                    selectionModel.setSelectedColor(colorOnEntry);
                }
            }
        });

        return colorSelectionItem;
    }

    /**
     * Allows picking between two pixel size options for the video window, currently only single or double sized.
     * 
     * @param pixelSizeGroup
     *            the group the radio buttons will be managed by
     * @param size
     *            the size this button chooses, either 1 or 2
     * @return the button we can put on the menu to include this option
     */
    private static final JRadioButton getPixelSizeSelectionButton(ButtonGroup pixelSizeGroup, int sizeNumber,
            String sizeText) {

        // Create the button with an appropriate label and put it into the group
        JRadioButton pixelSizeButton = new JRadioButton(sizeText);
        pixelSizeGroup.add(pixelSizeButton);

        // Add listener to this button so that it can change the size
        pixelSizeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                Horizon.getSettings().put(Settings.WINDOW, Settings.PIXELSIZE, sizeNumber);
                boolean consoleOut = (!Horizon.getScreenSplitter().isVisible());
                Horizon.getScreenSplitter().resize(sizeNumber);
                int consoleFontSize = Horizon.getWorkspace().getTextConsole().getMatchingFontSize();
                Horizon.getWorkspace().getTextConsole().setFontSize(consoleFontSize);
                if (consoleOut) {
                    Horizon.getWorkspace().displayScreenSplitter();
                    Horizon.getWorkspace().displayConsole();
                }
                MenuSelectionManager.defaultManager().clearSelectedPath();
            }
        });
        return pixelSizeButton;
    }

    /**
     * Add a selection for which address range the ScreenSplitter is mapped to. Note that most of these are at best poor
     * choices and some will not work at all given the standard Horizon address ranges, but all are still presented as
     * options.
     * 
     * @param boardAddressGroup
     *            button group the address selection radio buttons link to.
     * @param boardAddressName
     *            the name of the address selection for this button.
     * @return
     */
    private static final JRadioButton getVideoAddressButton(ButtonGroup boardAddressGroup, String boardAddressName) {
        // Create the button with an appropriate label and put it into the group
        JRadioButton boardAddressButton = new JRadioButton(boardAddressName);
        boardAddressGroup.add(boardAddressButton);

        // Select initially selected button based on previous settings (if any)
        int boardAddress;
        if (Horizon.getSettings().get(Settings.SYSTEM, Settings.VIDEO_ADDRESS) != null) {
            boardAddress = Horizon.getSettings().get(Settings.SYSTEM, Settings.VIDEO_ADDRESS, int.class);
        } else {
            boardAddress = Horizon.getScreenSplitter().getBoardAddress();
        }

        if (boardAddressName.equals(ScreenSplitter.BOARD_ADDRESSES.get(boardAddress))) {
            boardAddressButton.setSelected(true);
        } else {
            boardAddressButton.setSelected(false);
        }

        // Add listener to this button so that it can change the selected board address
        boardAddressButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                int selectedBoardAddress = ScreenSplitter.BOARD_ADDRESSES.indexOf(boardAddressName);
                Horizon.getSettings().put(Settings.SYSTEM, Settings.VIDEO_ADDRESS, selectedBoardAddress);
            }
        });

        // Add listener to alert user that any address change will only occur on restart
        boardAddressButton.addFocusListener(new FocusListener() {

            @Override
            public void focusGained(FocusEvent e) {}

            @Override
            public void focusLost(FocusEvent e) {
                int currentAddress = Horizon.getScreenSplitter().getBoardAddress();
                int newAddress = Horizon.getSettings().get(Settings.SYSTEM, Settings.VIDEO_ADDRESS, int.class);
                if (newAddress != currentAddress) {
                    JOptionPane.showMessageDialog(Horizon.getWorkspace().getMainFrame(),
                            "Change will occur on next restart\n\nDifferent address requires compatible DOS version",
                            "Screen Spliter start address change", JOptionPane.INFORMATION_MESSAGE);
                }
            }

        });

        return boardAddressButton;
    }

    /**
     * Create the menu allowing selecting the font name to use for the text console
     * 
     * @return the menu containing the font name selection menu
     */
    private static JMenu addFontNameSelection() {
        JMenu consoleFontName = new JMenu("Font Name");

        // Get list of installed fixed width normal fonts
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        List<String> fontNamesAsList = new ArrayList<String>();
        FontRenderContext frc = new FontRenderContext(null, RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT,
                RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT);
        for (Font font : ge.getAllFonts()) {
            if (font.isPlain()) {
                Rectangle2D iBounds = font.getStringBounds("i", frc);
                Rectangle2D mBounds = font.getStringBounds("m", frc);
                if (iBounds.getWidth() == mBounds.getWidth()) {
                    fontNamesAsList.add(font.getName());
                }
            }
        }
        String[] fontNames = fontNamesAsList.toArray(new String[fontNamesAsList.size()]);

        // And assign the list of fonts to the selection list
        JList<String> fontSelector = new JList<>(fontNames);
        JScrollPane fontScrollPane = new JScrollPane(fontSelector);
        consoleFontName.add(fontScrollPane);

        // Listener to set the current selected font when the list is brought up
        consoleFontName.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                String currentFontName = Horizon.getWorkspace().getTextConsole().getFont().getName();
                for (int fontNameIndex = 0; fontNameIndex < fontNames.length; fontNameIndex++) {
                    if (fontNames[fontNameIndex].equals(currentFontName)) {
                        fontSelector.setSelectedIndex(fontNameIndex);
                        fontSelector.ensureIndexIsVisible(fontNameIndex);
                    }
                }

            }
        });

        // React to chosen a different font name
        fontSelector.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                String fontName = fontNames[fontSelector.getSelectedIndex()];
                Horizon.getWorkspace().getTextConsole().setFontName(fontName);
            }
        });

        return consoleFontName;
    }

    /**
     * Create the menu allowing selecting the font size to use for the text console
     * 
     * @return the menu containing the font size selection menu
     */
    private static JMenu addFontSizeSelection() {
        JMenu consoleFontSize = new JMenu("Font Size");

        // Add a list of fonts sizes for the text console to choose from
        JList<String> fontSize = new JList<>(FONT_SIZES);
        consoleFontSize.add(fontSize);

        // Listener to set the current font size when the list is brought up
        consoleFontSize.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                Font consoleFont = Horizon.getWorkspace().getTextConsole().getFont();
                for (int fontSizeIndex = 0; fontSizeIndex < FONT_SIZES.length; fontSizeIndex++) {
                    if (FONT_SIZES[fontSizeIndex].trim().equals(String.valueOf(consoleFont.getSize()))) {
                        fontSize.setSelectedIndex(fontSizeIndex);
                    }
                }

            }
        });

        // React to font size changes by applying them immediately to the text console window
        fontSize.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                String sizeValue = fontSize.getSelectedValue();
                try {
                    int newSize = Integer.valueOf(((String) sizeValue).trim());
                    Horizon.getWorkspace().getTextConsole().setFontSize(newSize);
                } catch (NumberFormatException nfe) {
                    // Should not happen, but ignore if it does
                }
            }
        });

        return consoleFontSize;
    }

    /**
     * Create the Help menu and associated options
     * 
     * @return the menu containing the Help options
     */
    private static JMenu addHelpMenuOptions() {
        // Help menu
        JMenu helpMenu = new JMenu(MENU_HELP);
        JMenuItem infoItem = new JMenuItem(INFO);
        helpMenu.add(infoItem);
        JMenuItem aboutItem = new JMenuItem(ABOUT);
        helpMenu.add(aboutItem);

        // Dialog to show info box off of help menu
        infoItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                String infoContents;
                try {
                    infoContents = Resources.getResourceAsString(Resources.RESOURCE_INFO);
                } catch (Exception e) {
                    infoContents = "Unable to access info resource:" + e.getMessage();
                }
                JLabel infoLabel = new JLabel(infoContents);
                JOptionPane.showMessageDialog(null, infoLabel, INFO, JOptionPane.PLAIN_MESSAGE);
            }
        });

        // Dialog to show about box off of help menu
        aboutItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                String aboutContents;
                try {
                    aboutContents = Resources.getResourceAsString(Resources.RESOURCE_ABOUT);
                } catch (Exception e) {
                    aboutContents = "Unable to access about resource:" + e.getMessage();
                }
                JLabel aboutLabel = new JLabel(aboutContents);
                try {
                    URL nsLogo = getClass().getClassLoader().getResource(Resources.RESOURCE_LOGO);
                    aboutLabel.setIcon(new ImageIcon(ImageIO.read(nsLogo)));
                } catch (IOException ioe) {
                    // Ignore
                }
                aboutLabel.setHorizontalAlignment(SwingConstants.CENTER);
                aboutLabel.setVerticalTextPosition(SwingConstants.BOTTOM);
                aboutLabel.setHorizontalTextPosition(SwingConstants.CENTER);
                JOptionPane.showMessageDialog(null, aboutLabel, ABOUT, JOptionPane.PLAIN_MESSAGE);
            }
        });

        return helpMenu;
    }
}