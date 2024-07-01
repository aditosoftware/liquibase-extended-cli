package de.adito.context;

import com.google.gson.Gson;
import de.adito.util.ExistingPathConverter;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.resource.DirectoryResourceAccessor;
import lombok.NoArgsConstructor;
import picocli.CommandLine.*;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.stream.*;

/**
 * CLI Command to resolve contexts
 *
 * @author r.hartinger, 31.01.2024
 */
@Command(name = "context", description = "Resolves the context from a root changelog and all depending changelogs",
    version = "1.0.0", mixinStandardHelpOptions = true)
@NoArgsConstructor
public class ContextResolver implements Callable<Integer>
{
  /**
   * The absolute path to the root changelog.
   */
  @Parameters(index = "0", arity = "1", description = "The absolute path to the changelog", converter = ExistingPathConverter.class)
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
      String contexts = liquibase.getDatabaseChangeLog().getChangeSets().stream()
          .flatMap(pChangeSet -> Stream.concat(pChangeSet.getContextFilter().getContexts().stream(),
                                               pChangeSet.getInheritableContextFilter().stream()
                                                   .flatMap(pContextExpr -> pContextExpr.getContexts().stream()))
          )
          // sort and distinct all contexts
          .sorted(String.CASE_INSENSITIVE_ORDER)
          .distinct()
          .collect(Collectors.collectingAndThen(Collectors.toList(), new Gson()::toJson));

      // System.out is needed to write to stdout, a logger would write to stderr
      System.out.println(contexts);
      return 0;
    }
  }
}
