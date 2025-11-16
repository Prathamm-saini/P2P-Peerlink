package org.p2p.utils;

import java.util.Random;

public class UploadUtils {

    // Common utility used by backend services
    public static int generateCode() {

        int DYNAMIC_STARTING_PORT = 49152;   // Valid ephemeral range start
        int DYNAMIC_ENDING_PORT = 65000;     // <-- FIX: was 85535 (INVALID)

        Random random = new Random();
        return random.nextInt(DYNAMIC_ENDING_PORT - DYNAMIC_STARTING_PORT)
                + DYNAMIC_STARTING_PORT;
    }
}
