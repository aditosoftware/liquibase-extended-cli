package de.adito.convert;

import de.adito.CliTestUtils;
import liquibase.Scope;
import liquibase.changelog.ChangeLogParameters;
import liquibase.command.*;
import liquibase.command.core.helpers.*;
import liquibase.database.Database;
import liquibase.database.core.MockDatabase;
import liquibase.exception.CommandExecutionException;
import liquibase.resource.*;
import lombok.*;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.MockedStatic;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import static de.adito.CliTestUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class for {@link FormatConverter}.
 *
 * @author r.hartinger, 19.06.2024
 */
class FormatConverterTest
{

  @TempDir(cleanup = CleanupMode.ALWAYS)
  private Path outputDir;

  /**
   * Creates the text that is used for logging the converting of the changesets.
   */
  private final Function<String, String> convertText = (pPath) -> "Converting changeset '" + pPath + "'";

  /**
   * Creates the text that is used for logging the copying of the changesets.
   */
  private final Function<String, String> copyText = (pPath) -> "Copying file '" + pPath + "' to new location";

  /**
   * Creates the text that is used for logging the transforming of the changesets with includes.
   */
  private final Function<String, String> transformText = (pPath) -> "Transforming file '" + pPath + "' with includes";

  /**
   * Tests the copying of files
   */
  @Nested
  class Copy
  {
    /**
     * Tests that an error during the converting will be handled correctly.
     */
    @Test
    void shouldHandleErrorDuringConverting()
    {
      String fileName = "invalid.xml";
      Path changelog = createFileInDirInOutputDir(fileName);

      assertCall(
          ExpectedCallResults.builder()
              .errorCode(3)
              .outText(convertText.apply(fileName))
              .errTexts(List.of(
                  "WARNING: error converting file '" + changelog + "' to format YAML",
                  "Error converting 1 file(s):",
                  "Error while converting files:",
                  " - " + changelog,
                  "These file(s) were copied to the new location."
              ))
              .expectedFile(outputDir.resolve(fileName))
              .additionalAssert(() -> assertThat(outputDir.resolve("invalid.yaml")).as("new file should not be there").doesNotExist())
              .build(),
          "convert", "--format", Format.YAML.name(), changelog.toString(), outputDir.toFile().getAbsolutePath());
    }


    /**
     * Tests that the error handling works, when an invalid file was tried to convert and then the copy does fail
     */
    @Test
    void shouldHandleErrorWhileCopyInvalidFiles()
    {
      String fileName = "invalid.xml";
      Path changelog = createFileInDirInOutputDir(fileName);


      try (MockedStatic<Files> filesMockedStatic = mockStatic(Files.class, CALLS_REAL_METHODS))
      {
        filesMockedStatic.when(() -> Files.copy(any(Path.class), any(), any())).thenThrow(new IOException("my message"));

        assertCall(
            ExpectedCallResults.builder()
                .errorCode(3)
                .outText(convertText.apply(changelog.getFileName().toString()))
                .errTexts(List.of(
                    "error copying file '" + changelog + "' to new target dir",
                    "Error converting 2 file(s):",

                    "Error while converting files:",
                    " - " + changelog,
                    "These file(s) were copied to the new location.",


                    "Error while copying files:",
                    " - " + changelog,
                    "These file(s) were NOT copied to the new location."
                ))
                .build(),
            "convert", "--format", Format.YAML.name(), changelog.toString(), outputDir.toFile().getAbsolutePath());
      }
    }


    /**
     * Tests that a file from with a not supported extension, will be copied to the new location.
     */
    @Test
    void shouldCopyInvalidFileExtensionToNewLocation()
    {
      String fileName = "notConverted.txt";
      Path txtFile = createFileInDirInOutputDir(fileName);

      assertCall(
          ExpectedCallResults.builder()
              .errorCode(0)
              .outText(copyText.apply(fileName))
              .expectedFile(outputDir.resolve(fileName))
              .build(),
          "convert", "--format", Format.YAML.name(), txtFile.toString(), outputDir.toFile().getAbsolutePath());
    }

    /**
     * Checks that a changelog that already has the format into which it is to be converted is not converted but copied.
     */
    @Test
    void shouldCopyFileWithSameExtension()
    {
      Path input = getPathForFormat(Format.XML);

      assertCall(
          ExpectedCallResults.builder()
              .errorCode(0)
              .outText(copyText.apply(input.getFileName().toString()))
              .expectedFile(outputDir.resolve(input.getFileName()))
              .build(),
          "convert", "--format", Format.XML.name(), input.toString(), outputDir.toFile().getAbsolutePath());
    }

    /**
     * Tests that an error while copying is handled correctly.
     */
    @Test
    void shouldHandleErrorWhileCopying()
    {
      Path input = getPathForFormat(Format.XML);

      try (MockedStatic<Files> filesMockedStatic = mockStatic(Files.class, CALLS_REAL_METHODS))
      {
        filesMockedStatic.when(() -> Files.copy(any(Path.class), any(), any())).thenThrow(new IOException("my message"));

        assertCall(
            ExpectedCallResults.builder()
                .errorCode(3)
                .outText(copyText.apply(input.getFileName().toString()))
                .errTexts(List.of(
                    "error copying file '" + input + "' to new target dir",
                    "Error converting 1 file(s):",
                    "Error while copying files:",
                    " - " + input,
                    "These file(s) were NOT copied to the new location."
                ))
                .additionalAssert(() -> assertThat(outputDir.resolve(input.getFileName())).as("file was not copied").doesNotExist())
                .build(),
            "convert", "--format", Format.XML.name(), input.toString(), outputDir.toFile().getAbsolutePath());
      }
    }

    /**
     * Creates a file in a folder inside the output directory.
     *
     * @param pFileName the file name
     * @return the path pointing to the created file
     */
    @SneakyThrows
    private @NonNull Path createFileInDirInOutputDir(@NonNull String pFileName)
    {
      Path dir = outputDir.resolve("input");
      Files.createDirectories(dir);

      Path txtFile = outputDir.resolve(pFileName);
      Files.createFile(txtFile);
      return txtFile;
    }
  }

