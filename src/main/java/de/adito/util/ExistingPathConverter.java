package de.adito.util;

import picocli.CommandLine;
import picocli.CommandLine.TypeConversionException;

import java.nio.file.*;

/**
 * TypeConverter that validates, if the given string points to an <b>existing</b> element on the file system.
 * If not, a {@link TypeConversionException} gets thrown.
 *
 * @author r.hartinger, 19.06.2024
 */
public class ExistingPathConverter implements CommandLine.ITypeConverter<Path>
{
  @Override
  public Path convert(String value)
  {
    Path file = Path.of(value);
    if (!Files.exists(file))
      throw new TypeConversionException("Specified file '" + value + "' does not exist.");
    return file;
  }
}
