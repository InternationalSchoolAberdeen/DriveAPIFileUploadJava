package tech.finnlestrange;

/*
* Application to upload a specified file to a location in a Google Workspace Drive Folder
*
* Build:
* - gradle clean
* - gradle build jar
*
* Run:
* - java -jar DriveUpload.jar <file location> <shared_folder_id (IN QUOTES)>
*
* */

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.FileContent;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.*;

public class FileUpload {

    /**
    * Uploads a file to a selected folder in Google Drive
    *
    * @return File metadata
    * @throws IOException if credentials.json file is not found
    * */

    private static final JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
    private static final String tokenDirectory = "tokens";
    private static final List<String> scopes = Collections.singletonList(DriveScopes.DRIVE_FILE);

    /**
     * Creates and authorized oAuth2 Google Credential Object -> requires credential.json file with cloud api enabled
     *
     * @return Credential object used to authenticate with GDrive API to upload file
     * @throws IOException if credential.json file is not found in the current working directory .
     * */
    private static Credential getCredentials(final NetHttpTransport httpTransport, String credentialPath) throws IOException {

        InputStream in = FileUpload.class.getResourceAsStream(credentialPath);
        if (in == null) {
            throw new FileNotFoundException("Resource could not be found: " + credentialPath);
        }

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(jsonFactory, new InputStreamReader(in));

        // Build & Trigger Authentication Request
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, jsonFactory, clientSecrets, scopes)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(tokenDirectory)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();

        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        if (credential.getExpiresInSeconds() <= 90000) { // if there is less than a day left until exp
            credential.refreshToken();
        }
        return credential;
    }

    /**
     * Uploads a file given a path to a selected folder (given ID) in Google Drive
     *
     * @return id of uploaded file
     * @throws IOException if the file to upload is not found
     * @throws GeneralSecurityException if credential is not valid or does not have the required permissions
    * */
    public static String fileUpload(String folderId, String csvPath) throws IOException, GeneralSecurityException {

        // Request oAuth Credentials
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        final Credential credential = getCredentials(httpTransport, "/credential.json");

        // Build authorized API client service
        Drive service = new Drive.Builder(
                httpTransport, jsonFactory, credential)
                .setApplicationName("Drive File Upload")
                .build();

        // File Object
        File fileToUpload = new File().setName("export.csv");
        fileToUpload.setParents(Collections.singletonList(folderId)); // ID of the folder for the file to be placed

        java.io.File filePath = new java.io.File(csvPath); // path to file data
        FileContent fileContent = new FileContent("text/csv", filePath); // -> content of the file to upload

        // Uploading the File
        try {
            File file = service.files().create(fileToUpload, fileContent)
                    .setFields("id")
                    .setSupportsAllDrives(true)
                    .execute();
            System.out.println("File ID: " + file.getId() + ", is now located in folder w/ id: " + folderId);
            return file.getId();
        } catch (GoogleJsonResponseException e) {
            System.err.println("Unable to upload file: " + e.getDetails());
            throw e;
        }
    }

    public static void main(String[] args) throws GeneralSecurityException, IOException {
        if (args.length < 1) {
            System.out.println("Usage: java -jar DriveUpload.jar <csv location> <shared_folder_id (IN QUOTES)>");
        }
        String csvPath = args[0]; // get location of csv from command line argument -> full path on Windows i.e. C:/

        String folderId = args[1];
        fileUpload(folderId, csvPath);
    }
}