  /**
   * Tests the converting of a single file.
   */
  @Nested
  class ConvertOneFile
  {

    /**
     * Tests that a single xml file can be converted to yaml.
     */
    @Test
    void shouldConvertSingleFileFromXmlToYaml()
    {
      Path input = getPathForFormat(Format.XML);
      Path expected = getPathForFormat(Format.YAML);

      Path expectedFile = outputDir.resolve("XML.yaml");

      assertCall(
          ExpectedCallResults.builder()
              .errorCode(0)
              .outText(convertText.apply("XML.xml"))
              .expectedFile(expectedFile)
              .additionalAssert(() -> assertThat(expectedFile).as("content should be the same").hasSameTextualContentAs(expected))
              .build(),
          "convert", "--format", Format.YAML.name(), input.toString(), outputDir.toFile().getAbsolutePath());
    }

    /**
     * Tests that converting from an XML file to an SQL file does work, even if the XML file does not have the new database name in it.
     *
     * @param pDatabaseType the type of the database. Various spellings should lead to success
     */
    @ParameterizedTest
    @ValueSource(strings = {"mariadb", "MariaDB", "mAriADb"})
    @SneakyThrows
    void shouldConvertSingleFileFromXmlToSQL(@NonNull String pDatabaseType)
    {
      Path inputDir = outputDir.resolve("input");
      Files.createDirectories(inputDir);

      Path input = inputDir.resolve("changelog.xml");
      Files.copy(getPathForFormat(Format.XML), input);

      assertCall(
          ExpectedCallResults.builder()
              .errorCode(0)
              .outText(convertText.apply("changelog.xml"))
              .expectedFile(outputDir.resolve("changelog.mariadb.sql"))
              .additionalAssert(() -> assertThat(outputDir.resolve("changelog.xml")).as("old changelog is not there").doesNotExist())
              .build(),
          "convert", "--format", Format.SQL.name(), "--database-type", pDatabaseType, input.toString(), outputDir.toFile().getAbsolutePath());
    }
  }

