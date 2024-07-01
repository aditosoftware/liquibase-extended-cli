package de.adito.context;

import com.google.gson.Gson;
import de.adito.CliTestUtils;
import de.adito.CliTestUtils.CallResults;
import lombok.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for {@link ContextResolver}.
 *
 * @author r.hartinger, 01.02.2024
 */
class ContextResolverTest
{

  /**
   * Tests that the resolve of any context works.
   */
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class ResolveContext
  {

    /**
     * @return the arguments for {@link #shouldResolveContext(String, List, String)}
     */
    private Stream<Arguments> shouldResolveContext()
    {
      Map<String, List<String>> filesWithContexts = Map.of(
          "noContext-changelog", List.of(),
          "example-changelog", List.of("context"),
          "utf8-changelog", List.of("âèù", "äöüß", "🐱‍🚀"),
          "three-changelogs", List.of("bar", "baz", "foo"),
          "nested-changelog", List.of("development", "junit", "production", "special", "special_version2", "testing", "v1", "v2", "version1", "version2")
      );

      ClassLoader classLoader = ContextResolverTest.class.getClassLoader();
      String packageName = ContextResolverTest.class.getPackageName().replace('.', '/');

      URL context = classLoader.getResource(packageName);
      assertNotNull(context, "context");

      URL json = classLoader.getResource(packageName + "/json");
      assertNotNull(json, "json");

      URL sql = classLoader.getResource(packageName + "/sql");
      assertNotNull(sql, "sql");


      URL xml = classLoader.getResource(packageName + "/xml");
      assertNotNull(xml, "xml");

      URL yaml = classLoader.getResource(packageName + "/yaml");
      assertNotNull(yaml, "yaml");

      return Stream.of(json, sql, xml, yaml)
          .map(URL::getFile)
          .map(File::new)
          .flatMap(pFile -> filesWithContexts.entrySet()
              .stream()
              .map(pEntry -> {

                AtomicReference<String> ending = new AtomicReference<>(pFile.getName());


                String description = ending + "-" + pEntry.getKey();

                List<String> expected = pEntry.getValue()
                    .stream().map(pContext -> ending.get() + "-" + pContext)
                    .collect(Collectors.toList());

                if (pEntry.getKey().equals("nested-changelog") && ending.get().equals("sql"))
                {
                  // sql nested changelogs cannot be in sql format, therefore the root changelog was written in xml for the test
                  ending.set("xml");
                }

                return Arguments.of(description, expected, pFile.getAbsolutePath() + "/" + pEntry.getKey() + "." + ending.get());
              }));
    }

    /**
     * Tests that the contexts can be resolved from any changelog file. It will listen to the output made by the command call
     *
     * @param pDisplayName   the display name of the test
     * @param pResult        the result of the method, given as an array
     * @param pChangelogPath the absolute path to the root changelog file
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource
    @SneakyThrows
    void shouldResolveContext(@NonNull String pDisplayName, @NonNull List<String> pResult, @NonNull String pChangelogPath)
    {
      CallResults callResults = CliTestUtils.call("context", pChangelogPath);

      String[] actual = new Gson().fromJson(callResults.getOutText().trim(), String[].class);

      assertAll(
          () -> assertEquals(0, callResults.getErrorCode(), pDisplayName + ": errorCode"),
          () -> assertArrayEquals(pResult.toArray(String[]::new), actual, pDisplayName + ": out message")
      );
    }
  }

  /**
   * Tests the various exit codes from the context command.
   */
  @Nested
  class ExitCodeOfResolveContext
  {

    /**
     * Checks that exit code 2 will be returned, when no parameter was given.
     */
    @Test
    @SneakyThrows
    void shouldReturnExitCode2WhenNoParameterGiven()
    {
      CallResults callResults = CliTestUtils.call("context");

      assertEquals(2, callResults.getErrorCode(), callResults.getErrText());
    }

    /**
     * Checks that exit code 1 will be returned, when no valid path was given.
     */
    @Test
    @SneakyThrows
    void shouldReturnExitCode1WhenNoValidFile()
    {
      CallResults callResults = CliTestUtils.call("context", "no_valid_file");

      assertAll(
          () -> assertEquals(2, callResults.getErrorCode(), callResults.getErrText()),
          () -> assertThat(callResults.getErrText()).contains("Specified file 'no_valid_file' does not exist")
      );
    }
  }

}
