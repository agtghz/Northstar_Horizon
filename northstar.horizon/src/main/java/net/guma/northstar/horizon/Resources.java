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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides a way to read various resources such as hex firmware images and character sets, as raw strings such as hex
 * values, and as integer binary arrays.
 */
public abstract class Resources {

    public static final String RESOURCE_WINDOW_PACKAGE = "resources/screensplitter/firmware/WindowPackage.hex";
    public static final String RESOURCE_CHARSET_DIR = "resources/screensplitter/charsets/";
    public static final String RESOURCE_DD_CONTROLLER = "resources/floppy/DDController.hex";
    public static final String RESOURCE_SD_CONTROLLER = "resources/floppy/SDController.hex";
    public static final String RESOURCE_INFO = "resources/info/info.html";
    public static final String RESOURCE_ABOUT = "resources/info/about.html";
    public static final String RESOURCE_LOGO = "resources/info/NSLogo.jpg";

    /**
     * Return a buffered reader to access a resource file
     * 
     * @param resourcePath
     *            path to the resource
     * @return the buffered reader to the resource
     */
    private static BufferedReader getResourceAsBufferedReader(String resourcePath) {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
        return new BufferedReader(new InputStreamReader(in));
    }

    /**
     * Given a path to a resource, read it in and return it as a string.
     * 
     * @param resourcePath
     *            path to the resource
     * @return the resource contents as a string
     * @throws Exception
     *             if resource could not be read
     */
    public static final String getResourceAsString(String resourcePath) throws Exception {
        StringBuffer resourceContents = new StringBuffer();

        try (BufferedReader reader = getResourceAsBufferedReader(resourcePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                resourceContents.append(line + "\n");
            }
        }

        return resourceContents.toString();
    }

    /**
     * Given a path to a resource, read it in and return it as a list collection of lines
     * 
     * @param resourcePath
     *            path to the resource
     * @return the resource contents as a list collection of lines
     * @throws Exception
     *             if resource could not be read
     */
    public static final List<String> getResourceAsList(String resourcePath) throws Exception {
        List<String> resourceLines = new ArrayList<String>();

        try (BufferedReader reader = getResourceAsBufferedReader(resourcePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() > 0) {
                    resourceLines.add(line);
                }
            }
        }

        return resourceLines;
    }

    /**
     * Given a path to a resource in hex format, read it in and return it as a list of hex strings
     * 
     * @param resourcePath
     *            path to the resource
     * @return the string list with the resource
     * @throws Exception
     *             if resource could not be read
     */
    public static final List<String> getHexResourceAsList(String resourcePath) throws Exception {
        List<String> hexData = new ArrayList<String>();

        for (String line : getResourceAsList(resourcePath)) {
            if ((line.length() > 0) && (line.substring(0, 1).toUpperCase().matches("[01234567890ABCDEF]"))) {
                for (String hex : line.split(" ")) {
                    if (hex.length() == 2) {
                        hexData.add(hex);
                    }
                }
            }
        }

        return hexData;
    }

    /**
     * Given a path to a resource in hex format, read it in and return it as a integer array.
     * 
     * @param resourcePath
     *            path to the resource
     * @return the integer array equivalent of the resource
     * @throws Exception
     *             if resource could not be read
     */
    public static final int[] getHexResourceAsBinary(String resourcePath) throws Exception {
        return convertHexListToBinary(getHexResourceAsList(resourcePath));

    }

    /**
     * Return an integer array equivalent of a list of hex strings
     * 
     * @param hexData
     *            list containing hex strings
     * @return the integer array equivalent of the hex list
     */
    public static final int[] convertHexListToBinary(List<String> hexData) {

        int[] binaryData = new int[hexData.size()];
        for (int hexCnt = 0; hexCnt < hexData.size(); hexCnt++) {
            binaryData[hexCnt] = Integer.parseInt(hexData.get(hexCnt), 16);
        }

        return binaryData;
    }
}