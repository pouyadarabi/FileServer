package vd_server.helper;

import com.google.gson.JsonArray;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by pouya on 1/20/17.
 */
public class common {

    public static String responseBuilder(Integer key, boolean status, String message) {

        String stringStatus = "ERR";
        if (status)
            stringStatus = "OK";

        return stringStatus + "|" + key + "|" + message;

    }

    public static JsonArray getfileList(String directory) {
        JsonArray fileNames = new JsonArray();
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Paths.get(directory))) {
            for (Path path : directoryStream) {
                fileNames.add(path.getFileName().toString());
            }
        } catch (IOException ex) {
        }
        return fileNames;
    }

}
