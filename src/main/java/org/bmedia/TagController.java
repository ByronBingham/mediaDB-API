package org.bmedia;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Optional;

/**
 * API controller for tag-related requests
 */
@RestController
@RequestMapping("/")
@CrossOrigin(origins = "*")
public class TagController {

    /**
     * Get a list of all tags in a DB
     *
     * @return
     */
    @RequestMapping(value = "/tags/get_all_tags", produces = "application/json")
    public ResponseEntity<String> getAllTags() {

        String query = "SELECT * FROM " + ApiSettings.getSchemaName() + ".tags ORDER BY tag_name;";

        String jsonOut = "[";
        try {
            Statement statement = Main.getDbconn().createStatement();
            ResultSet result = statement.executeQuery(query);

            ArrayList<String> jsonEntries = new ArrayList<>();
            while (result.next()) {
                String tagName = result.getString("tag_name");
                boolean nsfw = result.getBoolean("nsfw");

                String jsonString = "{\"tag_name\":\"" + tagName + "\",\"nsfw\":" + ((nsfw) ? "true" : "false") + "}";

                jsonEntries.add(jsonString);
            }

            jsonOut += String.join(",", jsonEntries);
            jsonOut += "]";
        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("SQL error");
        }

        return ResponseEntity.status(HttpStatus.OK).body(jsonOut);
    }

    /**
     * Add a tag to the DB
     *
     * @param tagName Tag to add
     * @param nsfw    Whether or not the tag should be marked NSFW
     * @return
     */
    @RequestMapping(value = "/tags/add_tag", produces = "application/json")
    public ResponseEntity<String> addTag(@RequestParam("tag_name") String tagName,
                                         @RequestParam("nsfw") Optional<Boolean> nsfw) {
        if (tagName.equals("")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Cannot have empty tag name in request");
        }

        boolean nsfwVal = nsfw.orElse(false);
        tagName = tagName.replace("'", "''");

        String query = "INSERT INTO " + ApiSettings.getSchemaName() + ".tags (tag_name, nsfw) VALUES (?, ?) ON CONFLICT (tag_name) DO UPDATE SET nsfw = " +
                "EXCLUDED.nsfw;";

        try {
            PreparedStatement statement = Main.getDbconn().prepareStatement(query);
            statement.setString(1, tagName);
            statement.setBoolean(2, nsfwVal);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("SQL error");
        }

        return ResponseEntity.status(HttpStatus.OK).body("Successfully added tag");
    }

    /**
     * Update a tag's nsfw value
     *
     * @param tagName Tag to update
     * @param nsfw    Update the tag's NSFW status to this input
     * @return
     */
    @RequestMapping(value = "/tags/update_tag", produces = "application/json")
    public ResponseEntity<String> updateTag(@RequestParam("tag_name") String tagName,
                                            @RequestParam("nsfw") Boolean nsfw) {
        tagName = tagName.replace("'", "''");

        String query = "UPDATE " + ApiSettings.getSchemaName() + ".tags SET nsfw = " + ((nsfw) ? "TRUE" : "FALSE")
                + " WHERE tag_name = '" + tagName + "';";

        try {
            Statement statement = Main.getDbconn().createStatement();
            statement.executeUpdate(query);
        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("SQL error");
        }

        return ResponseEntity.status(HttpStatus.OK).body("Successfully added tag");
    }

}
