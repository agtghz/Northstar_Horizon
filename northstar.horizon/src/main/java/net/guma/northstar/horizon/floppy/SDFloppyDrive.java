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
 * Represents one physical single-density floppy drive, all of which are managed by the single controller. In the real
 * machine there can be up to 3 of these, one or two within the actual case, and optionally one as an external enclosed
 * unit.
 */
public class SDFloppyDrive extends FloppyDrive {
    private static final int SECT_PER_TRACK = 10;
    private static final int SECTOR_SIZE = 256;
    private static final int SYNC_CHARACTER = 0xFB;

    // Machine states
    private static final int STATE_SECTOR_0 = 0;
    private static final int STATE_SECTOR_1 = 10;
    private static final int STATE_SECTOR_2 = 20;
    private static final int STATE_S_READ_DATA = 11;
    private static final int STATE_S_READ_CRC = 12;
    private static final int STATE_S_READ_END = 13;
    private static final int STATE_S_WRITE_LEADIN = 71;
    private static final int STATE_S_WRITE_DATA = 72;
    private static final int STATE_S_WRITE_CRC = 73;
    private static final int STATE_S_WRITE_END = 74;

    // Status-Byte Flags
    private static final int S_SECTOR_FLAG = 0x80;
    private static final int S_WINDOW_FLAG = 0x40;
    private static final int S_MTR_STAT = 0x10;
    private static final int S_WRITE_READY = 0x08;
    private static final int S_SYNC_FLAG = 0x04;
    private static final int S_WRITE_PROTECT_FLAG = 0x02;
    private static final int S_TRACK_0_FLAG = 0x01;

    private int currentState;
    private int stateCounter;
    private boolean syncDetected;
    private boolean inWindow;
    private boolean writeReady;

    private int bytesToXfer;
    private int crcValue;
    private int dataOffset;

    /**
     * Create the drive, including saving our drive number and initializing various values.
     * 
     * @param driveNumber
     */
    public SDFloppyDrive(int driveNumber) {
        setDriveNumber(driveNumber);
        initialize();
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
        crcValue = 0;
    }

    /**
     * About to write out a sector. Reset the raw and data byte counts to zero. The raw count keeps track of all data
     * sent to the write logic, including leading padding and sync characters that we don't actually write out, while
     * the data count keeps track of the bytes of actual sector data we use as an offset into the disk image array.
     * 
     * @return true if ok to write, for single density controller always ok.
     */
    protected boolean beginWrite() {
        writeReady = true;
        inWindow = false;
        currentState = STATE_S_WRITE_LEADIN;
        dataOffset = ((getCurrentTrack() * 10) + getCurrentSector()) * SECTOR_SIZE;
        bytesToXfer = SECTOR_SIZE;
        setAutoCommit(Horizon.getSettings().get(Settings.SYSTEM, Settings.AUTO_COMMIT, boolean.class));
        return true;
    }

