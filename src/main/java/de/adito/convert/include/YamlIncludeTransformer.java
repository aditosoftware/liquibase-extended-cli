package de.adito.convert.include;

import lombok.*;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Transforms the include in YAML files.
 *
 * @author r.hartinger, 27.06.2024
 */
class YamlIncludeTransformer extends AbstractIncludeTransformer
{
  /**
   * The pattern for detecting {@code include} and {@code includeAll} in YAML files.
   */
  private static final Pattern YAML_PATTERN = java.util.regex.Pattern.compile("-\\s*(include|includeAll)\\s*:");

  @Override
  protected @NonNull Pattern getPattern()
  {
    return YAML_PATTERN;
  }

  @Override
  public void modifyContent(@NonNull Map<Path, Path> pConvertedFiles, @NonNull Path pInput, @NonNull Path pIncludeFile, @NonNull Path pNewIncludeFile)
      throws IOException
  {
    DumperOptions dumperOptions = new DumperOptions();
    dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

    Yaml yaml = new Yaml(new Constructor(DatabaseChangeLogData.class, new LoaderOptions()), new Representer(dumperOptions));

    // read the data
    DatabaseChangeLogData databaseChangeLogData = yaml.load(Files.readString(pIncludeFile, StandardCharsets.UTF_8));

    // change the data
    for (Map<String, Object> entry : databaseChangeLogData.getDatabaseChangeLog())
    {
      if (entry.containsKey("include"))
      {
        //noinspection unchecked
        Map<String, Object> includeMap = (Map<String, Object>) entry.get("include");

        Object file = includeMap.get("file");
        Object relativeToChangelogValue = includeMap.get("relativeToChangelogFile");
        boolean relativeToChangelog = relativeToChangelogValue != null && Boolean.parseBoolean(relativeToChangelogValue.toString());

        if (file != null)
          includeMap.put("file", changeFile(pConvertedFiles, pInput, pIncludeFile, file.toString(), relativeToChangelog));
      }
    }

    // write the changed data
    try (BufferedWriter writer = Files.newBufferedWriter(pNewIncludeFile, StandardCharsets.UTF_8))
    {
      yaml.dump(databaseChangeLogData, writer);
    }
  }


  /**
   * The database changelog for YAML conversion.
   */
  @Getter
  @Setter
  @NoArgsConstructor
  public static class DatabaseChangeLogData
  {
    private List<Map<String, Object>> databaseChangeLog;
  }

}
