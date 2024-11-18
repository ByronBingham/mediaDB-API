package org.bmedia;

import org.apache.commons.io.FileUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.IOException;

/**
 * Singleton class for storing API settings
 */
public class ApiSettings {

    // Private variables

    private static ApiSettings instance;

    private String dbHostName;
    private String dbHostPort;
    private String dbName;
    private String fileShareBaseDir;
    private String schemaName;
    private String adminUsername;
    private String adminPassword;
    private String queryUsername;
    private String queryPassword;

    /**
     * Initialize the API settings. You must call this function before making any static calls to {@link ApiSettings}
     *
     * @param dbConfigPath Path to the API config file
     */
    public static void init(String dbConfigPath) {
        instance = new ApiSettings(dbConfigPath);
    }

    /**
     * Main constructor
     *
     * @param dbConfigPath Path to the API config file
     */
    private ApiSettings(String dbConfigPath) {
        JSONParser parser = new JSONParser();
        String dbConfigString = "";
        try {
            dbConfigString = FileUtils.readFileToString(new File(dbConfigPath));
        } catch (IOException e) {
            System.out.println("ERROR: \"" + dbConfigPath + "\" is not a valid file");
            return;
        }
        try {
            // TODO: this JSON needs validated
            Object obj = parser.parse(dbConfigString);
            JSONObject jsonObj = (JSONObject) obj;

            dbHostName = (String) jsonObj.get("database_host_name");
            dbHostPort = (String) jsonObj.get("database_host_port");
            dbName = (String) jsonObj.get("database_name");
            schemaName = (String) jsonObj.get("database_schema");
            adminUsername = (String) jsonObj.get("admin_username");
            adminPassword = (String) jsonObj.get("admin_password");
            queryUsername = (String) jsonObj.get("query_username");
            queryPassword = (String) jsonObj.get("query_password");
        } catch (ParseException e) {
            System.out.println("ERROR: Problem encountered parsing db config:\n" + e.getMessage());
            return;
        }
        fileShareBaseDir = System.getenv("MEDIA_SHARE");
        if (fileShareBaseDir == null) {
            System.out.println("ERROR: Did not find environment variable \"MEDIA_SHARE\". Please make sure it is defined");
            return;
        }
        if (!FileUtils.isDirectory(new File(fileShareBaseDir))) {
            System.out.println("ERROR: Environment variable \"MEDIA_SHARE\" is not a directory.");
            return;
        }
        if (fileShareBaseDir.endsWith("\\") || fileShareBaseDir.endsWith("/")) {
            fileShareBaseDir = fileShareBaseDir.substring(0, fileShareBaseDir.length() - 1);
        }
    }

    /**
     * Gets the DB name
     *
     * @return DB name
     */
    public static String getDbName() {
        return instance.dbName;
    }

    /**
     * Gets the DB schema name
     *
     * @return DB schema name
     */
    public static String getSchemaName() {
        return instance.schemaName;
    }

    /**
     * Gets the DB admin username
     * <p>
     * TODO: security
     *
     * @return DB admin username
     */
    public static String getAdminUsername() {
        return instance.adminUsername;
    }

    /**
     * Gets the DB admin password
     * <p>
     * TODO: security
     *
     * @return DB admin password
     */
    public static String getAdminPassword() {
        return instance.adminPassword;
    }

    /**
     * Get the DB username for read-only user
     * <p>
     * TODO: security
     *
     * @return Read-only username
     */
    public static String getQueryUsername() {
        return instance.queryUsername;
    }

    /**
     * Gets the DB read-only user password
     * <p>
     * TODO: security
     *
     * @return DB read-only user password
     */
    public static String getQueryPassword() {
        return instance.queryPassword;
    }

    /**
     * Get the DB host name
     *
     * @return DB host name
     */
    public static String getDbHostName() {
        return instance.dbHostName;
    }

    /**
     * Get the DB host port number
     *
     * @return DB host port number
     */
    public static String getDbHostPort() {
        return instance.dbHostPort;
    }

    /**
     * Get the path to the file share base directory
     *
     * @return path to the file share base directory
     */
    public static String getFileShareBaseDir() {
        return instance.fileShareBaseDir;
    }

    /**
     * Get full, absolute file path
     *
     * @param subPath Path to file relative to the file share base directory
     * @return Full path
     */
    public static String getFullFilePath(String subPath) {
        return instance.fileShareBaseDir + "/" + subPath;
    }

    /**
     * Get the path to a file relative to the file share base directory
     *
     * @param fullPath Full, absolute path of file (must point to file within the file share base directory)
     * @return path relative to file share base directory
     */
    public static String getPathRelativeToShare(String fullPath) {
        if (fullPath.startsWith(instance.fileShareBaseDir)) {
            return fullPath.substring(instance.fileShareBaseDir.length());
        } else {
            System.out.println("ERROR: Full path does not seem to be a subdirectory/file of the base share");
            return null;
        }
    }
}