    /**
     * Return either an A or B status byte.
     * 
     * @param gateBStatus
     *            is 0 if we want A, and 1 if we want B
     * @return the status value asked for.
     */
    protected int getStatus(int gateBStatus) {
        int status = 0;
        if (getFileName() == null) {
            status = S_SECTOR_FLAG; // No disk in drive
        } else {
            int sectorHole = isSectorHoleDetected() ? S_SECTOR_FLAG : 0;
            int windowFlag = inWindow ? S_WINDOW_FLAG : 0;
            int motorOn = S_MTR_STAT;
            status = sectorHole | windowFlag | motorOn;
            if (gateBStatus == 1) {
                status = status | getCurrentSector();
            } else {
                int writeOKNum = writeReady ? S_WRITE_READY : 0;
                int syncDetectedNum = syncDetected ? S_SYNC_FLAG : 0;
                int writeProtectNum = isWriteProtect() ? S_WRITE_PROTECT_FLAG : 0;
                int trackZeroNum = (getCurrentTrack() == 0) ? S_TRACK_0_FLAG : 0;
                status = status | writeOKNum | syncDetectedNum | writeProtectNum | trackZeroNum;
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
        int data;
        if ((currentState != STATE_S_READ_DATA) && (currentState != STATE_S_READ_CRC)) {
            currentState = STATE_S_READ_DATA;
            dataOffset = ((getCurrentTrack() * 10) + getCurrentSector()) * SECTOR_SIZE;
            bytesToXfer = SECTOR_SIZE;
        }

        switch (currentState) {
        case STATE_S_READ_DATA:
            bytesToXfer--;
            if (bytesToXfer <= 0) {
                currentState = STATE_S_READ_CRC;
            }
            if ((getDiskData() != null) && (dataOffset < getDiskData().length)) {
                data = getDiskData()[dataOffset++];
                // Accumulate checksum, XOR with rotate left
                crcValue ^= data;
                crcValue = crcValue << 1;
                if (crcValue > 0xFF) {
                    crcValue = (crcValue & 0xFF) + 1;
                }
            } else {
                // No disk inserted, accumulate empty data
                data = 0;
            }

            break;

        case STATE_S_READ_CRC:
            if (getDiskData() != null) {
                data = crcValue;
            } else {
                // If no disk inserted, return invalid checksum to force error
                data = 0xFF;
            }
            currentState = STATE_S_READ_END;
            stateCounter = 2;
            break;

        default:
            Horizon.displayErrorMessage("Error in read, machine state not implemented: " + currentState);
            data = 0;
            System.exit(0);
            break;
        }

        return data;
    }

    /**
     * Allows setting controller orders that hardware supports. This is only needed for double density controllers, so
     * for our single density logic here we do nothing since this will never be called.
     * 
     * @param params
     *            Controller command info, not used here.
     */
    protected void setControllerOrders(int params) {}

    /**
     * Set when setting or clearing the sector hole detected flag. We override this so that when clearing the flag we
     * can add some additional logic to set the state correctly.
     */
    @Override
    protected void setSectorHoleDetected(boolean sectorHoleDetected) {
        super.setSectorHoleDetected(sectorHoleDetected);
        if (!sectorHoleDetected && (currentState > STATE_SECTOR_0)) {
            currentState = STATE_SECTOR_0;
            stateCounter = 0;
            syncDetected = false;
            crcValue = 0;
        }
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
                // Normal operation finds sector hole and moves us to next state
                int currentSector = getCurrentSector() + 1;
                if (currentSector >= SECT_PER_TRACK) {
                    currentSector = 0;
                }
                setCurrentSector(currentSector);
                syncDetected = false;
                crcValue = 0;
                currentState = STATE_SECTOR_1;
                setSectorHoleDetected(true);
                stateCounter = 5;
            }
            break;

        case STATE_SECTOR_1:
            writeReady = false;
            inWindow = true;
            stateCounter--;
            if (stateCounter <= 0) {
                currentState = STATE_SECTOR_2;
                stateCounter = 6;
            }

            break;

        case STATE_SECTOR_2:
            inWindow = false;
            syncDetected = true;
            stateCounter--;
            if (stateCounter <= 0) {
                currentState = STATE_SECTOR_0;
            }
            break;

        case STATE_S_READ_DATA:
            break;

        case STATE_S_READ_CRC:
            break;

        case STATE_S_READ_END:
            stateCounter--;
            if (stateCounter <= 0) {
                currentState = STATE_SECTOR_0;
            }
            break;

        case STATE_S_WRITE_LEADIN:
            break;

        case STATE_S_WRITE_DATA:
            break;

        case STATE_S_WRITE_CRC:
            break;

        case STATE_S_WRITE_END:
            stateCounter--;
            if (stateCounter <= 0) {
                currentState = STATE_SECTOR_0;
            }
            break;

        default:
            Horizon.displayErrorMessage("Floppy machine state not implemented: " + currentState);
            System.exit(0);
            break;

        }
    }

    /**
     * Request to write one byte to the current sector we are updating. We keep track of the counts of such data coming
     * in, including the leading padding and sync characters, the actual sector bytes of data, and the trailing CRC
     * value. Only the actual sector bytes of data matter to us since we don't care about the leading padding, and the
     * CRC is something we recalculate ourselves when we read the data subsequently since there is no chance of errors,
     * unlike a real floppy that can corrupt what is written.
     * 
     * @param data
     *            a single byte of data we want to process for the write
     */
    protected int writeByte(int data) {
        switch (currentState) {

        case STATE_S_WRITE_LEADIN:
            // Ignore any zero value lead-in padding, until we hit the sync
            if (data == SYNC_CHARACTER) {
                // Sync byte, about to start to write sector
                currentState = STATE_S_WRITE_DATA;
            }
            break;

        case STATE_S_WRITE_DATA:
            if (getDiskData() != null) {
                getDiskData()[dataOffset++] = data;
            }
            bytesToXfer--;
            if (bytesToXfer <= 0) {
                currentState = STATE_S_WRITE_CRC;
            }
            break;

        case STATE_S_WRITE_CRC:
            // checksum byte not actually written into disk image file.
            writeReady = false;
            currentState = STATE_S_WRITE_END;
            stateCounter = 2;

            // Either commit this sector to the main file system, or flag the fact that we have a change that
            // needs to be written out to the local file system: This decision is controlled by the System
            // menu Auto Commit option.
            if (isAutoCommit()) {
                commitChanges();
            } else {
                setCommitPending(true);
            }
            break;

        default:
            Horizon.displayErrorMessage("Error in machine state during disk write: " + currentState);
            data = 0;
            System.exit(0);
            break;
        }
        return data;
    }
}