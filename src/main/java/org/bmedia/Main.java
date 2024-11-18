package org.bmedia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Main class for the API. Starts SpringBoot and handles the DB connection
 */
@SpringBootApplication
@RestController
@CrossOrigin(origins = "*")
public class Main {

    private static Connection dbconn = null;

    /**
     * Main function that runs the API and starts the SpringBoot app
     *
     * @param args Should include 1 argument that points to the config file for the API
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("ERROR: Invalid number of arguments.\nPlease pass in the path to the config file");
        } else if (!Files.exists(Path.of(args[0]))) {
            System.out.println("ERROR: Invalid argument. The provided file does not exist.\nPlease pass in the path to the config file");
        }

        ApiSettings.init(args[0]);

        try {
            String url = "jdbc:postgresql://" + ApiSettings.getDbHostName() + ":" +
                    ApiSettings.getDbHostPort() + "/" + ApiSettings.getDbName();
            System.out.println("INFO: Connnecting to DB: \n" + url);
            dbconn = DriverManager.getConnection(url, ApiSettings.getAdminUsername(), ApiSettings.getAdminPassword());
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("ERROR: Unable to establish connection to database. Exiting...");
            return;
        }
        args = (new ArrayList<>((Arrays.asList(args))).subList(1, args.length)).toArray(new String[0]);
        SpringApplication.run(Main.class, args);
    }

    /**
     * Test API call
     *
     * @return
     */
    @RequestMapping(value = "/", produces = "text/plain")
    public ResponseEntity<String> defaultResponse() {
        return ResponseEntity.status(HttpStatus.OK).body("This is the BMedia API");
    }

    /**
     * Gets the connection to the DB. Will create a new connection if needed
     *
     * @return {@link Connection} to the DB
     * @throws SQLException
     */
    public synchronized static Connection getDbconn() throws SQLException {
        try {
            Statement statement = dbconn.createStatement();
            ResultSet result = statement.executeQuery("SELECT 1;");
        } catch (SQLException e) {
            dbconn = DriverManager.getConnection("jdbc:postgresql://" + ApiSettings.getDbHostName() + ":" +
                            ApiSettings.getDbHostPort() + "/" + ApiSettings.getDbName(),
                    ApiSettings.getAdminUsername(), ApiSettings.getAdminPassword());
            System.out.println("INFO: Error encountered while checking DB connection:\n" + e.getMessage());
            System.out.println("INFO: Connection with database was closed. Re-connecting to database");
        }

        return dbconn;
    }

    /**
     * Sets an item's path to NULL. This allows the DB to keep the tag data in case the image is re-added (or just moved).
     * This saves lots of processing time in the case of moved/re-added images. NULL paths are ignored in API queries
     *
     * @param relativeDbPath Path of the image to "remove" relative to the file share base directory
     * @param fullTableName  DB table image belongs to
     * @throws SQLException DB Exception
     */
    public static void removeBrokenPathInDB(String relativeDbPath, String fullTableName) throws SQLException {
        String baseQuery = "UPDATE " + fullTableName + " SET file_path=NULL WHERE file_path=?;";

        if (relativeDbPath.startsWith("/") || relativeDbPath.startsWith("\\")) {
            relativeDbPath = relativeDbPath.substring(1);
        }

        PreparedStatement statement = Main.getDbconn().prepareStatement(baseQuery);
        statement.setString(1, relativeDbPath);
        statement.executeUpdate();
        System.out.println("INFO: Nulled broken path in DB: \"" + relativeDbPath + "\"");
    }
}