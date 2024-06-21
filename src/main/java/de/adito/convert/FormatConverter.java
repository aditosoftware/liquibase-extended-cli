package de.adito.convert;

import de.adito.util.*;
import liquibase.changelog.*;
import liquibase.parser.*;
import liquibase.resource.*;
import liquibase.serializer.*;
import lombok.*;
import org.apache.commons.io.FilenameUtils;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Converts from one format to another format.
 *
 * @author r.hartinger, 19.06.2024
 */
@CommandLine.Command(name = "convert", description = "Converts changelogs from one format to another format",
    version = "1.0.0", mixinStandardHelpOptions = true,
    exitCodeListHeading = "Exit codes\n",
    exitCodeList = {
        "0:Successful program execution",
        "3:Partial successful programm execution (check output afterwards)"
    })
@NoArgsConstructor
public class FormatConverter implements Callable<Integer>
{

  @CommandLine.Option(names = {"-f", "--format"}, description = "The format you want to convert to. Valid values: ${COMPLETION-CANDIDATES}",
      required = true)
  private Format format;

  @CommandLine.Parameters(description = "The input file or directory", index = "0", converter = ExistingPathConverter.class)
  private Path input;

  @CommandLine.Parameters(description = "The output directory", index = "1", converter = ExistingFolderConverter.class)
  private Path output;

  // FIXME sql benötigt db-Angabe im Namen bei sql!

  private int errorCode;


  @Override
  public Integer call() throws Exception
  {

    if (Files.isDirectory(input))
    {
      // multiple files, convert them all
      try (Stream<Path> files = Files.walk(input))
      {
        files.filter(Predicate.not(Files::isDirectory))
            .forEach(this::convertFile);
      }
    }
    else
    {
      // single file, just convert
      convertFile(input);
    }
    
    return errorCode;
  }


  /**
   * Converts one file.
   *
   * @param pPathToConvert The full path to the existing file, that should be converted
   */
  private void convertFile(@NonNull Path pPathToConvert)
  {

    if (!Format.isValidFormat(FilenameUtils.getExtension(pPathToConvert.toString())))
    {
      // invalid file format, just copy the old file to the new location
      System.out.printf("Copying file '%s' to new location%n", input.getParent().relativize(pPathToConvert));

      copyOldFile(pPathToConvert);
    }
    else
    {
      // valid file format, convert it
      System.out.printf("Converting changeset '%s'%n", input.getParent().relativize(pPathToConvert));

      try (ResourceAccessor resourceAccessor = new DirectoryResourceAccessor(pPathToConvert.getParent()))
      {
        Path fileName = pPathToConvert.getFileName();

        ChangeLogParser parser = ChangeLogParserFactory.getInstance().getParser(fileName.toString(), resourceAccessor);

        DatabaseChangeLog changeLog = parser.parse(fileName.toString(), new ChangeLogParameters(), resourceAccessor);

        ChangeLogSerializer serializer = ChangeLogSerializerFactory.getInstance().getSerializer(format.getFileEnding());
        Path newFilePath = generateNewFileName(pPathToConvert, true);
        try (OutputStream outputStream = Files.newOutputStream(newFilePath))
        {
          // set the new file path for the changelogs
          changeLog.getChangeSets().forEach(pChangeSet -> pChangeSet.setFilePath(newFilePath.getFileName().toString()));
          // and then write them
          serializer.write(changeLog.getChangeSets(), outputStream);
        }
      }
      catch (Exception pE)
      {
        errorCode = 3;
        System.err.printf("error converting file '%s' to format %s: %s%n", pPathToConvert, format, pE.getMessage());
        pE.printStackTrace(System.err);
        copyOldFile(pPathToConvert);
      }
    }
  }

  private void copyOldFile(Path pOldFile)
  {
    try
    {
      Path newFile = generateNewFileName(pOldFile, false);

      Files.copy(pOldFile, newFile);
    }
    catch (IOException pE)
    {
      System.err.printf("error copying file '%s' to new target dir: %s%n", pOldFile, pE.getMessage());
      pE.printStackTrace(System.err);
    }
  }

  /**
   * Generate the new file name under which the new file should be saved.
   *
   * @param pToConvert    the current file (with full path) that should be converted
   * @param pChangeEnding if the ending should be changed
   * @return the new path and file name
   * @throws IOException Error while creating any necessary directories for the new output location.
   */
  @NonNull
  private Path generateNewFileName(@NonNull Path pToConvert, boolean pChangeEnding) throws IOException
  {
    String newFileName;
    if (pChangeEnding)
    {
      // Find out file name with new extension
      String baseName = FilenameUtils.getBaseName(pToConvert.toString());
      newFileName = baseName + format.getFileEnding();
    }
    else
    {
      newFileName = pToConvert.getFileName().toString();
    }

    Path newLocationInOutput;
    if (input.equals(pToConvert))
    {
      // if we have a single file, then we do not need to find out the location in the folder
      newLocationInOutput = output;
    }
    else
    {
      // find out location in output
      // first, find the relative path inside the input directory for our current file
      Path relativePathInInput = input.relativize(pToConvert);

      // then find the new location with the same relative path in the output directory
      newLocationInOutput = output.resolve(relativePathInInput).getParent();
      if (Files.notExists(newLocationInOutput))
        Files.createDirectories(newLocationInOutput);
    }

    // and finally, set the new file name
    return newLocationInOutput.resolve(newFileName);
  }

}
