package org.bmedia;

import org.apache.commons.io.FilenameUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerErrorException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.List;

/**
 * API controller for image-related requests
 */
@RestController
@RequestMapping("/")
@CrossOrigin(origins = "*")
public class ImageController {

    /**
     * Gets a list of images that fit the search criteria. This request will get a specific "page" of results based on
     * the passed in page number and number of results per page specified. E.g. with a page number of 2 and results-per-
     * page of 50, this call will return images 100-149 (assuming there are at least 150 images)
     *
     * @param tbName         DB table to search
     * @param tags           Will only return images that include all of these tags
     * @param pageNum        "Page" number to get results for
     * @param resultsPerPage Number of images that should be in a "page" (number of images that will be returned)
     * @param includeThumb   If true, this call will return a base64 encoded thumbnail with each image result
     * @param thumbHeight    Height (pixels) of thumbnail image (width will be whatever is required to keep the aspect ratio
     *                       for the given height)
     * @param includeNsfw    If true, include NSFW results in the results
     * @return
     */
    @RequestMapping(value = "/search_images/by_tag/page", produces = "application/json")
    public ResponseEntity<String> search_images_by_tag_page(@RequestParam("table_name") String tbName,
                                                            @RequestParam("tags") String[] tags,
                                                            @RequestParam("page_num") int pageNum,
                                                            @RequestParam("results_per_page") int resultsPerPage,
                                                            @RequestParam("include_thumb") Optional<Boolean> includeThumb,
                                                            @RequestParam("thumb_height") Optional<Integer> thumbHeight,
                                                            @RequestParam("include_nsfw") Optional<Boolean> includeNsfw,
                                                            @RequestParam("min_width") Optional<Integer> minWidth,
                                                            @RequestParam("min_height") Optional<Integer> minHeight,
                                                            @RequestParam("aspect_ratio") Optional<Double> aspectRatio,
                                                            @RequestParam("asc_desc") Optional<Boolean> ascDesc,
                                                            @RequestParam("sort_by") Optional<String> sortBy) {

        boolean includeThumbVal = includeThumb.orElse(false);
        int thumbHeightVal = thumbHeight.orElse(400);
        boolean includeNsfwVal = includeNsfw.orElse(false);
        boolean ascending = ascDesc.orElse(false);
        String schemaName = ApiSettings.getSchemaName();
        String tbNameFull = schemaName + "." + tbName;

        String fullQuery = createSearchQuery(tbNameFull, tbName, schemaName, tags, pageNum, resultsPerPage, includeNsfwVal,
                minWidth, minHeight, aspectRatio, ascending, sortBy) + ";";

        String jsonOut = "[";
        try (Statement statement = Main.getDbconn().createStatement()) {
            ResultSet result = statement.executeQuery(fullQuery);

            ArrayList<String> jsonEntries = new ArrayList<>();
            while (result.next()) {
                long id = result.getLong("id");
                String md5 = result.getString("md5");
                String filename = result.getString("filename");
                int resolutionWidth = result.getInt("resolution_width");
                int resolutionHeight = result.getInt("resolution_height");
                int fileSizeBytes = result.getInt("file_size_bytes");

                String jsonEntry = "{" +
                        "\"id\": " + id + "," +
                        "\"md5\": \"" + md5 + "\"," +
                        "\"filename\": \"" + filename + "\"," +
                        "\"resolution_width\": " + resolutionWidth + "," +
                        "\"resolution_height\": " + resolutionHeight + "," +
                        "\"file_size_bytes\": " + fileSizeBytes;

                if (includeThumbVal) {
                    String imagePath = result.getString("file_path");
                    String b64Thumb = getThumbnailForImageB64(imagePath, thumbHeightVal, tbNameFull);
                    if (b64Thumb == null) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("FILE IO error");
                    }
                    jsonEntry += "\n,\"thumb_base64\": \"" + b64Thumb + "\"";
                }

                jsonEntry += "}";
                jsonEntries.add(jsonEntry);
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
     * Returns the number pages that a query would produce, given a list of tags, number of results per page, and NSFW/SFW
     *
     * @param tbName         DB table to search
     * @param tags           Will only return images that include all of these tags
     * @param resultsPerPage Number of images that should be in a "page" (number of images that will be returned)
     * @param includeNsfw    If true, include NSFW results in the results
     * @return
     */
    @RequestMapping(value = "/search_images/by_tag/page/count", produces = "application/json")
    public ResponseEntity<String> search_images_by_tag_page_count(@RequestParam("table_name") String tbName,
                                                                  @RequestParam("tags") String[] tags,
                                                                  @RequestParam("results_per_page") int resultsPerPage,
                                                                  @RequestParam("include_nsfw") Optional<Boolean> includeNsfw,
                                                                  @RequestParam("min_width") Optional<Integer> minWidth,
                                                                  @RequestParam("min_height") Optional<Integer> minHeight,
                                                                  @RequestParam("aspect_ratio") Optional<Double> aspectRatio,
                                                                  @RequestParam("asc_desc") Optional<Boolean> ascDesc,
                                                                  @RequestParam("sort_by") Optional<String> sortBy) {
        boolean includeNsfwVal = includeNsfw.orElse(false);
        String schemaName = ApiSettings.getSchemaName();
        String tbNameFull = schemaName + "." + tbName;
        String tagJoinTableName = schemaName + "." + tbName + "_tags_join";
        String tagTableName = schemaName + "." + "tags";
        boolean ascending = ascDesc.orElse(false);

        String query = "SELECT COUNT(*) AS itemCount FROM (" + createSearchQuery(tbNameFull, tbName, schemaName, tags,
                -1, resultsPerPage, includeNsfwVal, minWidth, minHeight, aspectRatio, ascending, sortBy) + ") AS sq;";

        String jsonOut = "";
        try {
            Statement statement = Main.getDbconn().createStatement();
            ResultSet result = statement.executeQuery(query);

            if (result.next()) {
                int totalResults = result.getInt("itemCount");
                int pages = (int) Math.ceil(totalResults / resultsPerPage);

                jsonOut += "{" +
                        "\"pages\": \"" + pages + "\"," +
                        "\"total_results\": " + totalResults;

                jsonOut += "}";
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("SQL error");
        }

        return ResponseEntity.status(HttpStatus.OK).body(jsonOut);
    }

    /**
     * Gets a base64 encoded thumbnail for an image in the DB
     *
     * @param tbName      DB table name
     * @param id          ID of image in the table
     * @param thumbHeight Height (pixels) the thumbnail should be (width will be whatever is required to keep the aspect ratio
     *                    *                    for the given height)
     * @return
     */
    @RequestMapping(value = "/images/get_thumbnail_b64", produces = "application/json")
    public ResponseEntity<String> get_image_thumbnail_b64(@RequestParam("table_name") String tbName,
                                                          @RequestParam("id") long id,
                                                          @RequestParam("thumb_height") Optional<Integer> thumbHeight) {
        int thumbHeightVal = thumbHeight.orElse(400);
        String schemaName = ApiSettings.getSchemaName();
        String tbNameFull = schemaName + "." + tbName;

        String query = "SELECT file_path FROM " + tbNameFull + " WHERE id='" + id + "';";

        String b64Thumb = null;
        try {
            Statement statement = Main.getDbconn().createStatement();
            ResultSet result = statement.executeQuery(query);

            if (!result.next()) {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("SQL error: no results returned");
            }

            String filePath = result.getString("file_path");
            if (filePath == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("IOError: this file is probably deleted from the filesystem");
            }
            b64Thumb = getThumbnailForImageB64(ApiSettings.getFullFilePath(filePath), thumbHeightVal, tbNameFull);
            if (b64Thumb == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("SQL error: no results returned from query");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("SQL error");
        }

        String jsonOut = "{" +
                "\"id\": \"" + id + "\"," +
                "\n\"thumb_base64\": \"" + b64Thumb + "\"\n}";

        return ResponseEntity.status(HttpStatus.OK).body(jsonOut);
    }

    /**
     * Gets a thumbnail for an image in the DB
     *
     * @param tbName      DB table name
     * @param id          ID of image in the table
     * @param thumbHeight Height (pixels) the thumbnail should be (width will be whatever is required to keep the aspect ratio
     *                    *                    for the given height)
     * @return
     */
    @RequestMapping(value = "/images/get_thumbnail", produces = MediaType.IMAGE_JPEG_VALUE)
    public @ResponseBody byte[] get_image_thumbnail(@RequestParam("table_name") String tbName,
                                                    @RequestParam("id") long id,
                                                    @RequestParam("thumb_height") Optional<Integer> thumbHeight) {

        int thumbHeightVal = thumbHeight.orElse(400);
        String schemaName = ApiSettings.getSchemaName();
        String tbNameFull = schemaName + "." + tbName;

        String query = "SELECT file_path FROM " + tbNameFull + " WHERE id='" + id + "';";

        byte[] thumbBytes = null;
        try {
            Statement statement = Main.getDbconn().createStatement();
            ResultSet result = statement.executeQuery(query);

            if (!result.next()) {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("SQL error: no results returned");
            }

            String filePath = result.getString("file_path");
            if (filePath == null) {
                throw new ServerErrorException("IOError: this file is probably deleted from the filesystem");
            }

            thumbBytes = getThumbnailForImage(ApiSettings.getFullFilePath(filePath), thumbHeightVal, tbNameFull);
            if (thumbBytes == null) {
                throw new ServerErrorException("Error: Could not create thumbnail for image");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            throw new ServerErrorException("SQL error");
        }


        return thumbBytes;
    }

    /**
     * Gets a full image from the DB as a base64 encoded string
     *
     * @param tbName DB table name
     * @param id     ID of image in the table
     * @return
     */
    @RequestMapping(value = "/images/get_image_full_b64", produces = "application/json")
    public ResponseEntity<String> get_image_full_b64(@RequestParam("table_name") String tbName,
                                                     @RequestParam("id") long id) {
        String schemaName = ApiSettings.getSchemaName();
        String tbNameFull = schemaName + "." + tbName;

        String query = "SELECT file_path FROM " + tbNameFull + " WHERE id=" + id + ";";

        String b64Image = null;
        try {
            Statement statement = Main.getDbconn().createStatement();
            ResultSet result = statement.executeQuery(query);

            if (!result.next()) {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("SQL error: no results returned");
            }

            String filePath = result.getString("file_path");
            if (filePath == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("IOError: this file is probably deleted from the filesystem");
            }
            b64Image = getFullImage_b64(ApiSettings.getFullFilePath(filePath), tbNameFull);
            if (b64Image == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("SQL error: no results returned from query");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("SQL error");
        }

        String jsonOut = "{" +
                "\"id\": \"" + id + "\"," +
                "\n\"image_base64\": \"" + b64Image + "\"\n}";

        return ResponseEntity.status(HttpStatus.OK).body(jsonOut);
    }

    /**
     * Gets a full image from the DB as a base64 encoded string
     *
     * @param tbName DB table name
     * @param id     ID of image in the table
     * @return
     */
    @RequestMapping(value = "/images/get_image_full", produces = MediaType.IMAGE_JPEG_VALUE)
    public @ResponseBody byte[] get_image_full(@RequestParam("table_name") String tbName,
                                               @RequestParam("id") long id) {
        String schemaName = ApiSettings.getSchemaName();
        String tbNameFull = schemaName + "." + tbName;

        String query = "SELECT file_path FROM " + tbNameFull + " WHERE id=" + id + ";";

        byte[] imageBytes = null;
        try {
            Statement statement = Main.getDbconn().createStatement();
            ResultSet result = statement.executeQuery(query);

            if (!result.next()) {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("SQL error: no results returned");
            }

            String filePath = result.getString("file_path");
            if (filePath == null) {
                throw new ServerErrorException("IOError: this file is probably deleted from the filesystem");
            }
            imageBytes = getFullImage(ApiSettings.getFullFilePath(filePath), tbNameFull);
            if (imageBytes == null) {
                throw new ServerErrorException("SQL error: no results returned from query");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            throw new ServerErrorException("SQL error");
        }

        return imageBytes;
    }

    /**
     * Get a list of the tags for an image in the DB
     *
     * @param tbName DB table name
     * @param id     ID of image in the table
     * @return
     */
    @RequestMapping(value = "/images/get_tags", produces = "application/json")
    public ResponseEntity<String> get_image_tags(@RequestParam("table_name") String tbName,
                                                 @RequestParam("id") long id) {
        String schemaName = ApiSettings.getSchemaName();
        String tagJoinTableName = schemaName + "." + tbName + "_tags_join";
        String tagTableName = schemaName + "." + "tags";

        String query = "SELECT at.tag_name, t.nsfw FROM " + tagTableName + " t " +
                "JOIN " + tagJoinTableName + " at ON t.tag_name = at.tag_name " +
                "WHERE at.id=" + id + ";";

        String jsonOut = "[";
        try {
            Statement statement = Main.getDbconn().createStatement();
            ResultSet result = statement.executeQuery(query);

            ArrayList<String> jsonEntries = new ArrayList<>();
            while (result.next()) {
                String tag_name = result.getString("tag_name");
                Boolean nsfw = result.getBoolean("nsfw");

                String jsonEntry = "{" +
                        "\"tag_name\": \"" + tag_name + "\"," +
                        "\"nsfw\": " + nsfw + "}";
                jsonEntries.add(jsonEntry);
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
     * Adds a tag to an image in the DB
     *
     * @param tbName        DB table name
     * @param id            ID of image in the table
     * @param tagName       Name of tag to add (does not have to already be in the DB)
     * @param nsfw          If true, will set the tag to NSFW (will overwrite this setting for existing tags as well)
     * @param overwriteNsfw Overwrite the NSFW setting for an existing tag
     * @return
     */
    @RequestMapping(value = "/images/add_tag", produces = "application/json")
    public ResponseEntity<String> add_tag_to_image(@RequestParam("table_name") String tbName,
                                                   @RequestParam("id") long id,
                                                   @RequestParam("tag_name") String tagName,
                                                   @RequestParam("nsfw") Optional<Boolean> nsfw,
                                                   @RequestParam("overwrite_nsfw") Optional<Boolean> overwriteNsfw) {
        if (tagName.equals("")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Cannot have empty tag name in request");
        }

        boolean nsfwVal = nsfw.orElse(false);
        boolean overwriteNsfwVal = overwriteNsfw.orElse(false);
        tagName = tagName.replace("'", "''");
        String schemaName = ApiSettings.getSchemaName();
        String tagJoinTableName = tbName + "_tags_join";

        String query1 = "INSERT INTO " + schemaName + ".tags (tag_name, nsfw) VALUES (?, ?) ON CONFLICT (tag_name) DO";
        if (overwriteNsfwVal) {
            query1 += " UPDATE SET nsfw = " +
                    "EXCLUDED.nsfw;";
        } else {
            query1 += " NOTHING;";
        }

        String query2 = "INSERT INTO " + schemaName + "." + tagJoinTableName + " (id, tag_name) VALUES (?, ?)" +
                " ON CONFLICT DO NOTHING;";

        try {
            // add tag if not already in tag table
            PreparedStatement statement1 = Main.getDbconn().prepareStatement(query1);
            statement1.setString(1, tagName);
            statement1.setBoolean(2, nsfwVal);
            statement1.executeUpdate();

            // add entry into join table
            PreparedStatement statement2 = Main.getDbconn().prepareStatement(query2);
            statement2.setLong(1, id);
            statement2.setString(2, tagName);
            statement2.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("SQL error");
        }

        return ResponseEntity.status(HttpStatus.OK).body("Successfully added tag");
    }

    /**
     * Adds a tag to an image in the DB
     *
     * @param tbName        DB table name
     * @param ids           ID's of images in the table
     * @param tagNames      Names of tags to add (does not have to already be in the DB)
     * @param overwriteNsfw Overwrite the NSFW setting for an existing tag
     * @return
     */
    @RequestMapping(value = "/images/add_tags", produces = "application/json")
    public ResponseEntity<String> add_tag_to_image(@RequestParam("table_name") String tbName,
                                                   @RequestParam("id") List<Long> ids,
                                                   @RequestParam("tag_names") List<String> tagNames,
                                                   @RequestParam("overwrite_nsfw") Optional<Boolean> overwriteNsfw) {
        if (tagNames.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Cannot have empty tag list in request");
        }
        boolean overwriteNsfwVal = overwriteNsfw.orElse(false);

        for (String tagName : tagNames) {
            if (tagName.contains("'")) {
                String newTag = tagName.replace("'", "''");
                tagNames.remove(tagName);
                tagNames.add(newTag);
            }
        }
        String schemaName = ApiSettings.getSchemaName();
        String tagJoinTableName = tbName + "_tags_join";

        String query1 = "INSERT INTO " + schemaName + ".tags (tag_name, nsfw) VALUES (?, ?) ON CONFLICT (tag_name) DO";
        if (overwriteNsfwVal) {
            query1 += " UPDATE SET nsfw = " +
                    "EXCLUDED.nsfw;";
        } else {
            query1 += " NOTHING;";
        }

        String query2 = "INSERT INTO " + schemaName + "." + tagJoinTableName + " (id, tag_name) VALUES (?, ?)" +
                " ON CONFLICT DO NOTHING;";

        try (PreparedStatement statement2 = Main.getDbconn().prepareStatement(query2);
             PreparedStatement statement1 = Main.getDbconn().prepareStatement(query1)) {

            for (String tagName : tagNames) {
                for (Long id : ids) {
                    // add tag if not already in tag table
                    statement1.setString(1, tagName);
                    statement1.setBoolean(2, false);
                    statement1.addBatch();

                    // add entry into join table
                    statement2.setLong(1, id);
                    statement2.setString(2, tagName);
                    statement2.addBatch();
                }
            }

            // Run batch query
            statement1.executeBatch();
            statement2.executeBatch();

        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("SQL error");
        }

        return ResponseEntity.status(HttpStatus.OK).body("Successfully added tag");
    }

    /**
     * Deletes a tag for an image in the DB
     *
     * @param tbName  DB table name
     * @param id      ID of image in the table
     * @param tagName Name of tag to add (does not have to already be in the DB)
     * @return
     */
    @RequestMapping(value = "/images/delete_tag", produces = "application/json")
    public ResponseEntity<String> delte_tag_for_image(@RequestParam("table_name") String tbName,
                                                      @RequestParam("id") String id,
                                                      @RequestParam("tag_name") String tagName) {
        if (tagName.equals("")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Cannot have empty tag name in request");
        }

        tagName = tagName.replace("'", "''");
        String schemaName = ApiSettings.getSchemaName();
        String tagJoinTableName = tbName + "_tags_join";
        tagName = tagName.replace("'", "''");

        String query = "DELETE FROM " + schemaName + "." + tagJoinTableName + " WHERE id = '" + id + "'" +
                " AND tag_name = '" + tagName + "';";

        try {
            Statement statement = Main.getDbconn().createStatement();
            statement.executeUpdate(query);
        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("SQL error");
        }

        return ResponseEntity.status(HttpStatus.OK).body("Successfully added tag");
    }

    /**
     * Create a base64 encoded thumbnail for an image
     *
     * @param imagePath     Full path to an image
     * @param thumbHeight   Height (pixels) of thumbnail image (width will be whatever is required to keep the aspect ratio
     *                      for the given height)
     * @param fullTableName Table name ([schema_name].[table_name]) of image. This is used in case the image's path is
     *                      broken and needs removed form the DB
     * @return
     */
    private String getThumbnailForImageB64(String imagePath, int thumbHeight, String fullTableName) {

        ByteArrayOutputStream boas = new ByteArrayOutputStream();
        String imgExt = FilenameUtils.getExtension(imagePath);
        try {
            BufferedImage img = ImageIO.read(new File(imagePath));
            BufferedImage imgSmall = null;

            double w = img.getWidth();
            double h = img.getHeight();
            int targetWidth = (int) (w * (thumbHeight / h));
            Image resultingImage = img.getScaledInstance(targetWidth, thumbHeight, Image.SCALE_AREA_AVERAGING | Image.SCALE_FAST);
            BufferedImage outputImage = new BufferedImage(targetWidth, thumbHeight, BufferedImage.TYPE_INT_RGB);
            outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
            imgSmall = outputImage;

            // convert image to jpg compatible format if necessary
            if (imgExt.equals("png")) {
                BufferedImage newBufferedImage = new BufferedImage(imgSmall.getWidth(), imgSmall.getHeight(),
                        BufferedImage.TYPE_INT_RGB);
                newBufferedImage.createGraphics().drawImage(imgSmall, 0, 0, Color.WHITE, null);
                imgSmall = newBufferedImage;
            }
            if (!ImageIO.write(imgSmall, "jpg", boas)) {
                System.out.println("ERROR: Failed to write image to buffer for b64 encoding.");
                return null;
            }
        } catch (IOException e) {
            System.out.println("ERROR: IO error while trying to encode image" + imagePath + ". \n" + e.getMessage());
            if (!Files.exists(Path.of(imagePath))) {
                // keep DB entry but set path to null
                String relPath = ApiSettings.getPathRelativeToShare(imagePath);
                try {
                    Main.removeBrokenPathInDB(relPath, fullTableName);
                } catch (SQLException sqlException) {
                    System.out.println("WARNING: Could not delete path from DB: \"" + relPath + "\"");
                }
            }
            return null;
        }
        return Base64.getEncoder().encodeToString(boas.toByteArray());

    }

    /**
     * Create a thumbnail for an image
     *
     * @param imagePath     Full path to an image
     * @param thumbHeight   Height (pixels) of thumbnail image (width will be whatever is required to keep the aspect ratio
     *                      for the given height)
     * @param fullTableName Table name ([schema_name].[table_name]) of image. This is used in case the image's path is
     *                      broken and needs removed form the DB
     * @return Byte array of image
     */
    private byte[] getThumbnailForImage(String imagePath, int thumbHeight, String fullTableName) {

        ByteArrayOutputStream boas = new ByteArrayOutputStream();
        String imgExt = FilenameUtils.getExtension(imagePath);
        try {
            BufferedImage img = ImageIO.read(new File(imagePath));
            BufferedImage imgSmall = null;

            double w = img.getWidth();
            double h = img.getHeight();
            int targetWidth = (int) (w * (thumbHeight / h));
            Image resultingImage = img.getScaledInstance(targetWidth, thumbHeight, Image.SCALE_AREA_AVERAGING | Image.SCALE_FAST);
            BufferedImage outputImage = new BufferedImage(targetWidth, thumbHeight, BufferedImage.TYPE_INT_RGB);
            outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
            imgSmall = outputImage;

            // convert image to jpg compatible format if necessary
            if (imgExt.equals("png")) {
                BufferedImage newBufferedImage = new BufferedImage(imgSmall.getWidth(), imgSmall.getHeight(),
                        BufferedImage.TYPE_INT_RGB);
                newBufferedImage.createGraphics().drawImage(imgSmall, 0, 0, Color.WHITE, null);
                imgSmall = newBufferedImage;
            }
            if (!ImageIO.write(imgSmall, "jpg", boas)) {
                System.out.println("ERROR: Failed to write image to buffer for b64 encoding.");
                return null;
            }
        } catch (IOException e) {
            System.out.println("ERROR: IO error while trying to encode image" + imagePath + ". \n" + e.getMessage());
            if (!Files.exists(Path.of(imagePath))) {
                // keep DB entry but set path to null
                String relPath = ApiSettings.getPathRelativeToShare(imagePath);
                try {
                    Main.removeBrokenPathInDB(relPath, fullTableName);
                } catch (SQLException sqlException) {
                    System.out.println("WARNING: Could not delete path from DB: \"" + relPath + "\"");
                }
            }
            return null;
        }
        return boas.toByteArray();
    }

    /**
     * Gets a base64 encoded representation of an image
     *
     * @param imagePath     Full path to an image
     * @param fullTableName Table name ([schema_name].[table_name]) of image. This is used in case the image's path is
     *                      broken and needs removed form the DB
     * @return
     */
    private String getFullImage_b64(String imagePath, String fullTableName) {
        ByteArrayOutputStream boas = new ByteArrayOutputStream();
        try {
            BufferedImage img = ImageIO.read(new File(imagePath));
            String extension = FilenameUtils.getExtension(imagePath);
            if (!ImageIO.write(img, extension, boas)) {
                System.out.println("ERROR: Failed to write image to buffer for b64 encoding.");
                return null;
            }
        } catch (IOException e) {
            System.out.println("ERROR: IO error while trying to encode image " + imagePath + ". \n" + e.getMessage());
            if (!Files.exists(Path.of(imagePath))) {
                // keep DB entry but set path to null
                String relPath = ApiSettings.getPathRelativeToShare(imagePath);
                try {
                    Main.removeBrokenPathInDB(relPath, fullTableName);
                } catch (SQLException sqlException) {
                    System.out.println("WARNING: Could not delete path from DB: \"" + relPath + "\"");
                }
            }
            return null;
        }
        return Base64.getEncoder().encodeToString(boas.toByteArray());
    }

    /**
     * Gets a byte array representation of an image
     *
     * @param imagePath     Full path to an image
     * @param fullTableName Table name ([schema_name].[table_name]) of image. This is used in case the image's path is
     *                      broken and needs removed form the DB
     * @return
     */
    private byte[] getFullImage(String imagePath, String fullTableName) {
        ByteArrayOutputStream boas = new ByteArrayOutputStream();
        try {
            BufferedImage img = ImageIO.read(new File(imagePath));
            String extension = FilenameUtils.getExtension(imagePath);
            if (!ImageIO.write(img, extension, boas)) {
                System.out.println("ERROR: Failed to write image to buffer for b64 encoding.");
                return null;
            }
        } catch (IOException e) {
            System.out.println("ERROR: IO error while trying to encode image " + imagePath + ". \n" + e.getMessage());
            if (!Files.exists(Path.of(imagePath))) {
                // keep DB entry but set path to null
                String relPath = ApiSettings.getPathRelativeToShare(imagePath);
                try {
                    Main.removeBrokenPathInDB(relPath, fullTableName);
                } catch (SQLException sqlException) {
                    System.out.println("WARNING: Could not delete path from DB: \"" + relPath + "\"");
                }
            }
            return null;
        }
        return boas.toByteArray();
    }

    /**
     * Creates the SQL query for the search request
     * <p>
     * NOTE: does not include a terminating semicolon so that this query can be used as a sub-query
     *
     * @param tbNameFull     Full table name
     * @param tbName         Table name
     * @param schemaName     Schema name
     * @param inputTags      List of tags
     * @param pageNum        Page number ( use -1 if you want all results)
     * @param resultsPerPage Number of results per page
     * @param includeNsfwVal Whether to include NSFW results
     * @param minWidth       Min width
     * @param minHeight      Min height
     * @param aspectRatio    Aspect ratio
     * @param ascending      Asc/Desc
     * @param sortBy         Sort by string
     * @return SQL query to search for images in DB (no trailing ';')
     */
    private String createSearchQuery(String tbNameFull, String tbName, String schemaName, String[] inputTags, int pageNum, int resultsPerPage,
                                     boolean includeNsfwVal, Optional<Integer> minWidth, Optional<Integer> minHeight,
                                     Optional<Double> aspectRatio, boolean ascending, Optional<String> sortBy) {

        String tagJoinTableName = schemaName + "." + tbName + "_tags_join";
        String tagTableName = schemaName + "." + "tags";
        for (int i = 0; i < inputTags.length; i++) {
            inputTags[i] = inputTags[i].replace("'", "''");
        }

        // Get excluded tags
        ArrayList<String> excludeTags = new ArrayList<>();
        for (int i = 0; i < inputTags.length; i++) {
            if (inputTags[i].startsWith("-")) {
                excludeTags.add(inputTags[i].replace("-", ""));
            }
        }
        // Remove excluded tags from input tags
        ArrayList<String> newTags = new ArrayList<>();
        for (String tag : inputTags) {
            if (!tag.startsWith("-")) {
                newTags.add(tag);
            }
        }
        String[] tags = newTags.toArray(String[]::new);

        String excludeString = "'" + String.join("','", excludeTags.toArray(String[]::new)) + "'";
        boolean excludingTags = excludeTags.size() > 0;

        // Main query
        String fullQuery = createBaseQuery(tbNameFull, tagJoinTableName, tagTableName, tags, includeNsfwVal, excludingTags);

        // Filter query if applicable
        boolean hExists = minHeight.isPresent();
        boolean wExists = minWidth.isPresent();
        boolean arExists = aspectRatio.isPresent();
        boolean doSubQuery = hExists || wExists || arExists;
        String extraFilteringString = "";

        // Need to create this subquery if either filtering or excluding tags
        if(doSubQuery || excludingTags){
            String filterSelectPrefix = (excludingTags) ? "s1.id" : "*";
            fullQuery = "SELECT " + filterSelectPrefix + " FROM ( " + fullQuery + ") AS s1 ";
        }

        // Back to filtering stuff
        if (doSubQuery) {
            extraFilteringString = "WHERE ";


            // Build filtering "WHERE" clause
            if (hExists) {
                extraFilteringString += "resolution_height >= " + minHeight.get() + " ";
                if (wExists) {
                    extraFilteringString += "AND ";
                }
            }
            if (wExists) {
                extraFilteringString += "resolution_width >= " + minWidth.get() + " ";
                if (arExists) {
                    extraFilteringString += "AND ";
                }
            }
            if (arExists) {
                String sign = "=";
                if (aspectRatio.get() > 1.0) {
                    sign = ">=";
                } else if (aspectRatio.get() < 1.0) {
                    sign = "<=";
                }
                extraFilteringString += "CAST(resolution_width AS FLOAT) / CAST(resolution_height AS FLOAT) " + sign + " " + aspectRatio.get() + " ";
            }
        }

        // Tertiary query if applicable
        if (excludingTags) {
            fullQuery = "SELECT * FROM (" + fullQuery;
            fullQuery += "JOIN " + tagJoinTableName + " xat ON (s1.id) = (xat.id)" +
                    "GROUP BY (s1.id)" +
                    "HAVING MAX(CASE WHEN xat.tag_name IN ("+ excludeString + ") THEN 1 ELSE 0 END) = 0";
            fullQuery += ") AS s2 JOIN " + tbNameFull + " j ON s2.id = j.id ";
        }

        // Add extra filtering to end of query
        fullQuery += extraFilteringString;

        // Add Sorting to end of query
        String sortBySql = "ORDER BY ";
        String ascDescString = (ascending) ? "ASC " : "DESC ";
        String sortByPrefix = (excludingTags)? "j." : "";
        switch ((sortBy.isPresent()) ? sortBy.get().toLowerCase() : "") {
            case "height":
                sortBySql += sortByPrefix + "resolution_height " + ascDescString;
                break;
            case "width":
                sortBySql += sortByPrefix + "resolution_width " + ascDescString;
                break;
            case "file_size":
                sortBySql += sortByPrefix + "file_size_bytes " + ascDescString;
                break;
            case "aspect_ratio":
                sortBySql += sortByPrefix + "resolution_width * " + sortByPrefix + "resolution_height " + ascDescString;
                break;
        }
        sortBySql += ((sortBy.isPresent()) ? "," : "") + " " + sortByPrefix + "id " + ascDescString;

        fullQuery += sortBySql;

        // End of main query
        if (pageNum >= 0) {
            fullQuery += " OFFSET " + pageNum * resultsPerPage + " LIMIT " + resultsPerPage;
        }

        return fullQuery;
    }

    /**
     * Creates the base query that would run with minimum parameters, but makes changes that are necessary if there are
     * more parameters specified
     *
     * @param tbNameFull Full tablename ( [schema].[table] )
     * @param tagJoinTableName Full table name for the tag join table
     * @param tagTableName Name only of tag table
     * @param tags list of tags to include in search
     * @param includeNsfwVal True/False include NSFW
     * @param excludingTags This should be true if the subsequent queries will account for excluding tags
     * @return Base query as a String
     */
    private String createBaseQuery(String tbNameFull, String tagJoinTableName, String tagTableName, String[] tags, boolean includeNsfwVal, boolean excludingTags) {
        int numTags = tags.length;
        String tagString = "'" + String.join("','", tags) + "'";
        String nsfwString1 = "";
        String nsfwString2 = "";
        String nsfwJoinString = "";
        if (!includeNsfwVal) {
            nsfwJoinString += "JOIN " + tagTableName + " t ON at.tag_name = t.tag_name ";
            nsfwString1 += "t.nsfw = TRUE ";
            nsfwString2 += "MAX(CASE t.nsfw WHEN TRUE THEN 1 ELSE 0 END) = 0 ";
        }
        String includeNonExclude = "";

        if (!excludingTags) {
            includeNonExclude = ",a.md5,a.filename,a.resolution_width,a.resolution_height,a.file_size_bytes";
        }

        String query = "SELECT a.id" + includeNonExclude +
                " FROM " + tbNameFull + " a JOIN " + tagJoinTableName + " at ON (a.id) = (at.id) " +
                nsfwJoinString;

        // Query with no specified tags
        if (numTags == 0) {
            if (!includeNsfwVal) {
                nsfwString2 = "HAVING " + nsfwString2;
            }
            query += " WHERE NOT a.file_path IS NULL " +
                    "GROUP BY (a.id)" + nsfwString2;
        } else {
            if (!includeNsfwVal) {
                nsfwString1 = "OR  " + nsfwString1;
                nsfwString2 = " AND " + nsfwString2;
            }
            query += "WHERE NOT a.file_path IS NULL AND at.tag_name IN (" + tagString + ") " + nsfwString1 +
                    "GROUP BY (a.id) HAVING COUNT(at.tag_name) >= " + numTags + " " + nsfwString2;
        }

        return query;
    }
}