  /**
   * Tests the converting of multiple files in one or more directories.
   */
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class ConvertMultipleFiles
  {

    /**
     * Creates arguments with a cross product of each format, except matching formats.
     *
     * @return the created arguments
     */
    private Stream<Arguments> shouldTransformFileWithPreConditions()
    {
      return Arrays.stream(Format.values())
          .flatMap(pGivenFormat -> Arrays.stream(Format.values())
              .filter(pToTransformFormat -> pToTransformFormat != pGivenFormat)
              .map(pToTransformFormat -> Arguments.of(pGivenFormat, pToTransformFormat)));
    }

    /**
     * Tests that the transforming of a file with pre-conditions does work.
     *
     * @param pGivenFormat       the given format
     * @param pToTransformFormat the format to which the file in the given format should be transformed.
     * @see <a href="https://github.com/liquibase/liquibase/issues/4379">Liquibase Issue #4379</a> describes currently a problem with pre-conditions,
     * therefore all tests that transform to JSON or YAML with pre-conditions are failing.
     */
    @ParameterizedTest
    @MethodSource
    void shouldTransformFileWithPreConditions(@NonNull Format pGivenFormat, @NonNull Format pToTransformFormat)
    {
      Path input = getPathForFormat(pGivenFormat, "pre");

      Path expectedFile = outputDir.resolve(pGivenFormat.name() + "-pre.mariadb" + pToTransformFormat.getFileEnding());

      assertCall(
          ExpectedCallResults.builder()
              .errorCode(0)
              .outText(convertText.apply(input.getFileName().toString()))
              .expectedFile(expectedFile)
              .additionalAssert(() -> {
                if (pToTransformFormat == Format.YAML || pToTransformFormat == Format.JSON)
                  assertThrows(CommandExecutionException.class, () -> assertValideFile(expectedFile), "JSON and YAML currently produce invalid results");
                else
                  assertDoesNotThrow(() -> assertValideFile(expectedFile), "created file should be valid");
              })
              .build(),

          "convert", "--format", pToTransformFormat.name(), "--database-type", "mariadb", input.toString(), outputDir.toFile().getAbsolutePath());
    }


    /**
     * @return the arguments for {@link #shouldConvertFromEveryFormatToEveryFormat(Format, Path, String, String)}
     */
    private @NonNull Stream<Arguments> shouldConvertFromEveryFormatToEveryFormat()
    {
      Map<Format, Path> formats = new HashMap<>();

      // loading for every argument the path
      Arrays.stream(Format.values()).forEach(pFormat -> formats.put(pFormat, getPathForFormat(pFormat)));

      // and creating the arguments
      return Arrays.stream(Format.values())
          .flatMap(pOutputFormat -> formats.entrySet().stream()
              .map(pEntry -> {
                Format inputFormat = pEntry.getKey();
                Path input = pEntry.getValue();
                String expectedOutFileName = inputFormat.name() + (pOutputFormat == Format.SQL && inputFormat != Format.SQL ? ".mariadb" : "") + pOutputFormat.getFileEnding();
                String expectedOutText = pOutputFormat == inputFormat ?
                    copyText.apply(input.getFileName().toString()) :
                    convertText.apply(input.getFileName().toString());

                return Arguments.of(pOutputFormat, input, expectedOutFileName, expectedOutText);
              }));
    }

    /**
     * Tests that converting from every format to every format does work.
     *
     * @param pFormat           the format to which it should be converted
     * @param pInput            the given input file
     * @param pExpectedFileName the expected file name
     * @param pExpectedOutText  the expected text that should be written to out
     */
    @ParameterizedTest
    @MethodSource
    void shouldConvertFromEveryFormatToEveryFormat(@NonNull Format pFormat, @NonNull Path pInput, @NonNull String pExpectedFileName,
                                                   @NonNull String pExpectedOutText)
    {
      ExpectedCallResults expectedCallResults = ExpectedCallResults.builder()
          .errorCode(0)
          .outText(pExpectedOutText)
          .expectedFile(outputDir.resolve(pExpectedFileName))
          .build();


      if (pFormat == Format.SQL)
        // only call with additional parameter when format is sql
        assertCall(expectedCallResults, "convert", "--format", pFormat.name(), "--database-type", "mariadb", pInput.toString(), outputDir.toFile().getAbsolutePath());
      else
        assertCall(expectedCallResults, "convert", "--format", pFormat.name(), pInput.toString(), outputDir.toFile().getAbsolutePath());
    }

    /**
     * Tests that a given folder can be converted.
     *
     * @param pFormat The format to which the changelogs should be converted
     */
    @ParameterizedTest
    @EnumSource
    @SneakyThrows
    void shouldWorkWithFolder(@NonNull Format pFormat)
    {
      Path inputForFiles = CliTestUtils.loadResource("convert");


      Path input = outputDir.resolve("input");
      Files.createDirectories(input);

      List<String> expected = Arrays.stream(Format.values())
          .map(pAnyFormat -> {
            String fileName = "input" + File.separator + pAnyFormat.name() + pAnyFormat.getFileEnding();

            if (pAnyFormat == pFormat)
              return copyText.apply(fileName);
            else
              return convertText.apply(fileName);


          }).collect(Collectors.toList());


      // creates an input dir with the four changelogs
      // we cannot use the inputForFiles directory, because it has class files, and we do not want to test this error case here
      Arrays.stream(Format.values())
          .forEach(pSingleFormat -> {
            Path pathForFormat = getPathForFormat(pSingleFormat).getFileName();
            assertDoesNotThrow(() -> Files.copy(inputForFiles.resolve(pathForFormat), input.resolve(pathForFormat)));
          });


      assertCall(
          ExpectedCallResults.builder()
              .errorCode(0)
              .outTexts(expected)
              .expectedFiles(
                  Arrays.stream(Format.values())
                      // file name should be always include .mariadb before the ending
                      // except when the file is in our current format, then it is just copied
                      .map(pFilenameFormat -> {
                        if (pFilenameFormat == pFormat)
                          return pFormat.name() + pFormat.getFileEnding();
                        else
                          return pFilenameFormat.name() + ".mariadb" + pFormat.getFileEnding();
                      })
                      .map(pFileName -> outputDir.resolve(pFileName))
                      .collect(Collectors.toList()))
              .build(),
          "convert", "--format", pFormat.name(), "--database-type", "mariadb", input.toString(), outputDir.toFile().getAbsolutePath());
    }


    /**
     * @return creates the arguments for {@link #shouldWorkWithMultipleFolders(Format, Format)}
     */
    private Stream<Arguments> shouldWorkWithMultipleFolders()
    {
      return Arrays.stream(Format.values())
          .flatMap(pFormat -> Arrays.stream(Format.values())
              .map(pGivenFormat -> Arguments.of(pFormat, pGivenFormat))
          );
    }

    /**
     * Tests that a given folder can be converted.
     *
     * @param pTargetFormat The format to which the changelogs should be converted
     * @param pGivenFormat  The format the current changelogs are having
     */
    @ParameterizedTest
    @MethodSource
    @SneakyThrows
    void shouldWorkWithMultipleFolders(@NonNull Format pTargetFormat, @NonNull Format pGivenFormat)
    {
      List<String> folders = List.of("a", "b", "b" + File.separatorChar + "c");
      BiFunction<Format, String, List<String>> createExpectedFiles = (pAnyFormat, pDatabaseType) ->
          List.of("changelogInInput" + pDatabaseType + pAnyFormat.getFileEnding(),
                  "a" + File.separatorChar + "changelogInA" + pDatabaseType + pAnyFormat.getFileEnding(),
                  "b" + File.separatorChar + "c" + File.separatorChar + "changelogInBC" + pDatabaseType + pAnyFormat.getFileEnding());

      // create file structure for tests
      Path output = outputDir.resolve(Paths.get("output"));
      Files.createDirectories(output);

      Path input = outputDir.resolve(Paths.get("input"));
      Files.createDirectories(input);

      Path basicChangelog = getPathForFormat(pGivenFormat);

      Files.copy(basicChangelog, input.resolve(Path.of("changelogInInput" + pGivenFormat.getFileEnding())));

      Path subFolderA = input.resolve("a");
      Files.createDirectories(subFolderA);
      Files.copy(basicChangelog, subFolderA.resolve(Path.of("changelogInA" + pGivenFormat.getFileEnding())));

      Path subFolderC = input.resolve(Path.of("b", "c"));
      Files.createDirectories(subFolderC);

      Files.copy(basicChangelog, subFolderC.resolve(Path.of("changelogInBC" + pGivenFormat.getFileEnding())));

      // check if the files were created as expected for the test
      assertThat(getFilesInDirectory(input))
          .as("input should be created as expected")
          .containsExactlyInAnyOrder(Stream.concat(createExpectedFiles.apply(pGivenFormat, "").stream(), folders.stream()).toArray(String[]::new));

      String expectedDatabaseType = pGivenFormat == pTargetFormat ? "" : ".mariadb";

      List<String> expectedFiles = createExpectedFiles.apply(pTargetFormat, expectedDatabaseType);

      assertCall(
          ExpectedCallResults.builder()
              .errorCode(0)
              .outTexts(createExpectedFiles.apply(pGivenFormat, ""))
              .expectedFiles(expectedFiles.stream().map(output::resolve).collect(Collectors.toList()))
              .additionalAssert(
                  () -> assertThat(getFilesInDirectory(output))
                      .as("output should be created as expected with the folders")
                      .containsExactlyInAnyOrder(
                          Stream.concat(expectedFiles.stream(), folders.stream()).toArray(String[]::new)))
              .build(),
          "convert", "--format", pTargetFormat.name(), "--database-type", "mariadb", input.toString(), output.toFile().getAbsolutePath());
    }


    /**
     * Returns the relative paths for all files and folders in the given directory.
     *
     * @param pPath the directory which children should be extracted
     * @return the relative names of the files and folders inside the directory
     */
    @SneakyThrows
    private Set<String> getFilesInDirectory(@NonNull Path pPath)
    {
      try (Stream<Path> files = Files.walk(pPath))
      {
        return files.map(pPath::relativize)
            .map(Path::toString)
            .filter(Predicate.not(String::isBlank))
            .collect(Collectors.toSet());
      }
    }
  }

