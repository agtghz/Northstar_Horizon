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

import java.util.Random;

import net.guma.northstar.horizon.Horizon;
import net.guma.northstar.horizon.Settings;

/**
 * Represents one physical double-density and possibly double-sided floppy drive, each of which is managed by the single
 * controller. In the real machine there can be up to 4 of these.
 *
 */
public class DDFloppyDrive extends FloppyDrive {
    private static final int SECT_PER_TRACK = 10;
    private static final int MAX_TRACKS = 35;
    private static final int SD_SECTOR_SIZE = 256;
    private static final int DD_SECTOR_SIZE = 512;
    private static final int SYNC_CHARACTER = 0xFB;

    // Machine states
    private static final int STATE_SECTOR_0 = 0;
    private static final int STATE_SECTOR_1 = 10;
    private static final int STATE_SECTOR_2 = 20;
    private static final int STATE_S_READ_DATA = 21;
    private static final int STATE_D_READ_DATA = 22;
    private static final int STATE_D_READ_CRC = 23;
    private static final int STATE_D_READ_END = 40;
    private static final int STATE_D_WRITE_INIT1 = 81;
    private static final int STATE_D_WRITE_INIT2 = 82;
    private static final int STATE_D_WRITE_INIT_S = 83;
    private static final int STATE_D_WRITE_DATA = 84;
    private static final int STATE_D_WRITE_CRC = 85;

    // Status-Byte Flags
    private static final int D_SECTOR_FLAG = 0x80;
    private static final int D_INDEX_FLAG = 0x40;
    private static final int D_DENSITY_FLAG = 0x20;
    private static final int D_MOTOR_ON_FLAG = 0x10;
    private static final int D_WINDOW_FLAG = 0x08;
    private static final int D_READ_ENABLE = 0x04;
    // private static final int D_A_SPARE_FLAG = 0x02;
    private static final int D_SYNC_FLAG = 0x01;
    private static final int D_WRITE_FLAG = 0x08;
    // private static final int D_B_SPARE_FLAG = 0x04;
    private static final int D_WRITE_PROTECT_FLAG = 0x02;
    private static final int D_TRACK_0_FLAG = 0x01;

    // Double density control codes
    private static final int D_CTRL_DENSITY = 0x80;
    private static final int D_CTRL_SIDE_SELECT = 0x40;
    private static final int D_CTRL_DP = 0x20;
    private static final int D_CTRL_STEP = 0x10;
    // private static final int D_CTRL_MOTOR_ON = 0x10;

    private int currentState;
    private int stateCounter;
    private boolean syncDetected;
    private boolean inWindow;
    private boolean writeReady;
    private boolean indexFlag;
    private boolean readEnable;

    private boolean doubleDensityImage;
    private int diskSide;

    private int bytesToXfer;
    private int crcValue;
    private int bytePointer;
    private boolean writeDoubleDensity = false;

    public DDFloppyDrive(int driveNumber) {
        setDriveNumber(driveNumber);
        initialize();
    }

    /**
     * About to write out a sector. Reset the raw and data byte counts to zero. The raw count keeps track of all data
     * sent to the write logic, including leading padding and sync characters that we don't actually write out, while
     * the data count keeps track of the 256/512 byte actual sector data we use as an offset into the disk image array.
     * 
     * @param return
     *            false if not ok to write due to density mismatch
     */
    public boolean beginWrite() {
        // If trying to write double density to a single density disk image,
        // return a failure condition.
        if (!doubleDensityImage && writeDoubleDensity) {
            return false;
        }

        initializeBytePointer();

        if (writeDoubleDensity) {
            currentState = STATE_D_WRITE_INIT1;
        } else {
            currentState = STATE_D_WRITE_INIT_S;
        }

        writeReady = true;
        inWindow = false;

        setAutoCommit(Horizon.getSettings().get(Settings.SYSTEM, Settings.AUTO_COMMIT, boolean.class));
        return true;
    }

