package de.adito.context;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.resource.DirectoryResourceAccessor;
import picocli.CommandLine;

import java.nio.file.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.*;

/**
 * CLI Command to resolve contexts
 *
 * @author r.hartinger, 31.01.2024
 */
@CommandLine.Command(name = "context", mixinStandardHelpOptions = true)
public class ContextResolver implements Callable<Integer>
{
  @CommandLine.Parameters(index = "0", description = "The absolute path to the changelog")
  private String changelog;

  @Override
  public Integer call() throws Exception
  {
    // get the changelog file from the input
    Path changelogFile = Paths.get(changelog.trim());
    if (Files.notExists(changelogFile))
    {
      System.err.println("changelog file does not exist: " + changelogFile);
      return 10;
    }

    // and the parent file of the changelog file.
    // This is needed for the DirectoryResourceAccessor
    Path parent = changelogFile.getParent();

    // get the relative changelog to the parent for liquibase
    Path relativePathToChangelog = parent.relativize(changelogFile);

    try (Liquibase liquibase = new Liquibase(relativePathToChangelog.toString(), new DirectoryResourceAccessor(parent), (Database) null))
    {
      List<String> contexts = liquibase.getDatabaseChangeLog().getChangeSets().stream()
          .flatMap(pChangeSet -> Stream.concat(pChangeSet.getContextFilter().getContexts().stream(),
                                               pChangeSet.getInheritableContextFilter().stream()
                                                   .flatMap(pContextExpr -> pContextExpr.getContexts().stream()))
          )
          // sort and distinct all contexts
          .sorted(String.CASE_INSENSITIVE_ORDER)
          .distinct()
          .collect(Collectors.toList());

      System.out.println(contexts);
      return 0;
    }
  }

  public static void main(String... args)
  {
    System.exit(new CommandLine(new ContextResolver()).execute(args));
  }
}