  /**
   * Tests various converting cases with includes.
   */
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class ConvertIncludes
  {
    private static final String firstExpectedIncludeFilesInfoMessage = "The following files will not be converted, since they contain include/includeAll:";
    private static final String secondExpectedIncludeConversionMessage = "If possible, the paths of those includes were transformed to use the new file ending.";

    /**
     * Supplies all formats that are supported in the community edition.
     */
    private final Supplier<Stream<Format>> formatForIncludes = () -> Arrays.stream(Format.values())
        // includes are not possible in the community edition for SQL
        .filter(pGivenFormat -> pGivenFormat != Format.SQL);

    /**
     * Tests that errors while checking for includes are detected.
     */
    @Test
    void shouldHandleErrorsWhileCheckingForIncludes()
    {
      Path file = getPathForFormat(Format.XML);

      try (MockedStatic<Files> filesMockedStatic = mockStatic(Files.class, CALLS_REAL_METHODS))
      {
        filesMockedStatic.when(() -> Files.readString(file, StandardCharsets.UTF_8)).thenThrow(IOException.class);

        assertCall(
            ExpectedCallResults.builder()
                .errorCode(0)
                .outText(convertText.apply("XML.xml"))
                .errText("WARNING: error reading file for reading includes in file '" + file + "'")
                .build(),
            "convert", "--format", Format.YAML.name(), file.toString(), outputDir.toFile().getAbsolutePath());
      }
    }


    /**
     * Tests that an error during the handling of includes does work.
     */
    @Test
    @SneakyThrows
    void shouldHandleErrorsWhileConvertingIncludeFile()
    {
      Path input = outputDir.resolve("input");
      Files.createDirectories(input);

      // create multiple invalid files
      List<Path> transformedFiles = IntStream.of(1, 2, 3)
          .mapToObj(pCount -> {
            Path file = input.resolve("foo" + pCount + ".xml");
            assertDoesNotThrow(() -> Files.writeString(file, "<include"), "broken file " + file + " can be created");
            return file;
          })
          .collect(Collectors.toList());


      List<String> errTexts = transformedFiles.stream()
          .flatMap(pPath -> Stream.of(" - " + pPath,
                                      "WARNING: error while transforming file with includes '" + pPath + "' to format YAML"))
          .collect(Collectors.toList());
      errTexts.add("Error converting 3 file(s):");
      errTexts.add("Error while transforming includes:");
      errTexts.add("These file(s) were copied to the new location.");

      List<String> expectedOutput = transformedFiles.stream()
          .map(pPath -> input.getParent().relativize(pPath))
          .map(Path::toString)
          .map(transformText::apply)
          .collect(Collectors.toList());
      expectedOutput.add(firstExpectedIncludeFilesInfoMessage);
      expectedOutput.addAll(transformedFiles.stream().map(pPath -> " - " + pPath).collect(Collectors.toList()));
      expectedOutput.add(secondExpectedIncludeConversionMessage);
      assertCall(
          ExpectedCallResults.builder()
              .errorCode(3)
              .outTexts(expectedOutput)
              .errTexts(errTexts)
              .build(),
          "convert", "--format", Format.YAML.name(), input.toString(), outputDir.toFile().getAbsolutePath());
    }

    /**
     * @return the arguments for {@link #shouldHandleFileWithInclude(List, List, Format, Format)}
     */
    private Stream<Arguments> shouldHandleFileWithInclude()
    {
      return formatForIncludes.get().flatMap(pFormat -> {
        List<Arguments> xml = List.of(
            Arguments.of(
                List.of(
                    "<include file=\"XML.xml\"/>",
                    "<include context=\"bar\" errorIfMissing=\"false\" file=\"JSON.json\" ignore=\"false\" labels=\"baz\" relativeToChangelogFile=\"false\"/>",
                    "<include context=\"bar\" errorIfMissing=\"false\" file=\"YAML.yaml\" ignore=\"false\" labels=\"baz\" relativeToChangelogFile=\"true\"/>"),
                List.of(), pFormat, Format.XML),
            Arguments.of(
                List.of(
                    "<include file=\"XML" + pFormat.getFileEnding() + "\"/>",
                    "<include context=\"bar\" errorIfMissing=\"false\" file=\"JSON.json\" ignore=\"false\" labels=\"baz\" relativeToChangelogFile=\"false\"/>",
                    "<include context=\"bar\" errorIfMissing=\"false\" file=\"YAML.yaml\" ignore=\"false\" labels=\"baz\" relativeToChangelogFile=\"true\"/>"
                ), List.of(Format.XML), pFormat, Format.XML),
            Arguments.of(
                List.of(
                    "<include file=\"XML.xml\"/>",
                    "<include context=\"bar\" errorIfMissing=\"false\" file=\"JSON" + pFormat.getFileEnding() + "\" ignore=\"false\" labels=\"baz\" relativeToChangelogFile=\"false\"/>",
                    "<include context=\"bar\" errorIfMissing=\"false\" file=\"YAML.yaml\" ignore=\"false\" labels=\"baz\" relativeToChangelogFile=\"true\"/>"
                ), List.of(Format.JSON), pFormat, Format.XML),
            Arguments.of(
                List.of(
                    "<include file=\"XML.xml\"/>",
                    "<include context=\"bar\" errorIfMissing=\"false\" file=\"JSON.json\" ignore=\"false\" labels=\"baz\" relativeToChangelogFile=\"false\"/>",
                    "<include context=\"bar\" errorIfMissing=\"false\" file=\"YAML" + pFormat.getFileEnding() + "\" ignore=\"false\" labels=\"baz\" relativeToChangelogFile=\"true\"/>"
                ), List.of(Format.YAML), pFormat, Format.XML),
            Arguments.of(
                List.of(
                    "<include file=\"XML" + pFormat.getFileEnding() + "\"/>",
                    "<include context=\"bar\" errorIfMissing=\"false\" file=\"JSON" + pFormat.getFileEnding() + "\" ignore=\"false\" labels=\"baz\" relativeToChangelogFile=\"false\"/>",
                    "<include context=\"bar\" errorIfMissing=\"false\" file=\"YAML" + pFormat.getFileEnding() + "\" ignore=\"false\" labels=\"baz\" relativeToChangelogFile=\"true\"/>"
                ), List.of(Format.XML, Format.JSON, Format.YAML), pFormat, Format.XML));


        List<Arguments> json = List.of(
            Arguments.of(List.of("\"file\": \"JSON.json\"", "\"file\": \"XML.xml\",", "\"file\": \"YAML.yaml\","), List.of(), pFormat, Format.JSON),
            Arguments.of(List.of("\"file\": \"JSON" + pFormat.getFileEnding() + "\"", "\"file\": \"XML.xml\",", "\"file\": \"YAML.yaml\","), List.of(Format.JSON), pFormat, Format.JSON),
            Arguments.of(List.of("\"file\": \"JSON.json\"", "\"file\": \"XML" + pFormat.getFileEnding() + "\",", "\"file\": \"YAML.yaml\","), List.of(Format.XML), pFormat, Format.JSON),
            Arguments.of(List.of("\"file\": \"JSON.json\"", "\"file\": \"XML.xml\",", "\"file\": \"YAML" + pFormat.getFileEnding() + "\","), List.of(Format.YAML), pFormat, Format.JSON),
            Arguments.of(List.of("\"file\": \"JSON" + pFormat.getFileEnding() + "\"", "\"file\": \"XML" + pFormat.getFileEnding() + "\",", "\"file\": \"YAML" + pFormat.getFileEnding() + "\","),
                         List.of(Format.XML, Format.JSON, Format.YAML), pFormat, Format.JSON));


        List<Arguments> yaml = List.of(
            Arguments.of(List.of("file: YAML.yaml", "file: XML.xml", "file: JSON.json"), List.of(), pFormat, Format.YAML),
            Arguments.of(List.of("file: YAML" + pFormat.getFileEnding(), "file: XML.xml", "file: JSON.json"), List.of(Format.YAML), pFormat, Format.YAML),
            Arguments.of(List.of("file: YAML.yaml", "file: XML.xml", "file: JSON" + pFormat.getFileEnding()), List.of(Format.JSON), pFormat, Format.YAML),
            Arguments.of(List.of("file: YAML.yaml", "file: XML" + pFormat.getFileEnding(), "file: JSON.json"), List.of(Format.XML), pFormat, Format.YAML),
            Arguments.of(List.of("file: YAML" + pFormat.getFileEnding(), "file: XML" + pFormat.getFileEnding(), "file: JSON" + pFormat.getFileEnding()),
                         List.of(Format.XML, Format.JSON, Format.YAML), pFormat, Format.YAML));


        if (pFormat == Format.XML)
          return Stream.concat(json.stream(), yaml.stream());
        else if (pFormat == Format.YAML)
          return Stream.concat(xml.stream(), json.stream());
        else if (pFormat == Format.JSON)
          return Stream.concat(xml.stream(), yaml.stream());
        else
          throw new IllegalArgumentException("Format " + pFormat + " was not expected while building arguments");
      });
    }

    /**
     * Tests that the includes of files in the same level are able to be transformed to the new file path.
     *
     * @param pExpected          the expected elements in the lines with {@code file}
     * @param pAdditionalFormats list of formats that should be in the input folder next to the include file
     * @param pFormatToConvertTo to format to which the given folder should be prepared
     * @param pPreparedFormat    the format for the include file in the prepared folder
     */
    @ParameterizedTest
    @MethodSource
    void shouldHandleFileWithInclude(@NonNull List<String> pExpected, @NonNull List<Format> pAdditionalFormats, @NonNull Format pFormatToConvertTo,
                                     @NonNull Format pPreparedFormat)
    {
      PreparedIncludes preparedIncludes = prepare(pPreparedFormat, pAdditionalFormats);

      Path expectedFile = outputDir.resolve(preparedIncludes.includeFile.getFileName());

      assertCall(
          ExpectedCallResults.builder()
              .errorCode(0)
              .outTexts(List.of(
                            transformText.apply(preparedIncludes.inputDirectory.getParent().relativize(preparedIncludes.includeFile).toString()),
                            firstExpectedIncludeFilesInfoMessage,
                            " - " + preparedIncludes.includeFile,
                            secondExpectedIncludeConversionMessage
                        )
              )
              .expectedFile(expectedFile)
              .additionalAssert(
                  () -> {
                    List<String> content = Files.readAllLines(expectedFile, StandardCharsets.UTF_8);

                    List<String> fileLines = content.stream()
                        .filter(pLine -> pLine.contains("file"))
                        .map(String::trim)
                        .collect(Collectors.toList());

                    assertThat(fileLines).as("file content should be as pExpected: \n" + String.join("\n", content)).containsExactlyInAnyOrderElementsOf(pExpected);
                  })
              .build(),

          "convert", "--format", pFormatToConvertTo.name(), preparedIncludes.inputDirectory.toString(), outputDir.toFile().getAbsolutePath());
    }


    /**
     * @return the arguments for {@link #shouldConvertNestedChangelog(Format, ArgumentsForNestedChangelogs)}
     */
    private Stream<Arguments> shouldConvertNestedChangelog()
    {
      String xmlContextPath = "context/xml/";
      ArgumentsForNestedChangelogs xml = new ArgumentsForNestedChangelogs(
          Format.XML, CliTestUtils.loadResource(xmlContextPath),
          List.of(
              convertText.apply("xml" + File.separatorChar + "changelogs" + File.separatorChar + "changelog1.xml"),
              convertText.apply("xml" + File.separatorChar + "changelogs" + File.separatorChar + "changelog2.xml"),
              convertText.apply("xml" + File.separatorChar + "changelogs" + File.separatorChar + "version1" + File.separatorChar + "v1changelog1.xml"),
              convertText.apply("xml" + File.separatorChar + "changelogs" + File.separatorChar + "version1" + File.separatorChar + "v1changelog2.xml"),
              convertText.apply("xml" + File.separatorChar + "changelogs" + File.separatorChar + "version2" + File.separatorChar + "v2changelog1.xml"),
              convertText.apply("xml" + File.separatorChar + "changelogs" + File.separatorChar + "version2" + File.separatorChar + "v2changelog2.xml"),
              convertText.apply("xml" + File.separatorChar + "example-changelog.xml"),
              convertText.apply("xml" + File.separatorChar + "noContext-changelog.xml"),
              convertText.apply("xml" + File.separatorChar + "three-changelogs.xml"),
              convertText.apply("xml" + File.separatorChar + "utf8-changelog.xml"),
              transformText.apply("xml" + File.separatorChar + "nested-changelog.xml"),
              transformText.apply("xml" + File.separatorChar + "changelogs" + File.separatorChar + "changelog3.xml"),
              firstExpectedIncludeFilesInfoMessage,
              " - " + CliTestUtils.loadResource(xmlContextPath) + File.separatorChar + "nested-changelog.xml",
              " - " + CliTestUtils.loadResource(xmlContextPath) + File.separatorChar + "changelogs" + File.separatorChar + "changelog3.xml",
              secondExpectedIncludeConversionMessage
          ),
          pFormat -> new String[]{"<include context=\"xml-junit\" file=\"changelogs/changelog1" + pFormat.getFileEnding() + "\" relativeToChangelogFile=\"true\"/>",
                                  "<include context=\"xml-junit\" file=\"changelogs/changelog2" + pFormat.getFileEnding() + "\" relativeToChangelogFile=\"true\"/>",
                                  "<include context=\"xml-junit\" file=\"changelogs/changelog3.xml\" relativeToChangelogFile=\"true\"/>"},
          pFormat -> new String[]{"<includeAll context=\"xml-v1\" path=\"version1\" relativeToChangelogFile=\"true\"/>",
                                  "<include context=\"xml-v2\" file=\"version2/v2changelog1" + pFormat.getFileEnding() + "\" relativeToChangelogFile=\"true\"/>",
                                  "<include context=\"xml-v2\" file=\"version2/v2changelog2" + pFormat.getFileEnding() + "\" relativeToChangelogFile=\"true\"/>"}
      );

      String yamlContextPath = "context/yaml";
      ArgumentsForNestedChangelogs yaml = new ArgumentsForNestedChangelogs(
          Format.YAML, CliTestUtils.loadResource(yamlContextPath),
          List.of(
              convertText.apply("yaml" + File.separatorChar + "changelogs" + File.separatorChar + "changelog1.yaml"),
              convertText.apply("yaml" + File.separatorChar + "changelogs" + File.separatorChar + "changelog2.yaml"),
              convertText.apply("yaml" + File.separatorChar + "changelogs" + File.separatorChar + "version1" + File.separatorChar + "v1changelog1.yaml"),
              convertText.apply("yaml" + File.separatorChar + "changelogs" + File.separatorChar + "version1" + File.separatorChar + "v1changelog2.yaml"),
              convertText.apply("yaml" + File.separatorChar + "changelogs" + File.separatorChar + "version2" + File.separatorChar + "v2changelog1.yaml"),
              convertText.apply("yaml" + File.separatorChar + "changelogs" + File.separatorChar + "version2" + File.separatorChar + "v2changelog2.yaml"),
              convertText.apply("yaml" + File.separatorChar + "example-changelog.yaml"),
              convertText.apply("yaml" + File.separatorChar + "noContext-changelog.yaml"),
              convertText.apply("yaml" + File.separatorChar + "three-changelogs.yaml"),
              convertText.apply("yaml" + File.separatorChar + "utf8-changelog.yaml"),
              transformText.apply("yaml" + File.separatorChar + "nested-changelog.yaml"),
              transformText.apply("yaml" + File.separatorChar + "changelogs" + File.separatorChar + "changelog3.yaml"),
              firstExpectedIncludeFilesInfoMessage,
              " - " + CliTestUtils.loadResource(yamlContextPath) + File.separatorChar + "nested-changelog.yaml",
              " - " + CliTestUtils.loadResource(yamlContextPath) + File.separatorChar + "changelogs" + File.separatorChar + "changelog3.yaml",
              secondExpectedIncludeConversionMessage
          ),
          pFormat -> new String[]{"file: changelogs/changelog1" + pFormat.getFileEnding(),
                                  "file: changelogs/changelog2" + pFormat.getFileEnding(),
                                  "file: changelogs/changelog3.yaml"},
          pFormat -> new String[]{"file: version2/v2changelog1" + pFormat.getFileEnding(), "file: version2/v2changelog2" + pFormat.getFileEnding()}
      );

      String jsonContextPath = "context/json/";
      ArgumentsForNestedChangelogs json = new ArgumentsForNestedChangelogs(
          Format.JSON, CliTestUtils.loadResource(jsonContextPath),
          List.of(
              convertText.apply("json" + File.separatorChar + "changelogs" + File.separatorChar + "changelog1.json"),
              convertText.apply("json" + File.separatorChar + "changelogs" + File.separatorChar + "changelog2.json"),
              convertText.apply("json" + File.separatorChar + "changelogs" + File.separatorChar + "version1" + File.separatorChar + "v1changelog1.json"),
              convertText.apply("json" + File.separatorChar + "changelogs" + File.separatorChar + "version1" + File.separatorChar + "v1changelog2.json"),
              convertText.apply("json" + File.separatorChar + "changelogs" + File.separatorChar + "version2" + File.separatorChar + "v2changelog1.json"),
              convertText.apply("json" + File.separatorChar + "changelogs" + File.separatorChar + "version2" + File.separatorChar + "v2changelog2.json"),
              convertText.apply("json" + File.separatorChar + "example-changelog.json"),
              convertText.apply("json" + File.separatorChar + "noContext-changelog.json"),
              convertText.apply("json" + File.separatorChar + "three-changelogs.json"),
              convertText.apply("json" + File.separatorChar + "utf8-changelog.json"),
              transformText.apply("json" + File.separatorChar + "nested-changelog.json"),
              transformText.apply("json" + File.separatorChar + "changelogs" + File.separatorChar + "changelog3.json"),
              firstExpectedIncludeFilesInfoMessage,
              " - " + CliTestUtils.loadResource(jsonContextPath) + File.separatorChar + "nested-changelog.json",
              " - " + CliTestUtils.loadResource(jsonContextPath) + File.separatorChar + "changelogs" + File.separatorChar + "changelog3.json",
              secondExpectedIncludeConversionMessage
          ),
          pFormat -> new String[]{"\"file\": \"changelogs/changelog1" + pFormat.getFileEnding() + "\",",
                                  "\"file\": \"changelogs/changelog2" + pFormat.getFileEnding() + "\",",
                                  "\"file\": \"changelogs/changelog3.json\","},
          pFormat -> new String[]{"\"file\": \"version2/v2changelog1" + pFormat.getFileEnding() + "\",",
                                  "\"file\": \"version2/v2changelog2" + pFormat.getFileEnding() + "\","}
      );

      return Stream.of(xml, yaml, json)
          .flatMap(pArgumentsForNestedChangelogs -> formatForIncludes.get()
              .filter(pFormat -> pFormat != pArgumentsForNestedChangelogs.baseFormat)
              .map(pFormat -> Arguments.of(pFormat, pArgumentsForNestedChangelogs)
              ));
    }

    /**
     * Tests that the converting of nested changelogs works.
     *
     * @param pFormat                       the format in which it should be converted
     * @param pArgumentsForNestedChangelogs contains the folder and the expected logs and content of the files
     */
    @ParameterizedTest
    @MethodSource
    void shouldConvertNestedChangelog(@NonNull Format pFormat, @NonNull ArgumentsForNestedChangelogs pArgumentsForNestedChangelogs)
    {
      assertCall(
          ExpectedCallResults.builder()
              .errorCode(0)
              .outTexts(pArgumentsForNestedChangelogs.outText)
              .additionalAsserts(List.of(
                  () -> {
                    Path nestedChangelog = outputDir.resolve("nested-changelog" + pArgumentsForNestedChangelogs.baseFormat.getFileEnding());
                    assertThat(nestedChangelog).as("nested changelog should exist").exists();
                    List<String> content = Files.readAllLines(nestedChangelog, StandardCharsets.UTF_8)
                        .stream().map(String::trim)
                        .collect(Collectors.toList());

                    assertThat(content).as("nested changelog")
                        .contains(pArgumentsForNestedChangelogs.nestedChangelogLines.apply(pFormat));
                  }, () -> {
                    Path changelog3 = outputDir.resolve("changelogs").resolve("changelog3" + pArgumentsForNestedChangelogs.baseFormat.getFileEnding());
                    assertThat(changelog3).as("changelog3 should exist").exists();

                    List<String> content = Files.readAllLines(changelog3, StandardCharsets.UTF_8)
                        .stream().map(String::trim)
                        .collect(Collectors.toList());

                    assertThat(content).as("changelog3 in subfolder")
                        .contains(pArgumentsForNestedChangelogs.changelog3Lines.apply(pFormat));
                  }))
              .build(),

          "convert", "--format", pFormat.name(), pArgumentsForNestedChangelogs.folder.toString(), outputDir.toFile().getAbsolutePath());
    }

    /**
     * The arguments for the nested changelogs.
     */
    @AllArgsConstructor
    class ArgumentsForNestedChangelogs
    {
      /**
       * The basic format in which all the files are.
       */
      @NonNull
      private Format baseFormat;

      /**
       * The path to the folder in which all the changelogs are located
       */
      @NonNull
      private Path folder;

      /**
       * The expected list of text written to {@code System.out}
       */
      @NonNull
      private List<String> outText;

      /**
       * Creates the expected lines that should be in the nested-changelog file.
       */
      @NonNull
      private Function<Format, String[]> nestedChangelogLines;

      /**
       * Creates the expected lines that should be in the changelog3 file.
       */
      @NonNull
      private Function<Format, String[]> changelog3Lines;

      @Override
      public String toString()
      {
        return "ArgumentsForNestedChangelogs{" + "baseFormat=" + baseFormat + '}';
      }
    }


    /**
     * Prepares the files for an include test
     *
     * @param pFormatOfIncludeFile the format of the include changelog file
     * @param pAdditionalFiles     the additional files
     * @return the prepared files
     */
    @SneakyThrows
    @NonNull
    private PreparedIncludes prepare(@NonNull Format pFormatOfIncludeFile, @NonNull List<Format> pAdditionalFiles)
    {

      Path input = outputDir.resolve("input");
      Files.createDirectories(input);

      Path include = getPathForFormat(pFormatOfIncludeFile, "include");
      Path newInclude = Files.copy(include, input.resolve(include.getFileName()));


      for (Format additionalFile : pAdditionalFiles)
      {
        Path additional = getPathForFormat(additionalFile);
        Files.copy(additional, input.resolve(additional.getFileName()));
      }


      return new PreparedIncludes(input, newInclude);
    }

    /**
     * The prepared files for the include tests.
     */
    @AllArgsConstructor
    class PreparedIncludes
    {
      /**
       * The input directory.
       */
      @NonNull
      private Path inputDirectory;

      /**
       * The include file.
       */
      @NonNull
      private Path includeFile;
    }

    /**
     * @return the arguments for {@link #shouldHandleIncludesCorrectly(Format, Format)}
     */
    private Stream<Arguments> shouldHandleIncludesCorrectly()
    {
      return formatForIncludes.get()
          .flatMap(pGivenFormat -> formatForIncludes.get()
              .filter(pToTransformFormat -> pToTransformFormat != pGivenFormat)
              .map(pToTransformFormat -> Arguments.of(pGivenFormat, pToTransformFormat)));
    }

    /**
     * Tests that the include elements will stay in the transformed elements.
     *
     * @param pGivenFormat       the given format
     * @param pToTransformFormat the format to which the files should be transformed
     */
    @ParameterizedTest
    @MethodSource
    void shouldHandleIncludesCorrectly(@NonNull Format pGivenFormat, @NonNull Format pToTransformFormat)
    {
      Path input = getPathForFormat(pGivenFormat, "include");

      Path expectedFile = outputDir.resolve(input.getFileName());

      assertCall(
          ExpectedCallResults.builder()
              .errorCode(0)
              .outText(transformText.apply(input.getFileName().toString()))
              .expectedFile(expectedFile)
              .additionalAssert(
                  () -> assertThat(assertDoesNotThrow(() -> Files.readString(expectedFile, StandardCharsets.UTF_8)))
                      .as("file should have some includes").contains("include"))
              .build(),

          "convert", "--format", pToTransformFormat.name(), input.toString(), outputDir.toFile().getAbsolutePath());
    }
  }


