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

import com.io7m.sunburst.model.SBBlob;
import com.io7m.sunburst.model.SBHash;
import com.io7m.sunburst.model.SBPackage;
import com.io7m.sunburst.model.SBPackageIdentifier;
import com.io7m.sunburst.model.SBPath;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The type of transactions that can be read.
 */

public interface SBTransactionReadableType
  extends SBTransactionCloseableType
{
  /**
   * Obtain the file associated with the blob in the given package.
   *
   * @param identifier The package identifier
   * @param path       The file path
   *
   * @return A file path
   *
   * @throws SBInventoryException On errors
   */

  Path blobFile(
    SBPackageIdentifier identifier,
    SBPath path)
    throws SBInventoryException;

  /**
   * @param time The comparison time
   *
   * @return The installed set of packages that have been updated since the
   * given time
   *
   * @throws SBInventoryException On errors
   */

  Set<SBPackageIdentifier> packagesUpdatedSince(
    OffsetDateTime time)
    throws SBInventoryException;

  /**
   * @return The installed set of packages
   *
   * @throws SBInventoryException On errors
   */

  Set<SBPackageIdentifier> packages()
    throws SBInventoryException;

  /**
   * @param identifier The package identifier
   *
   * @return The package with the given identifier, if one is installed
   *
   * @throws SBInventoryException On errors
   */

  Optional<SBPackage> packageGet(
    SBPackageIdentifier identifier)
    throws SBInventoryException;

  /**
   * Retrieve a blob.
   *
   * @param hash The hash value
   *
   * @return A blob, if one exists
   *
   * @throws SBInventoryException On errors
   */

  Optional<SBBlob> blobGet(SBHash hash)
    throws SBInventoryException;

  /**
   * List all available blobs.
   *
   * @return The available blobs
   *
   * @throws SBInventoryException On errors
   */

  Map<SBHash, SBBlob> blobList()
    throws SBInventoryException;

  /**
   * List all blobs that are not referenced by any packages.
   *
   * @return The unreferenced blobs
   *
   * @throws SBInventoryException On errors
   */

  Map<SBHash, SBBlob> blobsUnreferenced()
    throws SBInventoryException;
}
