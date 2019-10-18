package org.alien4cloud.plugin.datagouv_mls.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ProcessLauncher {

    public static int launch(String[] cmd, StringBuffer disp) {

        int exitCode = -1;

        ProcessBuilder processBuilder = new ProcessBuilder();

        processBuilder.command(cmd);

        try {

            Process process = processBuilder.start();

            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                disp = disp.append(line).append("\n");
            }

            reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            while ((line = reader.readLine()) != null) {
                disp = disp.append(line).append("\n");
            }

            exitCode = process.waitFor();
        } catch (IOException e) {
            disp.append("EXCEPTION: " + e.getMessage());
        } catch (InterruptedException e) {
            disp.append("EXCEPTION: " + e.getMessage());
        }

        return exitCode;
    }

}