  /**
   * Contains various tests regarding the validations for the command.
   */
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class Validation
  {

    /**
     * @return the args for {@link #shouldGiveCorrectErrorMessages(String, String[])}
     */
    private @NonNull Stream<Arguments> shouldGiveCorrectErrorMessages()
    {
      Path path = new File(".").toPath();

      return Stream.of(
          Arguments.of("Missing required options and parameters: '--format=<format>', '<input>', '<output>'", new String[]{"convert"}),
          Arguments.of("Invalid value for option '--format': expected one of [SQL, YAML, XML, JSON] (case-sensitive) but was 'invalid'", new String[]{"convert", "-f", "invalid", path.toString(), path.toString()}),
          Arguments.of("Invalid value for positional parameter at index 0 (<input>): Specified file '/not/valid/dir' does not exist", new String[]{"convert", "-f", "YAML", "/not/valid/dir", path.toString()}),
          Arguments.of("Invalid value for positional parameter at index 1 (<output>): Specified file '/not/valid/dir' does not exist", new String[]{"convert", "-f", "YAML", path.toString(), "/not/valid/dir"}),
          Arguments.of("Unmatched argument at index 5: 'foo'", new String[]{"convert", "-f", "YAML", path.toString(), path.toString(), "foo"}),
          Arguments.of("Option '--database-type' is required, when format SQL is given", new String[]{"convert", "-f", "SQL", path.toString(), path.toString(),})
      );
    }

    /**
     * Tests that the correct error messages are given for invalid parameters.
     *
     * @param pExpectedMessage The error message that should be anywhere in the error output
     * @param pArgs            the arguments that should be passed to the cli tool.
     */
    @ParameterizedTest
    @MethodSource
    void shouldGiveCorrectErrorMessages(@NonNull String pExpectedMessage, String @NonNull [] pArgs)
    {
      assertCall(
          ExpectedCallResults.builder()
              .errorCode(2)
              .errText(pExpectedMessage)
              .build(),
          pArgs);
    }


    /**
     * Validates that the output directory can not be a file, but must be a directory.
     *
     * @param pTempDir the temporary directory, in which a temporary file will be created
     */
    @Test
    @SneakyThrows
    void shouldGiveCorrectErrorMessageForFileAtOutput(@TempDir @NonNull Path pTempDir)
    {
      Path file = pTempDir.resolve(Paths.get("myFile.txt"));
      Files.createFile(file);

      CallResults callResults = call("convert", "-f", "YAML", pTempDir.toString(), file.toString());

      assertAll(
          () -> assertEquals(2, callResults.getErrorCode(), "error code"),
          () -> assertThat(callResults.getErrText())
              .contains("Invalid value for positional parameter at index 1 (<output>): Specified file '" + file + "' is not an directory.")
      );
    }
  }


