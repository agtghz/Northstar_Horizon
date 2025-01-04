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
package net.guma.northstar.horizon;

import javax.swing.JOptionPane;

import com.codingrodent.microprocessor.ProcessorException;
import com.codingrodent.microprocessor.Z80.Z80Core;

import net.guma.northstar.horizon.console.HorizonTextConsole;
import net.guma.northstar.horizon.cpu.InputOutput;
import net.guma.northstar.horizon.cpu.Memory;
import net.guma.northstar.horizon.floppy.DDFloppyController;
import net.guma.northstar.horizon.floppy.FloppyController;
import net.guma.northstar.horizon.floppy.FloppyDrive;
import net.guma.northstar.horizon.floppy.SDFloppyController;
import net.guma.northstar.horizon.gui.ScreenSplitter;
import net.guma.northstar.horizon.gui.Workspace;

/**
 * This is the main class to run the Horizon emulator.
 */
public class Horizon {
    private static final int BOOT_ADDRESS = 0xE800;

    // Main instance of running emulator
    private static Horizon horizon;

    // Emulation speed control constants
    private static final int TSTATES_PER_100MS = 400000;
    private static final int MS_PER_100K_TSTATES = 100;

    private static final int PAUSE_OR_BLINK_DELAY = 500;

    // Control flags
    private boolean running = true;
    private boolean paused = false;
    private boolean reboot = false;
    private boolean runFullSpeed;
    private long nextTStateSleep;
    private long initialTime;

    // Sub-system objects
    private InputOutput inputOutput;
    private Memory memory;
    private FloppyController floppyController;
    private ScreenSplitter screenSplitter;
    private Settings settings;
    private Workspace workspace;
    private Z80Core z80;

