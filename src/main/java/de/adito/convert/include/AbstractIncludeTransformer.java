package de.adito.convert.include;

import lombok.*;
import lombok.extern.java.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.logging.*;
import java.util.regex.*;

/**
 * Abstract transformer for changing the content of any include file
 *
 * @author r.hartinger, 27.06.2024
 */
@Log
abstract class AbstractIncludeTransformer
{

  /**
   * Adds the {@link ConsoleHandler} when creating the instance to the logger, in order to always log to the console.
   */
  protected AbstractIncludeTransformer()
  {
    log.addHandler(new ConsoleHandler());
  }

  /**
   * Checks for includes in a given file.
   *
   * @param pPathToConvert The file that should be checked for includes
   * @return {@code true}, if includes are in the file, {@code false} when no includes are there
   */
  public final boolean checkForIncludes(@NonNull Path pPathToConvert)
  {
    try
    {
      String content = Files.readString(pPathToConvert, StandardCharsets.UTF_8);

      Matcher matcher = getPattern().matcher(content);
      return matcher.find();
    }
    catch (IOException pE)
    {
      log.log(Level.WARNING, String.format("error reading file for reading includes in file '%s'", pPathToConvert), pE);
      return false;
    }
  }

  /**
   * Gets the pattern for detecting includes in a file.
   *
   * @return the pattern for detaching includes.
   */
  @NonNull
  protected abstract Pattern getPattern();


  /**
   * Modifies the content of a file with includes.
   *
   * @param pConvertedFiles the currently converted files with their old and new path
   * @param pInput          the given input root path by the user
   * @param pIncludeFile    the file with the includes
   * @param pNewIncludeFile the path were the new include file should be stored
   * @throws Exception when any error during modifying the include file occurs
   */
  public abstract void modifyContent(@NonNull Map<Path, Path> pConvertedFiles, @NonNull Path pInput,
                                     @NonNull Path pIncludeFile, @NonNull Path pNewIncludeFile)
      throws Exception; // NOSONAR we want to throw all exceptions here


  /**
   * Changes the value of the file element in the include files.
   *
   * @param pConvertedFiles      the currently converted files with their old and new path
   * @param pInput               the given input root path by the user
   * @param pIncludeFile         the file with the includes
   * @param pOldFile             the old value given in the file attribute
   * @param pRelativeToChangelog if the file is relative to the current changelog
   * @return the new file path
   */
  protected String changeFile(@NonNull Map<Path, Path> pConvertedFiles, @NonNull Path pInput,
                              @NonNull Path pIncludeFile, @NonNull String pOldFile, boolean pRelativeToChangelog)
  {

    for (Map.Entry<Path, Path> fileHandled : pConvertedFiles.entrySet())
    {
      Path oldPath = fileHandled.getKey();
      Path newPath = fileHandled.getValue();

      String relativePath = pRelativeToChangelog ? relativize(pIncludeFile.getParent(), oldPath) : relativize(pInput, oldPath);

      if (pOldFile.equals(relativePath))
        // replace the old with the new name
        return pOldFile.replace(oldPath.getFileName().toString(), newPath.getFileName().toString());
    }

    return pOldFile;
  }

  /**
   * Makes a relative path from a basis to a file. The relative path will be returned as string with {@code /} as path separator.
   *
   * @param pBasis the basis path for the relative path generation
   * @param pFile  the file path for the relative path generation
   * @return the relative path as String with {@code /} as path separator
   */
  protected @NonNull String relativize(@NonNull Path pBasis, @NonNull Path pFile)
  {
    return pBasis.relativize(pFile).toString().replace("\\", "/");
  }
}