  /**
   * Gets the path to the test resource for one format.
   *
   * @param format the format for which the file should be given
   * @return the path to the changelog file
   */
  private @NonNull Path getPathForFormat(@NonNull Format format)
  {
    return getPathForFormat(format, null);
  }

  /**
   * Gets the path to the test resource for one format.
   *
   * @param pFormat the format for which the file should be given
   * @param pSuffix the suffix that should be added to the filename with a minus
   * @return the path to the changelog file
   */
  @SneakyThrows
  @NonNull
  private Path getPathForFormat(@NonNull Format pFormat, @Nullable String pSuffix)
  {
    String fileName = createFileName(pFormat, pSuffix);
    return CliTestUtils.loadResource("convert/" + fileName);
  }

  /**
   * Gets the file name for our changelog files of the resources.
   *
   * @param pFormat the format
   * @param pSuffix the suffix that should be added with a minus
   * @return the changelog name with the format ending
   */
  private @NonNull String createFileName(@NonNull Format pFormat, @Nullable String pSuffix)
  {
    return pFormat.name() + (pSuffix == null ? "" : "-" + pSuffix) + pFormat.getFileEnding();
  }


  /**
   * Assert that a given file is a valid liquibase file by calling {@code liquibase validate}.
   *
   * @param expectedFile the path to the expected file
   */
  @SneakyThrows
  private void assertValideFile(@NonNull Path expectedFile)
  {
    try (ResourceAccessor resourceAccessor = new DirectoryResourceAccessor(expectedFile.getParent()))
    {
      Database database = new MockDatabase();

      Map<String, Object> scopeObjects = new HashMap<>();
      scopeObjects.put(Scope.Attr.database.name(), database);
      scopeObjects.put(Scope.Attr.resourceAccessor.name(), resourceAccessor);
      // build the validate command that should be later run against the created file
      Scope.ScopedRunnerWithReturn<CommandResults> validate = () -> new CommandScope("validate")
          .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database)
          .addArgumentValue(DatabaseChangelogCommandStep.CHANGELOG_FILE_ARG, expectedFile.getFileName().toString())
          .addArgumentValue(DatabaseChangelogCommandStep.CHANGELOG_PARAMETERS, new ChangeLogParameters())
          .execute();

      // check that the validate command is executed successfully
      CommandResults results = Scope.child(scopeObjects, validate);
      assertEquals(0, results.getResult("statusCode"), "status of validate");
    }
  }


}