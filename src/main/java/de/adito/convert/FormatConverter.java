package de.adito.convert;

import de.adito.util.*;
import liquibase.changelog.*;
import liquibase.parser.*;
import liquibase.resource.*;
import liquibase.serializer.*;
import lombok.*;
import lombok.extern.java.Log;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.logging.*;
import java.util.stream.*;

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
@Log
public class FormatConverter implements Callable<Integer>
{

  @Option(names = {"-f", "--format"}, description = "The format you want to convert to. Valid values: ${COMPLETION-CANDIDATES}",
      required = true)
  private Format format;

  @Option(names = {"-d", "--database-type"}, description = "The type of the database. This is only required when converting to SQL")
  private String databaseType;

  @Parameters(description = "The input file or directory", index = "0", converter = ExistingPathConverter.class)
  private Path input;

  @Parameters(description = "The output directory", index = "1", converter = ExistingFolderConverter.class)
  private Path output;

  @Spec
  private CommandSpec spec;

  /**
   * The files that could not be converted. These will be put out at the end of the command execution.
   */
  private final Set<Path> errorFiles = new HashSet<>();

  /**
   * The handler for the include files.
   */
  private final IncludeHandler includeHandler = new IncludeHandler();

  @Override
  public Integer call() throws Exception
  {
    log.addHandler(new ConsoleHandler());

    if (format == Format.SQL && StringUtils.isBlank(databaseType))
    {
      throw new ParameterException(spec.commandLine(), "Option '--database-type' is required, when format SQL is given");
    }

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

    // handles the includes after all files were transformed
    if (!includeHandler.getIncludeFiles().isEmpty())
      handleIncludes();

    if (errorFiles.isEmpty())
      return 0;
    else
    {
      System.err.println("Error converting " + errorFiles.size() + " file(s):\n" +
                             errorFiles.stream().map(pPath -> " - " + pPath).collect(Collectors.joining("\n")) +
                             "\nThese file(s) were copied to the new location.");
      return 3;
    }
  }

  /**
   * Handles the files with includes.
   */
  private void handleIncludes()
  {

    for (Path includeFile : includeHandler.getIncludeFiles())
    {
      try
      {
        System.out.printf("Handling file '%s' with includes%n", relativizeInput(includeFile));

        includeHandler.handleIncludes(input, includeFile, generateNewFileName(includeFile, false));
      }
      catch (Exception pE)
      {
        log.log(Level.WARNING, String.format("error while handling file with includes '%s' to format %s", includeFile, format), pE);
        errorFiles.add(includeFile);
        copyOldFile(includeFile);
      }
    }
  }


  /**
   * Converts one file.
   *
   * @param pPathToConvert The full path to the existing file, that should be converted
   */
  private void convertFile(@NonNull Path pPathToConvert)
  {

    String extension = FilenameUtils.getExtension(pPathToConvert.toString());
    if (!Format.isValidFormat(extension) || format.isTargetFormat(extension))
    {
      // invalid file format or file in the correct target format, just copy the old file to the new location
      System.out.printf("Copying file '%s' to new location%n", relativizeInput(pPathToConvert));

      copyOldFile(pPathToConvert);
    }
    else if (includeHandler.checkForIncludes(pPathToConvert))
    {
      // file with include will be handled after all other files, save for later
      includeHandler.getIncludeFiles().add(pPathToConvert);
    }
    else
    {
      // valid file format, convert it
      System.out.printf("Converting changeset '%s'%n", relativizeInput(pPathToConvert));
      
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

          includeHandler.addConvertedFile(pPathToConvert, newFilePath);
        }
      }
      catch (Exception pE)
      {
        log.log(Level.WARNING, String.format("error converting file '%s' to format %s", pPathToConvert, format), pE);
        errorFiles.add(pPathToConvert);
        copyOldFile(pPathToConvert);
      }
    }
  }


  /**
   * Copies an old file to the new location without converting.
   *
   * @param pOldFile The file that needs to be copied
   */
  private void copyOldFile(Path pOldFile)
  {
    try
    {
      Path newFile = generateNewFileName(pOldFile, false);

      Files.copy(pOldFile, newFile, StandardCopyOption.REPLACE_EXISTING);
    }
    catch (IOException pE)
    {
      // TODO hier anders mit dem Fehler verfahren?
      log.log(Level.SEVERE, String.format("error copying file '%s' to new target dir", pOldFile), pE);
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
      newFileName = baseName + (StringUtils.isBlank(databaseType) ? "" : ("." + databaseType.toLowerCase())) + format.getFileEnding();
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

  /**
   * Relativizes a path to the input directory.
   *
   * @param pPath the given path
   * @return the relative path to the input directory
   */
  @NonNull
  public Path relativizeInput(@NonNull Path pPath)
  {
    return input.getParent().relativize(pPath);
  }

}