    /**
     * Sets all internal values that control this floppy to a known clean initial state.
     */
    @Override
    public void initialize() {
        super.initialize();
        currentState = STATE_SECTOR_0;
        stateCounter = 0;
        syncDetected = false;
        inWindow = false;
        writeReady = false;
        indexFlag = false;
        diskSide = 0;
        crcValue = 0;
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
    @Override
    public void insertDisk(String fileName, int[] diskData) {
        super.insertDisk(fileName, diskData);
        if (diskData != null) {
            if (diskData.length < 100000) {
                doubleDensityImage = false;
            } else if ((diskData.length >= 100000) && (diskData.length < 400000)) {
                doubleDensityImage = true;
            }
        } else {
            doubleDensityImage = false;
        }
    }

    /**
     * When reading a sector, fetch the next sequential byte, and update our CRC to include that new value.
     * 
     * @return the next sequential sector read byte
     */
    private int fetchByte() {
        int data = 0;

        if ((getDiskData() != null) && (bytePointer < getDiskData().length)) {
            data = getDiskData()[bytePointer++];
        }

        // Accumulate checksum, XOR with rotate left
        crcValue ^= data;
        crcValue = crcValue << 1;
        if (crcValue > 0xFF) {
            crcValue = (crcValue & 0xFF) + 1;
        }

        return data;
    }

    /**
     * Set the initial sector start byte pointer when reading or writing a sector. This factors in the density, the
     * number of sides, as well as the disk format.
     */
    private void initializeBytePointer() {
        if (diskSide > 0) {
            bytePointer = (((MAX_TRACKS * 2) - 1) - getCurrentTrack());
        } else {
            bytePointer = getCurrentTrack();
        }

        bytePointer *= SECT_PER_TRACK;
        bytePointer += getCurrentSector();

        if (doubleDensityImage) {
            bytePointer *= DD_SECTOR_SIZE;
        } else {
            bytePointer *= SD_SECTOR_SIZE;
        }
    }

    /**
     * Generate the A-status value that mimics the kind of state information the physical controller provides. Various
     * bits in the returned byte contain different status info, such as whether a sector hole had been found, or if the
     * drive motor is on.
     * 
     * @return the current A, B, or C status register values
     */
    protected int getStatus(int paramReturned) {
        int status = 0;
        if (getFileName() == null) {
            status = D_SECTOR_FLAG; // No disk in drive
        } else {
            int sectorHole = isSectorHoleDetected() ? D_SECTOR_FLAG : 0;
            int indexFlagNum = indexFlag ? D_INDEX_FLAG : 0;
            int doubleDensity = doubleDensityImage ? D_DENSITY_FLAG : 0;
            int motorOn = D_MOTOR_ON_FLAG;
            status = sectorHole | indexFlagNum | doubleDensity | motorOn;
            if (paramReturned == 1) {
                // A status
                int inWindowNum = inWindow ? D_WINDOW_FLAG : 0;
                int readEnableNum = readEnable ? D_READ_ENABLE : 0;
                int syncDetectedNum = syncDetected ? D_SYNC_FLAG : 0;
                status = status | inWindowNum | readEnableNum | syncDetectedNum;
            } else if (paramReturned == 2) {
                // B status
                int writeOK = writeReady ? D_WRITE_FLAG : 0;
                int writeProtect = isWriteProtect() ? D_WRITE_PROTECT_FLAG : 0;
                int trackZero = (getCurrentTrack() == 0) ? D_TRACK_0_FLAG : 0;
                status = status | writeOK | writeProtect | trackZero;
            } else {
                // C status
                status = status | getCurrentSector();
            }
        }
        return status;
    }

    /**
     * Reading data from a sector. This includes reading a specific byte located in the track/sector we are positioned
     * at, as well as a final checksum. Since we do not actually store a checksum, we just generate our own while
     * reading all of these bytes, then pass that back when asked, where the check is guaranteed to always succeed.
     * 
     * @return an integer representing one of the data sector bytes, or finally the CRC for the sector we just read.
     */
    protected int readByte() {
        int data = 0;

        // Set machine state to SD/DD sector-read
        if ((currentState != STATE_S_READ_DATA) && (currentState != STATE_D_READ_DATA)
                && (currentState != STATE_D_READ_CRC)) {

            initializeBytePointer();

            if (doubleDensityImage) {
                currentState = STATE_D_READ_DATA;
                bytesToXfer = DD_SECTOR_SIZE;
            } else {
                currentState = STATE_S_READ_DATA;
                bytesToXfer = SD_SECTOR_SIZE;
            }
        }
        stateCounter = 10;

        switch (currentState) {
        case STATE_S_READ_DATA:
            bytesToXfer--;
            if (bytesToXfer <= 0) {
                currentState = STATE_D_READ_CRC;
            }
            data = fetchByte();
            break;

        case STATE_D_READ_DATA:
            bytesToXfer--;
            if (bytesToXfer <= 0) {
                currentState = STATE_D_READ_CRC;
            }
            data = fetchByte();
            break;

        case STATE_D_READ_CRC:
            data = crcValue;
            currentState = STATE_D_READ_END;
            stateCounter = 2;
            break;

        default:
            Horizon.displayErrorMessage("Read byte state machine value not supported: " + currentState);
            System.exit(0);
            break;
        }
        return data;

    }

    /**
     * Set several control parameters
     * 
     * @param params
     *            control order parameters
     */
    protected void setControllerOrders(int params) {
        updateMachineState(false);
        writeDoubleDensity = ((params & D_CTRL_DENSITY) > 0);
        diskSide = (((params & D_CTRL_SIDE_SELECT) > 0) ? 1 : 0);
        if (writeReady) {
            // Precompensation option is not needed
        } else {
            setStepDirection(((params & D_CTRL_DP) > 0) ? 1 : 0);
        }
        setStepFlipFlop(((params & D_CTRL_STEP) > 0) ? 1 : 0);
    }

    /**
     * This emulates the transition between states that a real spinning drive would show, including advance to next
     * states, with suitable delays to keep things in sync.
     * 
     * @param returningSectorNumber
     *            is true if request is returning sector number in status
     */
    protected void updateMachineState(boolean returningSectorNumber) {
        switch (currentState) {
        case STATE_SECTOR_0:
            boolean randomize = false;
            if (returningSectorNumber) {
                // If advancing to new sector and are returning B status, then we are not really trying
                // to do disk I/O. This kind of operation appears to be used to generate pseudo random
                // numbers in BASIC. By setting our state counter to a high random value, and delaying
                // until we indicate a sector hole, we simulate an unpredictable delay each time so the
                // RND() function returns something unique on each call, most importantly the first
                // time after boot.
                if (stateCounter <= 0) {
                    // First time in set a random count down.
                    stateCounter = new Random().nextInt(256) + 10;
                    randomize = true;
                } else {
                    // On subsequent calls, continue counting down
                    stateCounter--;
                    if (stateCounter > 0) {
                        // Not done with random count down, keep looping.
                        randomize = true;
                    }
                }
            }

            if (!randomize) {
                int currentSector = getCurrentSector() + 1;
                if ((currentSector > 0) && (currentSector < SECT_PER_TRACK)) {
                    indexFlag = false;
                } else if (getCurrentSector() >= SECT_PER_TRACK) {
                    currentSector = 0;
                    indexFlag = true;
                }
                setCurrentSector(currentSector);
                currentState = STATE_SECTOR_1;
                syncDetected = false;
                crcValue = 0;
                setSectorHoleDetected(true);
                stateCounter = 5;
            }
            break;

        case STATE_SECTOR_1:
            stateCounter--;
            if (stateCounter <= 0) {
                currentState = STATE_SECTOR_2;
                stateCounter = 3;
            }
            inWindow = true;
            readEnable = true;
            break;

        case STATE_SECTOR_2:
            stateCounter--;
            inWindow = false;
            syncDetected = true;
            if (stateCounter <= 0) {
                currentState = STATE_SECTOR_0;
            }
            break;

        case STATE_S_READ_DATA:
        case STATE_D_READ_DATA:
            // Allow OS to abandon reading a sector, probably due to failure
            // occurring during verification. This is unlikely, unless one is
            // saving data from the active DOS area that is changing out from
            // under the saved sector
            stateCounter--;
            if (stateCounter <= 0) {
                // Give up.
                currentState = STATE_SECTOR_0;
            }
            break;

        case STATE_D_READ_CRC:
            break;

        case STATE_D_READ_END:
            stateCounter--;
            if (stateCounter <= 0) {
                currentState = STATE_SECTOR_0;
            }
            break;

        case STATE_D_WRITE_INIT1:
            break;

        case STATE_D_WRITE_INIT2:
            stateCounter--;
            if (stateCounter <= 0) {
                currentState = STATE_SECTOR_0;
            }
            break;

        case STATE_D_WRITE_INIT_S:
            break;

        case STATE_D_WRITE_DATA:
            break;

        case STATE_D_WRITE_CRC:
            break;

        default:
            Horizon.displayErrorMessage("Floppy machine state not implemented: " + currentState);
            System.exit(0);
            break;
        }
    }

    /**
     * Request to write one byte to the current sector we are updating. We keep track of the counts of such data coming
     * in, including the leading padding and sync characters, the actual 256/512 bytes of data, and the trailing CRC
     * value. Only the 256/512 bytes of data matter to us since we don't care about the leading padding, and the CRC is
     * something we recalculate ourselves when we read the data subsequently since there is no chance of errors, unlike
     * a real floppy that can corrupt what is written.
     * 
     * @param data
     *            a single byte of data we want to process for the write
     */
    protected int writeByte(int data) {
        // If trying to write double density to a single density disk image,
        // do nothing, which will eventually lead to a density error message.
        if (!doubleDensityImage && writeDoubleDensity) {
            return 0;
        }

        switch (currentState) {
        case STATE_D_WRITE_INIT1:
            // double-density sector write
            bytesToXfer = DD_SECTOR_SIZE;
            if (data == SYNC_CHARACTER) {
                currentState = STATE_D_WRITE_INIT2;
            }
            break;

        case STATE_D_WRITE_INIT2:
            currentState = STATE_D_WRITE_DATA;
            break;

        case STATE_D_WRITE_INIT_S:
            // single-density sector write
            bytesToXfer = SD_SECTOR_SIZE;
            if (data == SYNC_CHARACTER) {
                currentState = STATE_D_WRITE_DATA;
            }
            break;

        case STATE_D_WRITE_DATA:
            if (bytesToXfer > 0) {
                if ((getDiskData() != null) && (bytePointer < getDiskData().length)) {
                    getDiskData()[bytePointer++] = data;
                }

                bytesToXfer--;
                if (bytesToXfer <= 0) {
                    currentState = STATE_D_WRITE_CRC;
                }
            }

            break;

        case STATE_D_WRITE_CRC:
            // Checksum byte not actually written into disk image file
            writeReady = false;
            currentState = STATE_SECTOR_0;

            // Either commit this sector to the main file system, or flag the
            // fact that we have a change that needs to be written out to the
            // local file system: This decision is controlled by the System menu
            // Auto Commit option.
            if (isAutoCommit()) {
                commitChanges();
            } else {
                setCommitPending(true);
            }
            break;

        default:
            Horizon.displayErrorMessage("Error in machine state during disk write: " + currentState);
            currentState = STATE_SECTOR_0;
            data = 0;
            System.exit(0);
            break;
        }

        return data;
    }
}