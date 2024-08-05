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
package net.guma.northstar.horizon.floppy;

import java.awt.Color;

import net.guma.northstar.horizon.gui.MainMenu;

/**
 * Abstract floppy controller that is implemented by either the single-density or double-density controllers
 */
public abstract class FloppyController {

    public static final int FDC_FROM = 0XE800;
    public static final int FDC_TO = 0xEBFF;

    protected FloppyDrive floppies[];
    protected int currentFloppy = 1;

    protected boolean motorsRunning = false;
    protected long motorsActivityTime = 0;
    private static final int MOTOR_TIMEOUT = 5000;

    protected abstract FloppyDrive getNewFloppyDrive(int floppyNumber);

    protected abstract int getTotalFloppies();

    public abstract int read(int address);

    /**
     * Indicate whether or not any of the drives have pending writes.
     * 
     * @return true if at least one drive has writes pending.
     */
    public boolean anyPendingWrites() {
        for (int i = 0; i <= getTotalFloppies(); i++) {
            if (floppies[i] != null) {
                if (floppies[i].isCommitPending()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Flag motors to continue running for at least 5 seconds more.
     */
    protected void setMotorsRunning() {
        motorsRunning = true;
        motorsActivityTime = System.currentTimeMillis();
        MainMenu.getDisksMenu().setForeground(Color.RED);
    }

    /**
     * If motor is running, and there has been no activity in 5 seconds on any drives, then shut down the motors.
     */
    public void timeoutDriveMotors() {
        if (motorsRunning && (System.currentTimeMillis() > (motorsActivityTime + MOTOR_TIMEOUT))) {
            motorsRunning = false;
            MainMenu.getDisksMenu().setForeground(Color.BLACK);
        }
    }

    /**
     * @return true if drives are spinning, otherwise false
     */
    public boolean areDrivesRunning() {
        return motorsRunning;
    }

    /**
     * Return a specific floppy object
     * 
     * @param floppyNumber
     *            the floppy number to return
     * @return the floppy object asked for
     */
    public FloppyDrive getFloppy(int floppyNumber) {
        if ((floppyNumber < 0) || (floppyNumber > getTotalFloppies())) {
            // Invalid floppy number gets mapped to the bogus number zero one.
            floppyNumber = 0;
        }
        return floppies[floppyNumber];
    }

    /**
     * Create a set of floppies for this controller. Create one more than needed, since the zero slot is used when
     * selecting an invalid drive number.
     */
    public void initializeFloppies() {
        for (int i = 0; i <= getTotalFloppies(); i++) {
            floppies[i] = getNewFloppyDrive(i);
        }
    }
}
