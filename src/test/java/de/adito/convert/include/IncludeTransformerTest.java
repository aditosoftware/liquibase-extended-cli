package de.adito.convert.include;

import lombok.SneakyThrows;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;


/**
 * Test class for {@link IncludeTransformer}.
 * <p>
 * This only contains cases that are not covered by a normal program flow
 *
 * @author r.hartinger, 01.07.2024
 */
class IncludeTransformerTest
{
  
  /**
   * Tests the method {@link IncludeTransformer#transformIncludes(Path, Path, Path)}.
   */
  @Nested
  class TransformIncludes
  {

    private @TempDir Path tempDir;

    /**
     * Tests that a file with an extension that has to transformer does not create anything and does not produce any error.
     * <p>
     * This case can not happen in the normal program flow, because all files that are transformed, are checked before, if an include is there.
     * If there is no transformer (which happens in our tests case), then the include check returns {@code false} and we are not transforming this file.
     */
    @Test
    @SneakyThrows
    void shouldWorkWithNotExistingTransformer()
    {
      IncludeTransformer includeTransformer = new IncludeTransformer();

      assertDoesNotThrow(() -> includeTransformer.transformIncludes(tempDir, tempDir.resolve("foo.sql"), tempDir.resolve("foo.xml")));

      assertThat(tempDir).isEmptyDirectory();
    }
  }


}