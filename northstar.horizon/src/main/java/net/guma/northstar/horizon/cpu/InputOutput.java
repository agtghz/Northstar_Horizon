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
package net.guma.northstar.horizon.cpu;

import java.util.LinkedList;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import com.codingrodent.microprocessor.IBaseDevice;
import com.codingrodent.microprocessor.Z80.CPUConstants;

import net.guma.northstar.horizon.Horizon;
import net.guma.northstar.horizon.gui.Sounds;

/**
 * Provides methods that handle the IN/OUT type CPU operations for reading/writing from interfaces such as serial ports.
 */
public class InputOutput implements IBaseDevice {

    private LinkedList<Integer> keyBuffer = new LinkedList<Integer>();
    private Integer lastKey = null;
    private boolean piInputFlag = false;
    private Object lastKeySync = new Object();

    /**
     * Provides access to a linked list buffer of keys typed
     * 
     * @return the buffer containing keys pressed
     */
    public LinkedList<Integer> getKeyBuffer() {
        return keyBuffer;
    }

    /**
     * Main handler for Z-80 opcodes that perform an IN from a particular port.
     * 
     * @param address
     *            is the port address to read from.
     */
    @Override
    public int IORead(int address) {
        int data = 0;
        switch (address) {
        case 0:
        case 1:
            // Parallel I/O Data. Returns last key as long as PI flag is true
            synchronized (lastKeySync) {
                if (piInputFlag) {
                    if (lastKey != null) {
                        data = lastKey.intValue();
                    }
                }
            }
            break;
        case 2:
            // Standard Serial I/O Data
            if (Horizon.getWorkspace().keyAvailable()) {
                data = Horizon.getWorkspace().getNextKey();
                if (data == 10) {
                    data = 13;
                }
            }
            break;
        case 3:
            // Standard Serial I/O Control
            data = 0;
            if (Horizon.getWorkspace().keyAvailable()) {
                data = 3;
            } else {
                data = 1;
            }
            data = data | 0x80;
            break;
        case 4:
            // Second Serial I/O Data, not currently supported
            break;
        case 5:
            // Second Serial I/O Control, not currently supported
            break;
        case 6:
        case 7:
            // Motherboard Control, operation based on A-register value.
            switch (Horizon.getZ80().getRegisterValue(CPUConstants.RegisterNames.A)) {
            case 30:
                // Parallel port input flag reset
                piInputFlag = false;
                break;
            default:
                // All other operations not currently supported
                break;
            }
            break;
        default:
            break;
        }
        return data;

    }

    /**
     * Main handler for Z-80 opcodes that perform an OUT to a particular port.
     * 
     * @param address
     *            is the port address to output to.
     * @param data
     *            is the 8-bit value to send to the port.
     */
    @Override
    public void IOWrite(int address, int data) {
        switch (address) {
        case 0:
        case 1:
            // Parallel I/O Data, not currently supported
            break;
        case 2:
            // Standard Serial I/O Data
            if (data == 7) {
                // Bell character makes a sound. Too bad no simple way in Java
                // of adding more complex sounds here and elsewhere.
                Sounds.tone(1000, 100);
            } else {
                if (data == 8) {
                    // Backspace removes last character from text console
                    Document doc = Horizon.getWorkspace().getTextPane().getDocument();
                    if (doc.getLength() > 0) {
                        try {
                            doc.remove(doc.getLength() - 1, 1);
                        } catch (BadLocationException ble) {}
                    }
                } else {
                    // All other character print directly to console
                    data = data & 0x7F;
                    System.out.print((char) data);
                }
            }
            break;
        case 3:
            // Standard Serial I/O Control, nothing to do since there is no
            // actual port to reconfigure.
            break;
        case 4:
            // Second Serial I/O Data, not currently supported
            break;
        case 5:
            // Second Serial I/O Control, not currently supported
            break;
        case 6:
        case 7:
            // Motherboard Control, not currently supported
            break;
        default:
            break;
        }
    }

    /**
     * Sets a single last key pressed, used by parallel port input
     * 
     * @param lastKey
     *            is the last key pressed
     */
    public void setLastKey(Integer lastKey) {
        synchronized (lastKeySync) {
            this.lastKey = lastKey;
            piInputFlag = true;
        }
    }
}