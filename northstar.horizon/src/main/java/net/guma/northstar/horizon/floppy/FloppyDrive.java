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
package net.guma.northstar.horizon.floppy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import net.guma.northstar.horizon.Horizon;

/**
 * Abstract floppy drive that is implemented by either the single-density or double-density controllers
 */
public abstract class FloppyDrive {

    public static final String FILE_EXTENSION = "nsi";
    public static final int SSSD_SIZE = 89600;
    public static final int SSDD_SIZE = 179200;
    public static final int DSDD_SIZE = 358400;

    private static final int HIGHEST_TRACK = 34;

    private int[] diskData;
    private String fileName = null;

    private int driveNumber;
    private boolean writeProtect;

    private boolean commitPending;
    private boolean autoCommit;

    private boolean stepFlipFlop;
    private int stepDirection;
    private int currentTrack;

    private int currentSector;
    private boolean sectorHoleDetected;

    protected abstract void updateMachineState(boolean returningSectorNumber);

    protected abstract int writeByte(int data);

    protected abstract int getStatus(int statusToReturn);

    protected abstract int readByte();

    protected abstract void setControllerOrders(int params);

    protected abstract boolean beginWrite();

    /**
     * Returns the file name extension for a particular file
     * 
     * @param fileName
     *            the name of the file whose extension we want.
     * @return the extension for the file
     */
    public static final String getFileNameExtension(String fileName) {
        String extension = null;
        if ((fileName != null) && fileName.contains(".")) {
            extension = fileName.substring(fileName.lastIndexOf(".") + 1);
        }
        return extension;
    }

    /**
     * Read in the contents of a disk image
     * 
     * @param fileName
     *            the file name, including path, where the image is stored.
     * @return an integer array containing the disk image.
     */
    public static final int[] readDisk(String fileName) {
        int[] diskData = null;
        if ((fileName != null) && (fileName.length() > 0)) {
            try {
                fileName = fileName.replace('/', File.separatorChar).replace('\\', File.separatorChar);
                File diskFile = new File(fileName);
                byte[] diskBytes = Files.readAllBytes(diskFile.toPath());
                diskData = new int[diskBytes.length];
                for (int i = 0; i < diskBytes.length; i++) {
                    diskData[i] = diskBytes[i];
                    if (diskData[i] < 0) {
                        diskData[i] = 256 + diskData[i];
                    }
                }
            } catch (IOException ioe) {
                Horizon.displayErrorMessage("Unable to read floppy image: " + ioe.getMessage());
            }
        }
        return diskData;
    }

    /**
     * Update the mounted image on the local file system with the current state of the emulated disk image. This is a
     * permanent change to the image.
     */
    public void commitChanges() {
        if (getFileName() == null) {
            return;
        }
        try {
            File diskFile = new File(getFileName());
            byte[] diskBytes = new byte[getDiskData().length];
            for (int i = 0; i < diskBytes.length; i++) {
                diskBytes[i] = (getDiskData()[i] < 128) ? ((byte) getDiskData()[i]) : (byte) ((getDiskData()[i] - 256));
            }
            Files.write(diskFile.toPath(), diskBytes, StandardOpenOption.CREATE);
            setCommitPending(false);
        } catch (IOException ioe) {
            Horizon.displayErrorMessage("Unable to write floppy image: " + ioe.getMessage());
        }
    }

    /**
     * Read a disk image that this floppy has inserted. Subsequent reads and writes of this virtual floppy will then
     * work against this image.
     * 
     * @param fileName
     *            is the file, including path, we want to insert
     * @param writeProtect
     *            is true if the disk should not be writable
     */
    public void insertDisk(String fileName, int[] diskData) {
        setDiskData(diskData);
        if (diskData == null) {
            fileName = null;
        } else if ((fileName != null) && (fileName.length() == 0)) {
            fileName = null;
        }
        setFileName(fileName);
        setCommitPending(false);
    }

    /**
     * Reset drive to an initial state
     */
    protected void initialize() {
        diskData = null;
        stepDirection = 0;
        stepFlipFlop = false;
        currentTrack = 0;
        currentSector = 0;
        sectorHoleDetected = true;
    }

    /**
     * Set or clear the sector flag. This is down by request of the disk routines, which then repeatedly probe for this
     * flag to go back high. In a real drive the hardware does this when a sector hole is found. For our emulation, we
     * simply count down from a small fixed number before raising the flag to make it look like a sector hole was
     * detected and subsequent reads and writes can occur correctly aligned to a sector on a disk.
     */
    protected void setSectorHoleDetected(boolean newSectorHoleDetected) {
        sectorHoleDetected = newSectorHoleDetected;
    }

    /**
     * Sets which direction, in (toward the middle) or out (towards the edge), the next request to step the heads will
     * move us in.
     * 
     * @param stepDirection
     *            is the step direction, 0=out and 1=in
     */
    protected void setStepDirection(int newStepDirection) {
        stepDirection = newStepDirection;
    }

    /**
     * I/O routines want to move the head in or out to a different track. They would have first set the direction
     * earlier, then they call here twice. The first time the flip-flop is set, then the second time it is cleared. In
     * the hardware, this triggers the controller to physically move the heads. In our emulated version we merely
     * increase or decrease the track number we are managing when the flag is cleared.
     * 
     * @param stepFlipFlop
     *            state of flip-flop to set, (0=off, 1=on)
     */
    protected void setStepFlipFlop(int stepFlipFlop) {
        if (this.stepFlipFlop && (stepFlipFlop == 0)) {
            // Clearing step flip flop moves track in or out by one
            this.stepFlipFlop = false;
            if (stepDirection == 0) {
                // Stepping out
                if (currentTrack > 0) {
                    currentTrack--;
                }
            } else {
                // Stepping in
                if (currentTrack < HIGHEST_TRACK) {
                    currentTrack++;
                }
            }
        }
        this.stepFlipFlop = (stepFlipFlop != 0);
    }

    // Various getter/setter methods

    protected boolean isSectorHoleDetected() {
        return sectorHoleDetected;
    }

    public boolean isAutoCommit() {
        return autoCommit;
    }

    public void setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    public boolean isCommitPending() {
        return commitPending;
    }

    public void setCommitPending(boolean commitPending) {
        this.commitPending = commitPending;
    }

    protected int getCurrentSector() {
        return currentSector;
    }

    protected void setCurrentSector(int newCurrentSector) {
        currentSector = newCurrentSector;
    }

    protected int getCurrentTrack() {
        return currentTrack;
    }

    protected void setCurrentTrack(int newCurrentTrack) {
        currentTrack = newCurrentTrack;
    }

    public int[] getDiskData() {
        return diskData;
    }

    public void setDiskData(int[] diskData) {
        this.diskData = diskData;
    }

    public int getDriveNumber() {
        return driveNumber;
    }

    public void setDriveNumber(int driveNumber) {
        this.driveNumber = driveNumber;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public boolean isWriteProtect() {
        return writeProtect;
    }

    public void setWriteProtect(boolean writeProtect) {
        this.writeProtect = writeProtect;
    }
}