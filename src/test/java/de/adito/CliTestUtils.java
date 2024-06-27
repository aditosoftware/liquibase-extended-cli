package de.adito;

import lombok.*;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.function.Executable;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.*;

import static com.github.stefanbirkner.systemlambda.SystemLambda.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Util class for testing the CLI methods.
 *
 * @author r.hartinger, 20.06.2024
 */
@NoArgsConstructor
public class CliTestUtils
{

  /**
   * Calls the CLI with the given arguments.
   *
   * @param args the arguments
   * @return the captured CallResults with stdout, stderr and error code.
   * @see #assertCall(ExpectedCallResults, String...) for calling and asserts at the same time
   */
  @SneakyThrows
  @NonNull
  public static CallResults call(String... args)
  {
    AtomicReference<String> outText = new AtomicReference<>("");
    AtomicReference<String> errText = new AtomicReference<>("");
    AtomicInteger errorCode = new AtomicInteger(-1);


    errText.set(tapSystemErr(() -> outText.set(tapSystemOut(() -> errorCode.set(catchSystemExit(() -> LiquibaseExtendedCli.main(args)))))));

    return new CallResults(outText.get(), errText.get(), errorCode.get());
  }


  /**
   * Calls the CLI with the given arguments and asserts the {@link CallResults}.
   *
   * @param pExpectedCallResults the expected values from the calls
   * @param args                 the arguments
   * @see #call(String...) - for just calling without asserts
   */
  public static void assertCall(@NonNull CliTestUtils.ExpectedCallResults pExpectedCallResults, String @NonNull ... args)
  {
    // make the call
    CallResults callResults = call(args);

    List<Executable> asserts = new ArrayList<>(List.of(
        () -> assertEquals(pExpectedCallResults.errorCode, callResults.getErrorCode(), "error code: " + callResults.getErrText()),

        () -> {
          AbstractStringAssert<?> errText = assertThat(callResults.getErrText()).as("error output");

          if (pExpectedCallResults.errTexts.isEmpty())
            errText.isEmpty();
          else
            errText.contains(pExpectedCallResults.errTexts);
        },

        () -> {
          AbstractStringAssert<?> outText = assertThat(callResults.getOutText()).as("out text");

          if (pExpectedCallResults.outTexts.isEmpty())
            outText.isEmpty();
          else
            outText.contains(pExpectedCallResults.outTexts);
        }));

    // only add check for file existence, if files were given
    if (!pExpectedCallResults.expectedFiles.isEmpty())
      asserts.add(() -> assertThat(pExpectedCallResults.expectedFiles).allSatisfy(pPath -> assertThat(pPath).as("new file should exist").exists()));

    // and add the custom asserts
    asserts.addAll(pExpectedCallResults.additionalAsserts);


    // finally, assert everything
    assertAll(asserts);
  }


  /**
   * The expected call results.
   */
  @Builder
  public static class ExpectedCallResults
  {
    /**
     * The expected error code.
     */
    private int errorCode;

    /**
     * The expected error texts that are expected to be written to {@code System.err}
     * <p>
     * If there is no value given, then it will be checked if the error texts are empty.
     */
    @NonNull
    @Singular("errText")
    private Collection<String> errTexts;

    /**
     * The expected output texts that are expected to be written to {@code System.out}
     * <p>
     * If there is no value given, then it will be checked if the output texts are empty.
     */
    @NonNull
    @Singular("outText")
    private Collection<String> outTexts;

    /**
     * The expected files that should be checked for existence.
     * <p>
     * If there is no value given, then there will be no checks.
     */
    @NonNull
    @Singular("expectedFile")
    private Collection<Path> expectedFiles;

    /**
     * Additional asserts that should be executed next to the standard assets.
     */
    @NonNull
    @Singular("additionalAssert")
    private List<Executable> additionalAsserts;
  }


  /**
   * The results of the calling of the program.
   */
  @AllArgsConstructor
  @Getter
  public static class CallResults
  {
    /**
     * The text that was written to {@link System#out}
     */
    private String outText;

    /**
     * The text that was written to {@link System#err}
     */
    private String errText;

    /**
     * The error code that was returned by the program.
     */
    private int errorCode;
  }

}
