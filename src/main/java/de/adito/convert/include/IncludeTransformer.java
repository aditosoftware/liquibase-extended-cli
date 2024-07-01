package de.adito.convert.include;

import lombok.*;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;

/**
 * Transform the includes of the changelog files.
 *
 * @author r.hartinger, 27.06.2024
 */
@AllArgsConstructor
public class IncludeTransformer
{
  /**
   * The files that contains includes
   */
  @NonNull
  @Getter
  private final Set<Path> includeFiles = new HashSet<>();


  /**
   * The files that were converted during the execution of the CLI.
   */
  @NonNull
  private final Map<Path, Path> convertedFiles = new HashMap<>();

  /**
   * All the detailed included transformers.
   *
   * <ul>
   *   <li><b>Key:</b> the extension of the file in lower case</li>
   *   <li><b>Value:</b> the include transformer for this file type</li>
   * </ul>
   */
  private final Map<String, AbstractIncludeTransformer> includeTransformers = Map.of(
      "xml", new XmlIncludeTransformer(),
      "yaml", new YamlIncludeTransformer(),
      "json", new JsonIncludeTransformer()
  );


  /**
   * Adds a file that was converted. These files are later used by every transformer to check the new file paths.
   *
   * @param pOldPath the old path to the file
   * @param pNewPath the new path to the file
   */
  public void addConvertedFile(@NonNull Path pOldPath, @NonNull Path pNewPath)
  {
    convertedFiles.put(pOldPath, pNewPath);
  }

  /**
   * Transform the includes.
   * <p>
   * This means changing the file path from the old path to a new path, if the file given in the include section was converted or copied before.
   *
   * @param pInput          the input path were the root of all changelogs is located
   * @param pIncludeFile    the path of the include file
   * @param pNewIncludeFile the path were the new include file should be written
   * @throws Exception Error reading or writing a changelog file
   */
  public void transformIncludes(@NonNull Path pInput, @NonNull Path pIncludeFile, @NonNull Path pNewIncludeFile) throws Exception
  {
    AbstractIncludeTransformer transformer = getTransformer(pIncludeFile);
    if (transformer != null)
      transformer.modifyContent(convertedFiles, pInput, pIncludeFile, pNewIncludeFile);
  }

  /**
   * Checks for includes in a given file.
   *
   * @param pPathToConvert The file that should be checked for includes
   * @return {@code true}, if includes are in the file, {@code false} when no includes are there
   */
  public boolean checkForIncludes(@NonNull Path pPathToConvert)
  {
    AbstractIncludeTransformer transformer = getTransformer(pPathToConvert);
    return transformer != null && transformer.checkForIncludes(pPathToConvert);

  }

  /**
   * Gets the correct transformer for the given file path.
   *
   * @param pFile the file for which the transformer should be returned
   * @return the correct {@link AbstractIncludeTransformer} or {@code null}, if no transformer was found
   */
  @Nullable
  private AbstractIncludeTransformer getTransformer(@NonNull Path pFile)
  {
    String extension = FilenameUtils.getExtension(pFile.getFileName().toString()).toLowerCase();

    return includeTransformers.get(extension);
  }

}
