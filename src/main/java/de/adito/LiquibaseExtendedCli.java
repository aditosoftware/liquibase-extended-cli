package de.adito;

import de.adito.context.ContextResolver;
import de.adito.convert.FormatConverter;
import picocli.CommandLine;

/**
 * The basic command and entry point for all CLI commands.
 *
 * @author r.hartinger, 19.06.2024
 */
@CommandLine.Command(name = "LiquibaseExtendedCli",
    description = "Executes some util commands in regards of Liquibase",
    version = "1.0.0",
    mixinStandardHelpOptions = true,
    subcommands = {
        ContextResolver.class,
        FormatConverter.class
    })
public class LiquibaseExtendedCli implements Runnable
{

  @CommandLine.Spec
  CommandLine.Model.CommandSpec spec;

  @Override
  public void run()
  {
    throw new CommandLine.ParameterException(spec.commandLine(), "Missing required subcommand.");
  }

  /**
   * Entry-Point to all CLI commands
   *
   * @param args the arguments passed on the command line. This includes the command name, parameters and options
   */
  public static void main(String... args)
  {
    int exitCode = new CommandLine(new LiquibaseExtendedCli()).execute(args);
    System.exit(exitCode);
  }

}
