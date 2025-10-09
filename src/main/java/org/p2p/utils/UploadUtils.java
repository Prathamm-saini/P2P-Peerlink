package org.p2p.utils;

import java.util.Random;

public class UploadUtils {

    ///  why static -> common class used by all services
    public static int generateCode(){

        int DYNAMIC_STARTING_PORT = 49152;
        int DYNAMIC_ENDING_PORT = 85535;

        Random random = new Random();
        return random.nextInt(DYNAMIC_STARTING_PORT- DYNAMIC_ENDING_PORT) + DYNAMIC_STARTING_PORT;


    }
}
