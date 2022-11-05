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


package com.io7m.sunburst.error_codes;

/**
 * Standard error codes.
 */

public final class SBErrorCodesStandard
{
  /**
   * An I/O error occurred.
   */

  public static final SBErrorCode ERROR_IO =
    new SBErrorCode("error-io");

  /**
   * One or more errors occurred whilst closing resources.
   */

  public static final SBErrorCode ERROR_CLOSING =
    new SBErrorCode("error-closing");

  /**
   * There was a database error.
   */

  public static final SBErrorCode ERROR_DATABASE =
    new SBErrorCode("error-db");

  /**
   * There was a database error caused by Trasco.
   */

  public static final SBErrorCode ERROR_DATABASE_TRASCO =
    new SBErrorCode("error-db-trasco");

  /**
   * A hash value did not match the expected value.
   */

  public static final SBErrorCode ERROR_HASH_MISMATCH =
    new SBErrorCode("error-hash-mismatch");

  /**
   * A package refers to blobs that are not in the database.
   */

  public static final SBErrorCode ERROR_PACKAGE_MISSING_BLOBS =
    new SBErrorCode("error-package-missing-blobs");

  /**
   * A package already exists.
   */

  public static final SBErrorCode ERROR_PACKAGE_DUPLICATE =
    new SBErrorCode("error-package-duplicate");

  /**
   * A blob cannot be deleted while a package refers to it.
   */

  public static final SBErrorCode ERROR_BLOB_REFERENCED =
    new SBErrorCode("error-blob-referenced");

  /**
   * No blob is associated with the given path.
   */

  public static final SBErrorCode ERROR_PATH_NONEXISTENT =
    new SBErrorCode("error-path-nonexistent");

  private SBErrorCodesStandard()
  {

  }
}
