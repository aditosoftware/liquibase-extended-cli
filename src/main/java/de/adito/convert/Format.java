package de.adito.convert;

import lombok.*;

import java.util.Arrays;

/**
 * The formats that are supported for the converting.
 *
 * @author r.hartinger, 19.06.2024
 */
@AllArgsConstructor
@Getter
public enum Format
{
  SQL(".sql"),
  YAML(".yaml"),
  XML(".xml"),
  JSON(".json");

  /**
   * The file ending that new files should have.
   */
  private final @NonNull String fileEnding;


  /**
   * Checks if the given extension is a valid format.
   *
   * @param pExtension the given extension without a dot
   * @return {@code true}, when the format is a valid format for converting, otherwise {@code false}
   */
  public static boolean isValidFormat(@NonNull String pExtension)
  {
    String normalizedExtension = getNormalizedExtension(pExtension);
    return Arrays.stream(Format.values()).map(Format::getFileEnding).anyMatch(normalizedExtension::equals);
  }

  /**
   * Checks if the given extension is equals to the file ending.
   *
   * @param pExtension the given extension without a dot
   * @return {@code true}, if the file ending of the current format is equals to the extension.
   */
  public boolean isTargetFormat(@NonNull String pExtension)
  {
    return fileEnding.equals(getNormalizedExtension(pExtension));
  }

  /**
   * Normalizes the extension for this class.
   *
   * @param pExtension the given extension without a dot
   * @return the extension with a dot
   */
  private static @NonNull String getNormalizedExtension(@NonNull String pExtension)
  {
    return "." + pExtension;
  }
}