    /**
     * Set flag that will reboot the processor the next chance it gets. If any pending writes that have not been
     * committed to the underlying file system, prompt to confirm that this data will be lost and should we still
     * reboot.
     */
    public static void flagForReboot() {
        horizon.reboot = true;
        if ((horizon.floppyController != null) && horizon.floppyController.anyPendingWrites()) {
            int confirm = JOptionPane.showOptionDialog(horizon.workspace.getMainFrame(),
                    "One or more drives have pending writes that will be lost, do you really want to reboot?",
                    "Reboot Confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
            if (confirm == JOptionPane.NO_OPTION) {
                horizon.reboot = false;
            }
        }
    }

    /**
     * Set flag that will stop the processor and exit this program
     */
    public static void flagForStop() {
        horizon.running = false;
    }

    /**
     * @return access to the floppy disk drive controller
     */
    public static FloppyController getFloppyController() {
        return horizon.floppyController;
    }

    /**
     * @return access to the port input and output handler
     */
    public static InputOutput getInputOutput() {
        return horizon.inputOutput;
    }

    /**
     * @return access to the ScreenSplitter video display
     */
    public static ScreenSplitter getScreenSplitter() {
        return horizon.screenSplitter;
    }

    /**
     * @return access to the INI file that saves settings
     */
    public static Settings getSettings() {
        return horizon.settings;
    }

    /**
     * @return access to the main window interface workspace
     */
    public static Workspace getWorkspace() {
        return horizon.workspace;
    }

    /**
     * Provides access to the text console
     * 
     * @return a handle to the text console window
     */
    public static HorizonTextConsole getTextConsole() {
        return getWorkspace().getTextConsole();
    }

    /**
     * @return access to the Z80 emulation
     */
    public static Z80Core getZ80() {
        return horizon.z80;
    }

    /**
     * Mount each supported disk, 3 for single density and 4 for double. Load each with a particular disk image based on
     * what we saved the last time we ran, including the read-only flag if it was set previously.
     * 
     */
    private void mountDrives() {
        int totDrives = 3;
        int driveNumber = 0;
        while (++driveNumber <= totDrives) {
            String fileName = getSettings().get(Settings.DRIVES, Settings.DRIVE_FILE_MAP.get(driveNumber));
            int[] diskData;
            if (fileName == null) {
                diskData = null;
            } else {
                diskData = FloppyDrive.readDisk(fileName);
            }

            // We always insert disk 1 first, and depending on the size of the
            // image we use either the single or the double density controller.
            if (driveNumber == 1) {
                if ((diskData != null) && (diskData.length > 99999)) {
                    // Double density, we support 4 total drives
                    floppyController = new DDFloppyController();
                    getWorkspace().setDisk4Enabled(true);
                    totDrives = 4;
                } else {
                    // Single density controller
                    floppyController = new SDFloppyController();
                    getWorkspace().setDisk4Enabled(false);
                }
                floppyController.initializeFloppies();
            }

            floppyController.getFloppy(driveNumber).insertDisk(fileName, diskData);
            floppyController.getFloppy(driveNumber).setWriteProtect(
                    getSettings().get(Settings.DRIVES, Settings.DRIVE_READONLY_MAP.get(driveNumber), boolean.class));
        }
    }

    /**
     * Set a flag that indicates whether our CPU is running wide-open or limited to running at stock 4mhz speed.
     * 
     * @param speed
     *            to set the processor to.
     */
    public static void setFullSpeed(boolean runFullSpeed) {
        horizon.runFullSpeed = runFullSpeed;
        getWorkspace().updateTitle();
        if (!runFullSpeed) {
            // If setting stock speed, reset values that control our speed throttle.
            horizon.nextTStateSleep = horizon.z80.getTStates() + TSTATES_PER_100MS;
            horizon.initialTime = System.currentTimeMillis();
        }
    }

    /**
     * Return flag that indicates wide-open or stock CPU speed execution.
     * 
     * @return true if wide-open, else false
     */
    public static boolean isFullSpeed() {
        return horizon.runFullSpeed;
    }

    /**
     * Helper method to display an error dialog message
     * 
     * @param message
     *            is the error message to display
     */
    public static void displayErrorMessage(String message) {
        if (getWorkspace() != null) {
            JOptionPane.showMessageDialog(getWorkspace().getMainFrame(), message, "Error occurred",
                    JOptionPane.ERROR_MESSAGE);
        } else {
            System.out.println(message);
        }
    }

    /**
     * Sets whether or not the CPU is paused.
     * 
     * @param paused
     *            true if paused, false if running
     */
    public static void setPaused(boolean paused) {
        horizon.paused = paused;
        getWorkspace().updateTitle();
    }

    /**
     * Returns state of CPU running
     * 
     * @return true if CPU is paused, false if running
     */
    public static boolean isPaused() {
        return horizon.paused;
    }

    /**
     * Our main method gets everything running.
     * 
     * @param args
     *            no arguments are currently supported
     */
    public static final void main(String[] args) {
        horizon = new Horizon();
        horizon.run();
    }

    /**
     * Run the emulation by creating the CPU and all related sub-system objects, then looping through emulated code
     * instructions until we exit.
     */
    private void run() {
        // Read in the INI settings saved the last time we ran
        settings = new Settings();
        settings.readSettings();

        // Create the Z80 processor and all of the subsystems, including
        // memory, I/O, terminal input, and ScreenSplitter video display.
        screenSplitter = new ScreenSplitter();
        workspace = new Workspace();
        memory = new Memory();
        inputOutput = new InputOutput();
        z80 = new Z80Core(memory, inputOutput);

        // Get the saved state of the CPU speed option that we saved and start
        // us up running the same way, either normal 4Mhz emulated speed or wide
        // open as fast as we can run.
        setFullSpeed(getSettings().get(Settings.SYSTEM, Settings.FULL_SPEED, boolean.class));

        // Main loop that handles running each processor opcode
        long blinkTime = System.currentTimeMillis();
        boolean emptyBootDiskPrompt = true;
        reboot = true;
        while (running && !z80.getHalt()) {

            // If reboot requested, including on initial startup, reset all the affected subsystems before
            // restarting the CPU and causing it to begin running at the normal jump on start address.
            if (reboot) {
                reboot = false;

                // Reinitialize all of the sub-systems
                memory.clearMemory();
                screenSplitter.reboot();
                workspace.reboot();

                // Mount each of the supported floppy drives, including loading
                // any disk images that were loaded the last time we ran.
                mountDrives();

                // If no boot disk mounted, force user to do so before letting the CPU run.
                emptyBootDiskPrompt = true;
                setPaused(false);
                if (floppyController.getFloppy(1).getDiskData() == null) {
                    setPaused(true);
                    if (emptyBootDiskPrompt) {
                        displayErrorMessage("Please mount a boot disk on unit 1 and then restart the emulation");
                        emptyBootDiskPrompt = false;
                    }
                }

                // Start up the CPU at the Horizon normal jump on start address
                nextTStateSleep = z80.getTStates() + TSTATES_PER_100MS;
                initialTime = System.currentTimeMillis();
                z80.setResetAddress(BOOT_ADDRESS);
                z80.reset();
            }

            // Every half second flip blink boolean in the ScreenSplitter settings. This allows us to use this
            // flag to blink certain characters. If paused, no extra delay since the pause was done elsewhere.
            if (((System.currentTimeMillis() - blinkTime) > PAUSE_OR_BLINK_DELAY) || paused) {
                screenSplitter.flipBlink();
                blinkTime = System.currentTimeMillis();
            }
            
            // Shutdown drive motors after 5 seconds of inactivity.
            getFloppyController().timeoutDriveMotors();

            // If pausing processor, sleep a bit and loop again
            if (paused) {
                try {
                    Thread.sleep(PAUSE_OR_BLINK_DELAY);
                } catch (InterruptedException ie) {}

            } else {
                // If not running wide open in speed, limit us to as close to
                // 4mhz operation as possible by counting T-states and pausing
                // periodically.
                if (!runFullSpeed) {
                    long currentTState = z80.getTStates();
                    // For a 4mhz Z80, 400K t-states should take 100ms.
                    // After that many t-states have gone by, sleep until 100ms
                    // total have elapsed.
                    if (currentTState > nextTStateSleep) {
                        long currentTime = System.currentTimeMillis();
                        try {
                            long elapsedTime = (currentTime - initialTime);
                            long sleepTime = MS_PER_100K_TSTATES - elapsedTime;
                            if (sleepTime > 0) {
                                Thread.sleep(sleepTime);
                            }
                        } catch (InterruptedException e) {}
                        nextTStateSleep = currentTState + TSTATES_PER_100MS;
                        initialTime = System.currentTimeMillis();
                    }
                }

                // Now run the next instruction at the current PC location.
                try {
                    z80.executeOneInstruction();
                } catch (ProcessorException e) {
                    displayErrorMessage("Hardware crash: " + e.getMessage());
                    System.exit(0);
                }
            }
        }

        // On the way out, save off all the INI settings for next time.
        settings.writeSettings();
        System.exit(0);
    }
}