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

import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;

/**
 * The type of transactions that can be read.
 */

public interface SBTransactionReadableType
  extends SBTransactionCloseableType
{
  SortedSet<SBPackageIdentifier> packages()
    throws SBInventoryException;

  Optional<SBPackage> packageGet(SBPackageIdentifier identifier)
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

  SortedMap<SBHash, SBBlob> blobList()
    throws SBInventoryException;
}
