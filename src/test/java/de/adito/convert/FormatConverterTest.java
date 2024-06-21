package de.adito.convert;

import lombok.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.io.File;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import static de.adito.CliTestUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for {@link FormatConverter}.
 *
 * @author r.hartinger, 19.06.2024
 */
class FormatConverterTest
{

  private static final ClassLoader classLoader = FormatConverterTest.class.getClassLoader();
  private static final String packageName = FormatConverterTest.class.getPackageName().replace('.', '/');

  /**
   * Tests the method {@link FormatConverter#call()}.
   */
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class Call
  {

    @TempDir(cleanup = CleanupMode.ALWAYS)
    private Path outputDir;

    /**
     * Tests that an error during the converting will be handled correctly.
     */
    @Test
    @SneakyThrows
    void shouldHandleErrorDuringConverting()
    {
      String fileName = "invalid.xml";
      Path changelog = createFileInDirInOutputDir(fileName);

      CallResults callResults = call("convert", "--format", Format.YAML.name(), changelog.toString(), outputDir.toFile().getAbsolutePath());

      assertAll(
          () -> assertEquals(3, callResults.getErrorCode(), "error code: " + callResults.getErrText()),
          () -> assertThat(callResults.getOutText()).as("out text").contains("Converting changeset '" + fileName + "'"),
          () -> assertThat(callResults.getErrText()).as("err text").contains("error converting file '" + changelog + "' to format YAML"),
          () -> assertThat(outputDir.resolve(fileName)).as("file was copied").exists()
      );
    }


    /**
     * Tests that a file from with a not supported extension, will be copied to the new location.
     */
    @Test
    @SneakyThrows
    void shouldCopyInvalidFileExtensionToNewLocation()
    {
      String fileName = "notConverted.txt";
      Path txtFile = createFileInDirInOutputDir(fileName);

      CallResults callResults = call("convert", "--format", Format.YAML.name(), txtFile.toString(), outputDir.toFile().getAbsolutePath());

      assertAll(
          () -> assertEquals(0, callResults.getErrorCode(), "error code: " + callResults.getErrText()),
          () -> assertThat(callResults.getOutText()).as("out text").contains("Copying file '" + fileName + "' to new location"),
          () -> assertThat(callResults.getErrText()).as("err text").isEmpty(),
          () -> assertThat(outputDir.resolve(fileName)).as("file was copied").exists()
      );
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


    /**
     * Tests that a single xml file can be converted to yaml.
     */
    @Test
    @SneakyThrows
    void shouldConvertSingleFileFromXmlToYaml()
    {
      Path input = getPathForFormat(Format.XML);
      Path expected = getPathForFormat(Format.YAML);

      CallResults callResults = call("convert", "--format", Format.YAML.name(), input.toString(), outputDir.toFile().getAbsolutePath());

      try (Stream<Path> files = Files.list(outputDir))
      {
        assertAll(
            () -> assertEquals(0, callResults.getErrorCode(), "error code: " + callResults.getErrText()),
            () -> assertThat(callResults.getOutText()).as("out text").contains("Converting changeset 'XML.mariadb.xml'"),
            () -> assertThat(files.filter(pPath -> !Files.isDirectory(pPath)).collect(Collectors.toList()))
                .hasSize(1)
                .allSatisfy(pPath -> assertAll(
                    () -> assertThat(pPath).as("file name should be the same").exists().endsWith(Paths.get("XML.mariadb.yaml")),
                    () -> assertThat(pPath).as("content should be the same").hasSameTextualContentAs(expected))));
      }
    }


    /**
     * @return the arguments for {@link #shouldConvertFromEveryFormatToEveryFormat(Format, Path, String)}
     */
    @SneakyThrows
    private @NonNull Stream<Arguments> shouldConvertFromEveryFormatToEveryFormat()
    {
      Map<Format, Path> formats = new HashMap<>();

      // loading for every argument the path 
      Arrays.stream(Format.values()).forEach(pFormat -> formats.put(pFormat, getPathForFormat(pFormat)));

      // and creating the arguments
      return Arrays.stream(Format.values())
          .flatMap(pFormat -> formats.entrySet().stream()
              .map(pEntry -> Arguments.of(pFormat, pEntry.getValue(), pEntry.getKey().name() + ".mariadb" + pFormat.getFileEnding())));
    }

    /**
     * Tests that converting from every format to every format does work.
     *
     * @param pFormat           the format to which it should be converted
     * @param pInput            the given input file
     * @param pExpectedFileName the expected file name
     */
    @ParameterizedTest
    @MethodSource
    @SneakyThrows
    void shouldConvertFromEveryFormatToEveryFormat(@NonNull Format pFormat, @NonNull Path pInput, String pExpectedFileName)
    {
      CallResults callResults = call("convert", "--format", pFormat.name(), pInput.toString(), outputDir.toFile().getAbsolutePath());

      // check that programm was run without errors
      assertAll(
          () -> assertEquals(0, callResults.getErrorCode(), "error code" + callResults.getErrText()),
          () -> assertThat(callResults.getErrText()).as("error output").isEmpty(),
          () -> assertThat(callResults.getOutText()).as("out text").contains("Converting changeset '" + pInput.getFileName() + "'"),
          () -> assertThat(outputDir.resolve(pExpectedFileName)).as("new file should exist").exists()
      );
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
      URL url = classLoader.getResource(packageName);
      assertNotNull(url, "url should be there");
      Path inputForFiles = Paths.get(url.toURI());


      Path input = outputDir.resolve("input");
      Files.createDirectories(input);


      // creates an input dir with the four changelogs
      // we cannot use the inputForFiles directory, because it has class files, and we do not want to test this error case here
      Arrays.stream(Format.values())
          .forEach(pSingleFormat -> {
            Path pathForFormat = getPathForFormat(pSingleFormat).getFileName();
            assertDoesNotThrow(() -> Files.copy(inputForFiles.resolve(pathForFormat), input.resolve(pathForFormat)));
          });

      CallResults callResults = call("convert", "--format", pFormat.name(), input.toString(), outputDir.toFile().getAbsolutePath());

      try (Stream<Path> files = Files.list(outputDir))
      {
        assertAll(
            () -> assertEquals(0, callResults.getErrorCode(), "error code: " + callResults.getErrText()),
            () -> assertThat(callResults.getOutText()).contains("Converting changeset 'input" + File.separator + "JSON.mariadb.json'",
                                                                "Converting changeset 'input" + File.separator + "XML.mariadb.xml'",
                                                                "Converting changeset 'input" + File.separator + "YAML.mariadb.yaml'",
                                                                "Converting changeset 'input" + File.separator + "SQL.mariadb.sql'"),
            () -> assertThat(files.filter(pPath -> !Files.isDirectory(pPath)).collect(Collectors.toList()))
                .hasSize(Format.values().length)
                .allSatisfy(pPath -> assertThat(pPath).as("file name should be the same")
                    .exists()
                    .extracting(Path::toFile)
                    .extracting(File::getAbsolutePath)
                    .asString().endsWith(pFormat.getFileEnding())
                ));
      }
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
      Function<Format, String[]> createExpectedFiles = (pAnyFormat) ->
          new String[]{"changelogInInput.mariadb" + pAnyFormat.getFileEnding(), "a", "a/changelogInA.mariadb" + pAnyFormat.getFileEnding(),
                       "b", "b/c", "b/c/changelogInBC.mariadb" + pAnyFormat.getFileEnding()};


      // create file structure for tests
      Path output = outputDir.resolve(Paths.get("output"));
      Files.createDirectories(output);

      Path input = outputDir.resolve(Paths.get("input"));
      Files.createDirectories(input);

      Path basicChangelog = getPathForFormat(pGivenFormat);

      Files.copy(basicChangelog, input.resolve(Path.of("changelogInInput.mariadb" + pGivenFormat.getFileEnding())));

      Path subFolderA = input.resolve("a");
      Files.createDirectories(subFolderA);
      Files.copy(basicChangelog, subFolderA.resolve(Path.of("changelogInA.mariadb" + pGivenFormat.getFileEnding())));

      Path subFolderC = input.resolve(Path.of("b", "c"));
      Files.createDirectories(subFolderC);

      Files.copy(basicChangelog, subFolderC.resolve(Path.of("changelogInBC.mariadb" + pGivenFormat.getFileEnding())));

      // check if the files were created as expected for the test
      assertThat(getFilesInDirectory(input))
          .as("input should be created as expected")
          .containsExactlyInAnyOrder(createExpectedFiles.apply(pGivenFormat));


      CallResults callResults = call("convert", "--format", pTargetFormat.name(), input.toString(), output.toFile().getAbsolutePath());

      // validate that the output is as expected
      assertAll(
          () -> assertEquals(0, callResults.getErrorCode(), "error code: " + callResults.getErrText()),
          () -> assertThat(getFilesInDirectory(output))
              .as("output should be created as expected")
              .containsExactlyInAnyOrder(createExpectedFiles.apply(pTargetFormat)));
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
            .map(pRelativePath -> pRelativePath.replace('\\', '/'))
            .filter(Predicate.not(String::isBlank))
            .collect(Collectors.toSet());
      }
    }

    /**
     * Gets the path to the test resource for one format.
     *
     * @param format the format for which the file should be given
     * @return the path to the changelog file
     */
    @SneakyThrows
    @NonNull
    private Path getPathForFormat(@NonNull Format format)
    {
      String fileName = createFileName(format);
      URL url = classLoader.getResource(packageName + "/" + fileName);
      assertNotNull(url, "url for " + format + " should be there");
      return Paths.get(url.toURI());
    }

    /**
     * Gets the file name for our changelog files of the resources.
     *
     * @param format the format
     * @return the changelog name with the format ending
     */
    private @NonNull String createFileName(@NonNull Format format)
    {
      return format.name() + ".mariadb" + format.getFileEnding();
    }
  }


  /**
   * Contains various validations for the command.
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
          Arguments.of("Unmatched argument at index 5: 'foo'", new String[]{"convert", "-f", "YAML", path.toString(), path.toString(), "foo"})
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
    @SneakyThrows
    void shouldGiveCorrectErrorMessages(@NonNull String pExpectedMessage, String @NonNull [] pArgs)
    {
      CallResults callResults = call(pArgs);

      assertAll(
          () -> assertEquals(2, callResults.getErrorCode(), "error code"),
          () -> assertThat(callResults.getErrText()).contains(pExpectedMessage)
      );
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


}