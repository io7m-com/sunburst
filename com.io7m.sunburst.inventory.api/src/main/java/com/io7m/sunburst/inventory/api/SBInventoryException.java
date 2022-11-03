/*
 * Copyright © 2022 Mark Raynsford <code@io7m.com> https://www.io7m.com
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */


package com.io7m.sunburst.inventory.api;

import com.io7m.sunburst.error_codes.SBErrorCode;
import com.io7m.sunburst.error_codes.SBException;

/**
 * The type of inventory exceptions.
 */

public final class SBInventoryException extends SBException
{
  /**
   * Construct an exception.
   *
   * @param inErrorCode The error code
   * @param message     The error message
   */

  public SBInventoryException(
    final SBErrorCode inErrorCode,
    final String message)
  {
    super(inErrorCode, message);
  }

  /**
   * Construct an exception.
   *
   * @param inErrorCode The error code
   * @param message     The error message
   * @param cause       The cause
   */

  public SBInventoryException(
    final SBErrorCode inErrorCode,
    final String message,
    final Throwable cause)
  {
    super(inErrorCode, message, cause);
  }

  /**
   * Construct an exception.
   *
   * @param inErrorCode The error code
   * @param cause       The cause
   */

  public SBInventoryException(
    final SBErrorCode inErrorCode,
    final Throwable cause)
  {
    super(inErrorCode, cause);
  }
}
