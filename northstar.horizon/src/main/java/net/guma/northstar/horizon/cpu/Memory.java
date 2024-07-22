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
package net.guma.northstar.horizon.cpu;

import java.util.Random;

import com.codingrodent.microprocessor.IMemory;

import net.guma.northstar.horizon.Horizon;
import net.guma.northstar.horizon.floppy.FloppyController;

/**
 * Implements the memory model for the Horizon that the Z-80 is running against. This includes both plain RAM, as well
 * as the floppy controller firmware and memory mapped control addresses, plus the ScreenSplitter video card firmware
 * and memory mapped video display. While we include the entire 64k memory space here, we pass through operations
 * against those regions that are re-mapped by the other subsystems, since they are responsible to perform read/write
 * operations against themselves.
 */
public class Memory implements IMemory {

    private int[] memory = new int[65536];

    /**
     * Instantiate the memory.
     */
    public Memory() {
        clearMemory();
    }

    /**
     * Set ram to random values, just like normal hardware.
     */
    public void clearMemory() {
        Random screenRandom = new Random();
        for (int i = 0; i < memory.length; i++) {
            memory[i] = screenRandom.nextInt(256);
        }
    }

    /**
     * Called by the CPU to read a byte of memory from some address. This correctly routes the request to either our
     * main RAM, to the floppy controller or the ScreenSplitter video card, depending on where in the address range the
     * request falls in.
     * 
     * @param address
     *            is the 16-bit memory address to read a byte from.
     */
    @Override
    public int readByte(int address) {
        int memByte;

        if ((address >= FloppyController.FDC_FROM) && (address <= FloppyController.FDC_TO)) {
            // Floppy controller firmware reads
            memByte = Horizon.getFloppyController().read(address);

        } else if ((address >= Horizon.getScreenSplitter().getFirmwareStart())
                && (address <= Horizon.getScreenSplitter().getFirmwareEnd())) {
            // ScreenSplitter firmware reads
            memByte = Horizon.getScreenSplitter().readFirmwareByte(address);

        } else if ((address >= Horizon.getScreenSplitter().getDisplayStart())
                && (address <= Horizon.getScreenSplitter().getDisplayEnd())) {
            // ScreenSplitter video memory reads
            memByte = Horizon.getScreenSplitter().readScreenByte(address);

        } else {
            // Everywhere else is regular memory
            memByte = memory[address];
        }

        return memByte;
    }

    /**
     * Similar to the read a byte version, this reads a word, low byte from the address supplied, and the high byte from
     * the subsequent address.
     * 
     * @param address
     *            is the 16-bit memory address to read a word from.
     */
    @Override
    public int readWord(int address) {
        int word;
        if (address < memory.length) {
            word = readByte(address) + (readByte(address + 1) << 8);
        } else {
            // This really should never happen, but just in case...
            word = readByte(address);
        }
        return word;
    }

    /**
     * Called when the CPU wants to write a byte of memory to a specific address. This handles the various ranges of
     * addresses that map to things other than plain RAM. While some of them, such as ScreenSplitter display RAM can be
     * written, others such as floppy controller firmware do nothing since they are read-only.
     * 
     * @param address
     *            is the 16-bit memory address we want to write to.
     * @param data
     *            is the 8-bit value to write to the address
     */
    @Override
    public void writeByte(int address, int data) {

        if ((address >= FloppyController.FDC_FROM) && (address <= FloppyController.FDC_TO)) {
            // Floppy drive firmware is read only, so write does nothing

        } else if ((address >= Horizon.getScreenSplitter().getFirmwareStart())
                && (address <= Horizon.getScreenSplitter().getFirmwareEnd())) {
            // ScreenSplitter firmware is read only, so write does nothing

        } else if ((address >= Horizon.getScreenSplitter().getDisplayStart())
                && (address <= Horizon.getScreenSplitter().getDisplayEnd())) {
            // ScreenSplitter display is memory mapped to the video window
            Horizon.getScreenSplitter().writeScreenByte(address, data);

        } else {
            // Everywhere else is regular memory
            memory[address] = data;
        }
    }

    /**
     * Similar to the write a byte version, this writes a word, low 8-bits in the data provided to the address given,
     * and high 8-bits in the data to the subsequent address.
     * 
     * @param address
     *            is the 16-bit memory address to write a word from.
     * @param data
     *            is the 16-bit data to write to the address
     */
    @Override
    public void writeWord(int address, int data) {
        writeByte(address, (data & 0xFF));
        if (address < memory.length) {
            writeByte(address + 1, (data >> 8));
        }
    }
}