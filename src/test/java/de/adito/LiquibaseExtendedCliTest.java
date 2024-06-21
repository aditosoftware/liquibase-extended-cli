package de.adito;

import de.adito.CliTestUtils.CallResults;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for {@link LiquibaseExtendedCli}.
 *
 * @author r.hartinger, 20.06.2024
 */
class LiquibaseExtendedCliTest
{
  /**
   * Tests that the command execution will fail when there is an invalid subcommand given
   */
  @Test
  void shouldFailWhenNoSubcommandGiven()
  {
    CallResults callResults = CliTestUtils.call();

    assertAll(
        () -> assertEquals(2, callResults.getErrorCode(), "errorCode"),
        () -> assertThat(callResults.getErrText()).as("errText")
            .contains("Missing required subcommand")
    );
  }
}
