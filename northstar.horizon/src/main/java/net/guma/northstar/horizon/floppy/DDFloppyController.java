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

import net.guma.northstar.horizon.Horizon;
import net.guma.northstar.horizon.Resources;

/**
 * This is the double-density and double sided floppy controller, implementing both access to the PROM bootstrap code,
 * as well as reads against the memory mapped range of addresses that control access to the actual drives.
 */
public class DDFloppyController extends FloppyController {

    private int[] doubleDensityPROM = null;

    /**
     * Create the floppy controller, including an instance of each drive. We create one more drive than supported since
     * drives are numbered starting from one, but we have a zero drive as a dumping ground for invalid drive selections.
     */
    public DDFloppyController() {
        floppies = new DDFloppyDrive[getTotalFloppies() + 1];

        // Read controller PROM data the first time
        if (doubleDensityPROM == null) {
            try {
                doubleDensityPROM = Resources.getHexResourceAsBinary(Resources.RESOURCE_DD_CONTROLLER);
            } catch (Exception e) {
                Horizon.displayErrorMessage(
                        "Failed reading double density floppy controller firmware: " + e.getMessage());
                System.exit(0);
            }
        }
    }

    /**
     * Return the total number of drives this controller supports.
     * 
     * @param the
     *            drives we support, for double density this is 4.
     */
    @Override
    public int getTotalFloppies() {
        return 4;
    }

    /**
     * A read request for the range of memory mapped to the floppy controller comes here. Depending on which lower order
     * bits are set indicates the operation requested, both fetching PROM instructions as well as interacting with the
     * drives themselves.
     * 
     * @param address
     *            memory address we are reading from
     * @return the corresponding data for that address, could be bootstrap code, or status information for the
     *         controller and drives.
     */
    @Override
    public int read(int address) {
        int retValue = 0;

        FloppyDrive floppy = floppies[currentFloppy];

        int opType = (address >> 8) & 0x3;
        int params = (address & 0xFF);

        switch (opType) {
        case 0:
            // Return PROM data
            retValue = doubleDensityPROM[params];
            break;

        case 1:
            // Write a byte of data to current sector
            floppy.updateMachineState(false);
            retValue = floppy.writeByte(params);
            break;

        case 2:
            // ***************************************************************
            // MODE 2 Controller Orders
            //
            // .--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.
            // |--------BS-------|--2--|DD|SS|DP|ST|-----DS----|
            // .--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.
            //
            // Load 8-bit order register from low order 8 address bits.
            //
            // DD Controls density on write DD=l for double density and
            // DD=0 for single density.
            //
            // SS Specifies the side of a double-sided disk. The
            // bottom side (and only side of a single-sided disk)
            // is selected when SS=0 The second (top) side is
            // selected when SS=l.
            //
            // DP has shared use. During stepping operations, DP=0
            // specifies a step out and DP=l specifies a step in.
            // During write operations, write pre-compensation is
            // invoked if and only if DP=l.
            //
            // ST controls the level of the head step signal to the disk
            // drives.
            //
            // DS is the drive select field, encoded as follows
            //
            // 0=no drive selected
            // l=drive 1 selected
            // 2=drive 2 selected
            // 4=drive 3 selected
            // 8=drive 4 selected
            //
            // ***************************************************************
            if ((params & 0x01) > 0) {
                currentFloppy = 1;
            } else if ((params & 0x02) > 0) {
                currentFloppy = 2;
            } else if ((params & 0x04) > 0) {
                currentFloppy = 3;
            } else if ((params & 0x08) > 0) {
                currentFloppy = 4;
            } else {
                currentFloppy = 0;
            }
            floppy.updateMachineState(false);
            floppy.setControllerOrders(params);
            break;

        case 3:
            // ***************************************************************
            // MODE 3 Controller Commands
            // .--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.
            // |--------BS-------|--3--|----DM-----|----CC-----|
            // .--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.
            //
            // Perform a disk controller command. The commands are
            // specified by the 8 low order address bits.
            //
            // DM The DM field controls what gets multiplexed onto the
            // DI bus during the command.
            //
            // l=A-status
            // 2=B-status
            // 3=C-status
            // 4=Read data (may enter wait state)
            //
            // CC Command code.
            //
            // 0=no operation
            // l=reset sector flag
            // 2=disarm interrupt
            // 3=arm interrupt
            // 4=set body (diagnostic)
            // 5=turn on drive motors
            // 6=begin write
            // 7=reset controller, de-select drives, stop motors
            //
            // ***************************************************************
            int paramReturned = (params >> 4);
            floppy.updateMachineState(paramReturned == 3);
            boolean readData = (paramReturned == 4);
            int commandCode = (params & 0x0F);

            switch (commandCode) {
            case 0:
                // NOP, just return requested data
                break;
            case 1:
                // Reset sector flag
                floppy.setSectorHoleDetected(false);
                break;
            case 2:
                // Disarm interrupt. Optional and not usually used in the
                // actual Horizon and not currently implemented here.
                break;
            case 3:
                // Arm interrupt. Optional and not usually used in the
                // actual Horizon and not currently implemented here.
                break;
            case 4:
                // Set diagnostic body. Not implemented.
                break;
            case 5:
                // Turn on drive motors. Not needed, emulated to always be on.
                break;
            case 6:
                // Set up to write a sector
                if (!floppy.beginWrite()) {
                    // Failure to set up for write, density mismatch, so return
                    // a value that will lead to an error message condition.
                    return 0;
                }
                break;
            case 7:
                // Reset controller, raise heads, stop motors
                // floppy.initialize();
                break;
            default:
                break;
            }

            if (readData) {
                // Return back next byte we are reading from sector
                retValue = floppy.readByte();
            } else {
                // Return A, B, or C status
                retValue = floppy.getStatus(paramReturned);
            }
            break;
        }
        return retValue;

    }

    /**
     * Create a new floppy drive unit of the type this controller supports.
     * 
     * @return a single density drive unit
     */
    @Override
    protected FloppyDrive getNewFloppyDrive(int floppyNumber) {
        return new DDFloppyDrive(floppyNumber);
    }
}