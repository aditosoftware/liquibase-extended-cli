package de.adito.convert;

import lombok.*;
import org.apache.commons.io.FilenameUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Handles the includes of the changelog files.
 *
 * @author r.hartinger, 27.06.2024
 */
@AllArgsConstructor
public class IncludeHandler
{
  private static final Pattern YAML_PATTERN = java.util.regex.Pattern.compile("-\\s*(include|includeAll)\\s*:");
  private static final Pattern XML_PATTERN = Pattern.compile("<\\s*(include|includeAll)");
  private static final Pattern JSON_PATTERN = Pattern.compile("\\s*(include|includeAll)\\s*\"\\s*:\\s*\\{");


  private Path output;
  private Path input;
  private Map<Path, Path> filesHandled;

  @NonNull
  private Path includeFile;

  @NonNull
  private Path newIncludeFile;

  /**
   * Handles the includes
   *
   * @throws IOException Error reading or writing a changelog file
   */
  public void handleIncludes() throws IOException
  {
    String content = Files.readString(includeFile, StandardCharsets.UTF_8);


    String modifiedContent = modifyContent(content);

    Files.writeString(newIncludeFile, modifiedContent, StandardCharsets.UTF_8);
  }

  private String modifyContent(@NonNull String pContent)
  {
    String extension = FilenameUtils.getExtension(includeFile.getFileName().toString()).toLowerCase();

    switch (extension)
    {
      case "xml":
        return modifyXmlContent(pContent);
      case "json":
        return modifyJsonContent(pContent);
      case "yaml":
        return modifyYamlContent(pContent);
      default:
        // TODO exception werfen anstelle logging
        System.err.printf("Not supported file ending %s%n", extension);
        return pContent;
    }
  }

  private String changeFile(@NonNull String pOldFile)
  {
    for (Map.Entry<Path, Path> fileHandled : filesHandled.entrySet())
    {
      String relativePath = relativize(input, fileHandled.getKey());

      if (pOldFile.equals(relativePath))
      {
        return relativize(output, fileHandled.getValue());
      }
    }

    return pOldFile;
  }

  private @NonNull String relativize(Path basis, Path file)
  {
    return basis.relativize(file).toString().replace("\\", "/");
  }


  private String modifyXmlContent(String content)
  {
    Pattern pattern = Pattern.compile("file=\"([^\"]+)\"");
    Matcher matcher = pattern.matcher(content);
    StringBuilder sb = new StringBuilder();

    while (matcher.find())
    {
      String originalFile = matcher.group(1);
      String modifiedFile = changeFile(originalFile);
      matcher.appendReplacement(sb, "file=\"" + modifiedFile + "\"");
    }

    matcher.appendTail(sb);
    return sb.toString();
  }

  private String modifyJsonContent(String content)
  {
    Pattern pattern = Pattern.compile("\"file\"\\s*:\\s*\"([^\"]+)\"");
    Matcher matcher = pattern.matcher(content);
    StringBuilder sb = new StringBuilder();

    while (matcher.find())
    {
      String originalFile = matcher.group(1);
      String modifiedFile = changeFile(originalFile);
      matcher.appendReplacement(sb, "\"file\": \"" + modifiedFile + "\"");
    }

    matcher.appendTail(sb);
    return sb.toString();
  }

  private String modifyYamlContent(String content)
  {
    DumperOptions dumperOptions = new DumperOptions();
    dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

    Yaml yaml = new Yaml(new Constructor(DatabaseChangeLog.class, new LoaderOptions()),
                         new Representer(dumperOptions));


    DatabaseChangeLog databaseChangeLog = yaml.load(content);

    for (Map<String, Object> entry : databaseChangeLog.getDatabaseChangeLog())
    {
      if (entry.containsKey("include"))
      {
        Map<String, Object> includeMap = (Map<String, Object>) entry.get("include");

        Object file = includeMap.get("file");

        if (file != null)
        {
          includeMap.put("file", changeFile(file.toString()));
        }
        // Weitere Bedingungen hier hinzufügen
      }
    }

    return yaml.dump(databaseChangeLog);
  }


  private static class DatabaseChangeLog
  {
    private List<Map<String, Object>> databaseChangeLog;

    // Getter und Setter
    public List<Map<String, Object>> getDatabaseChangeLog()
    {
      return databaseChangeLog;
    }

    public void setDatabaseChangeLog(List<Map<String, Object>> databaseChangeLog)
    {
      this.databaseChangeLog = databaseChangeLog;
    }
  }


  ////////////////////

  /**
   * Checks for includes in a given file.
   *
   * @param pPathToConvert The file that should be checked for includes
   * @return {@code true}, if includes are in the file, {@code false} when no includes are there
   */
  public static boolean checkForIncludes(@NonNull Path pPathToConvert)
  {
    switch (FilenameUtils.getExtension(pPathToConvert.toString()).toLowerCase())
    {
      case "yaml":
        return checkForIncludes(pPathToConvert, YAML_PATTERN);
      case "xml":
        return checkForIncludes(pPathToConvert, XML_PATTERN);
      case "json":
        return checkForIncludes(pPathToConvert, JSON_PATTERN);
      default:
        return false;
    }
  }

  /**
   * Checks for includes in a given file.
   *
   * @param pPathToConvert the file that should be checked
   * @param pPattern       the pattern for the include element
   * @return {@code true}, if includes are in the file, {@code false} when no includes are there
   */
  public static boolean checkForIncludes(@NonNull Path pPathToConvert, @NonNull Pattern pPattern)
  {
    try
    {
      String content = Files.readString(pPathToConvert, StandardCharsets.UTF_8);

      Matcher matcher = pPattern.matcher(content);
      return matcher.find();
    }
    catch (IOException pE)
    {
      System.err.printf("error reading file for reading includes in file '%s': %s%n", pPathToConvert, pE.getMessage());
      pE.printStackTrace(System.err);
      return false;
    }
  }

}
