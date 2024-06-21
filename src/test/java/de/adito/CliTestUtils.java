package de.adito;

import lombok.*;

import java.util.concurrent.atomic.*;

import static com.github.stefanbirkner.systemlambda.SystemLambda.*;

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
