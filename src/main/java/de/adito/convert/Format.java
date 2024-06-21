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
   * @param pExtension the given extension without a dot.
   * @return {@code true}, when the format is a valid format for converting, otherwise {@code false}
   */
  public static boolean isValidFormat(@NonNull String pExtension)
  {
    String normalizedExtension = "." + pExtension;
    return Arrays.stream(Format.values()).map(Format::getFileEnding).anyMatch(normalizedExtension::equals);
  }
}
