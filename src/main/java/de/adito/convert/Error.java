package de.adito.convert;

import lombok.*;

/**
 * Information about any error that can happen during the converting.
 *
 * @author r.hartinger, 28.06.2024
 */
@AllArgsConstructor
@Getter
public enum Error
{
  HANDLING_INCLUDES("Error while handling includes:", "These file(s) were copied to the new location."),
  CONVERTING_FILES("Error while converting files:", "These file(s) were copied to the new location."),
  COPYING_FILES("Error while copying files:", "These file(s) were NOT copied to the new location.");


  /**
   * The text what went wrong.
   */
  @NonNull
  private final String errText;

  /**
   * Information whether the files were copied or not.
   */
  @NonNull
  private final String copyText;
}
