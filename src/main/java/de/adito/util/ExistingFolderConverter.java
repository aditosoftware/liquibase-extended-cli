package de.adito.util;

import picocli.CommandLine;

import java.nio.file.*;

/**
 * TypeConverter that validates, if the given string points to an <b>existing</b> directory and exists.
 * If not, a {@link CommandLine.TypeConversionException} gets thrown.
 *
 * @author r.hartinger, 20.06.2024
 */
public class ExistingFolderConverter extends ExistingPathConverter
{

  @Override
  public Path convert(String value)
  {
    // first, make any checks from the ExistingPathConverter
    Path validated = super.convert(value);
    // and then check if is a directory
    if (!Files.isDirectory(validated))
      throw new CommandLine.TypeConversionException("Specified file '" + value + "' is not an directory.");
    return validated;
  }
}
