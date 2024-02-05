package de.adito.context;

import com.google.gson.Gson;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.resource.DirectoryResourceAccessor;
import picocli.CommandLine;
import picocli.CommandLine.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.*;

/**
 * CLI Command to resolve contexts
 *
 * @author r.hartinger, 31.01.2024
 */
@Command(name = "context", mixinStandardHelpOptions = true)
public class ContextResolver implements Callable<Integer>
{
  @Parameters(index = "0", arity = "1", description = "The absolute path to the changelog")
  private Path changelogFile;

  @Override
  public Integer call() throws Exception
  {
    // get the parent file of the changelog file.
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


      // transform to json, so it can be parsed back
      String contextToTransfer = new Gson().toJson(contexts);
      System.out.println(contextToTransfer);
      return 0;
    }
  }

  public static void main(String... args)
  {
    int exitCode = new CommandLine(new ContextResolver()).execute(args);
    System.exit(exitCode);
  }
}
