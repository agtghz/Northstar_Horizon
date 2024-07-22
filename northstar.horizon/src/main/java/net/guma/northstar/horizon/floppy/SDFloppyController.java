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
 * This is the single-density floppy controller, implementing both access to the PROM bootstrap code, as well as reads
 * against the memory mapped range of addresses that control access to the actual drives, up to 3 individual ones.
 */
public class SDFloppyController extends FloppyController {

    // This is the 256 byte bootstrap PROM for the single density controller.
    private int[] singleDensityPROM = null;

    /**
     * Create the floppy controller, including an instance of each drive. We create one more drive than supported since
     * drives are numbered starting from one, but we have a zero drive as a dumping ground for invalid drive selections.
     */
    public SDFloppyController() {
        floppies = new SDFloppyDrive[getTotalFloppies() + 1];

        // Read controller PROM data the first time
        if (singleDensityPROM == null) {
            try {
                singleDensityPROM = Resources.getHexResourceAsBinary(Resources.RESOURCE_SD_CONTROLLER);
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
     *            drives we support, for single density this is 3.
     */
    public int getTotalFloppies() {
        return 3;
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
    public int read(int address) {
        int retValue = 0;

        FloppyDrive floppy = floppies[currentFloppy];

        int opType = (address >> 8) & 0x3;
        int params = (address & 0xFF);

        switch (opType) {
        case 0:
            // ***************************************************************
            // Case 0 Optional PROM addressing
            //
            // .--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.
            // |--------BS-------|--0--|------PROM address-----|
            // .--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.
            //
            // Read byte from optional 256 bytes of PROM. Case 0 can be
            // made to address the standard PROM as well as case 1 by
            // making the following modification to the MDS controller
            // board. Cut the AD8/ trace that connects to 3E pin 14 and
            // 3F pin 14 and connect those two pins to ground instead.
            // This modification will allow the PROM to be addressed at
            // the beginning of the MDS controller's 1K address space.
            //
            // ***************************************************************
        case 1:
            // ***************************************************************
            // Case 1 PROM addressing
            //
            // .--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.
            // |--------BS-------|--1--|------PROM address-----|
            // .__.__.__.__.__.__.__.__.__.__.__.__.__.__.__.__.
            //
            // Read byte from standard 256 bytes of PROM.
            //
            // ***************************************************************
            retValue = singleDensityPROM[params];
            break;
        case 2:
            // ***************************************************************
            // Case 2 Write byte of data
            //
            // .--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.
            // |--------BS-------|--2--|----------Data---------|
            // .--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.
            //
            // Write a byte of data to the disk. Hang if the write shift
            // register is not empty. The low order 8 bits specify the
            // byte to be written.
            //
            // ***************************************************************
            floppy.updateMachineState(false);
            floppy.writeByte(params);
            break;
        case 3:
            // ***************************************************************
            // Case 3 Controller Commands
            //
            // .--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.
            // |--------BS-------|--3--|MO|RD|BST|---CC--|M1|M0|
            // .--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.--.
            //
            // Perform a disk controller command. The commands are
            // specified by the 8 low order address bits.
            //
            // MO if 1 then turn disk drive motors on if they are off
            // and reset auto-motor-off timer. If 0 then no action.
            //
            // RD If one then read a data byte from the read shift
            // register and gate it onto the Data Input Bus. Hang
            // the CPU until the read shift register if full. If
            // zero then gate status bits onto the Data Input Bus.
            //
            // BST If one then gate B-status byte (see below) onto the
            // Data Input Bus. If zero then gate the A-status byte
            // onto the Data Input Bus.
            //
            // CC Command code.
            // 0=load drive select register from M1,M0. Lower head on
            // selected drive.
            // 1=write record, start a write sector sequence
            // 2=load track step flip-flow from M0.
            // 3=load interrupt armed flip-flop from M0.
            // 4=no operation
            // 5=reset sector flag
            // 6=reset controller, raise heads, stop motors.
            // 7=load step direction from M0.(1=step in,0=step out)
            //
            // STATUS BYTES
            //
            // There are two different status bytes that can be read on
            // the Data Input Bus.
            //
            // A-Status
            // .---.---.---.---.---.---.---.---.
            // |SF |WN | 0 |MO |WRT|BDY|WP |TR0|
            // .---.---.---.---.---.---.---.---.
            //
            // B-Status
            // .---.---.---.---.---.---.---.---.
            // |SF |WN | 0 |MO |-------SP------|
            // .---.---.---.---.---.---.---.---.
            //
            // SF Sector Flag. Indicates a sector hole was detected.
            //
            // WN Window Flag. Indicates if the status byte was read
            // during the window.
            //
            // MO Motor On. Indicates that the motors are on.
            //
            // WRT Write. Indicates that controller is ready to receive
            // data bytes to write.
            //
            // BDY Body. Indicates that a sync char was found and that
            // data bytes can now be read.
            //
            // WP Write Protect. Indicates that the Diskette installed in
            // the selected drive is write protected.
            //
            // TR0 Track 0. Indicates that the selected drive is at track 0.
            //
            // SP Sector Position. Indicates the current sector position.
            //
            // ***************************************************************
            int readShiftReg = (params >> 6) & 0x01; // Reading data bytes
            int gateBStatus = (params >> 5) & 0x01; // Return B status, not A
            int commandCode = (params >> 2) & 0x07; // Command 0-7 to perform
            floppy.updateMachineState(gateBStatus == 1);

            switch (commandCode) {
            case 0:
                // Select drive, load head as well (NOP)
                int targetFloppy = (params & 0x03);
                currentFloppy = targetFloppy;
                if (currentFloppy == 0) {
                    Horizon.displayErrorMessage("Invalid floppy 0 requested");
                    System.exit(0);
                }
                floppy = floppies[currentFloppy];
                break;
            case 1:
                // Set up to write a sector
                floppy.beginWrite();
                break;
            case 2:
                // Load track step flip-flop (0=off, 1=on).
                int stepFlipFlop = (params & 0x01);
                floppy.setStepFlipFlop(stepFlipFlop);
                break;
            case 3:
                // Load interrupt armed flip-flop. Optional and not usually used
                // in the actual Horizon and not currently implemented here.
                // int intArmedFF = (params & 0x01);
                break;
            case 4:
                // NOP, just return a status or data value
                break;
            case 5:
                // Reset sector flag
                floppy.setSectorHoleDetected(false);
                break;
            case 6:
                // Reset controller, raise heads, stop motors
                // floppy.initialize();
                break;
            case 7:
                // Load step direction (0=out, 1=in)
                int stepDir = (params & 0x01);
                floppy.setStepDirection(stepDir);
                break;
            }
            if (readShiftReg == 1) {
                // Return back next byte we are reading from sector
                retValue = floppy.readByte();
            } else {
                // Return either A or B status
                retValue = floppy.getStatus(gateBStatus);
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
    protected FloppyDrive getNewFloppyDrive(int floppyNumber) {
        return new SDFloppyDrive(floppyNumber);
    }
}