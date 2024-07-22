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

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

//Provide a way to generate some very basic sounds
public abstract class Sounds {
    public static final float SAMPLE_RATE = 8000f;

    public static final void tone(int hz, int msecs) {
        tone(hz, msecs, 0.4);
    }

    public static final void tone(int hz, int msecs, double vol) {

        try {
            byte[] buf = new byte[1];
            AudioFormat af = new AudioFormat(SAMPLE_RATE, // sampleRate
                    8, // sampleSizeInBits
                    1, // channels
                    true, // signed
                    false); // bigEndian
            SourceDataLine sdl = AudioSystem.getSourceDataLine(af);
            sdl.open(af);
            sdl.start();
            for (int i = 0; i < msecs * 8; i++) {
                double angle = i / (SAMPLE_RATE / hz) * 2.0 * Math.PI;
                buf[0] = (byte) (Math.sin(angle) * 127.0 * vol);
                sdl.write(buf, 0, 1);
            }
            sdl.drain();
            sdl.stop();
            sdl.close();
        } catch (Exception e) {
            System.out.println("Unable to play sound: " + e.getMessage());
        }
    }
}