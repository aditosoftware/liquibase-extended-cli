package de.adito.convert.include;

import com.google.gson.*;
import lombok.NonNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Handles the includes in JSON files.
 *
 * @author r.hartinger, 27.06.2024
 */
public class JsonIncludeHandler extends AbstractIncludeHandler
{
  /**
   * The pattern for detecting {@code include} and {@code includeAll} in JSON files.
   */
  private static final Pattern JSON_PATTERN = Pattern.compile("\\s*(include|includeAll)\\s*\"\\s*:\\s*\\{");

  @Override
  protected @NonNull Pattern getPattern()
  {
    return JSON_PATTERN;
  }

  @Override
  public void modifyContent(@NonNull Map<Path, Path> pConvertedFiles, @NonNull Path pInput, @NonNull Path pIncludeFile, @NonNull Path pNewIncludeFile)
      throws IOException
  {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();

    JsonObject jsonObject;
    // Read JSON file
    try (BufferedReader reader = Files.newBufferedReader(pIncludeFile, StandardCharsets.UTF_8))
    {
      jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
    }

    // Get the "databaseChangeLog" array
    JsonArray databaseChangeLogArray = jsonObject.getAsJsonArray("databaseChangeLog");

    // Iterate through each element in the "databaseChangeLog" array
    for (JsonElement element : databaseChangeLogArray)
    {
      JsonObject changeLogEntry = element.getAsJsonObject();
      JsonObject includeObject = changeLogEntry.getAsJsonObject("include");

      if (includeObject != null)
      {
        // Replace the "file" value with a modified value
        String originalFileValue = includeObject.get("file").getAsString();
        boolean relativeToChangelogFile = includeObject.has("relativeToChangelogFile") &&
            includeObject.get("relativeToChangelogFile").getAsBoolean();

        includeObject.addProperty("file", this.changeFile(pConvertedFiles, pInput, pIncludeFile, originalFileValue, relativeToChangelogFile));
      }
    }

    // Write the modified JSON back to a file
    try (BufferedWriter writer = Files.newBufferedWriter(pNewIncludeFile, StandardCharsets.UTF_8))
    {
      gson.toJson(jsonObject, writer);
    }

  }
}
