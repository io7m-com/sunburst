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

/**
 * Access to the local inventory.
 *
 * Multiple processes can open an inventory at any given time, but only one
 * process should write to it.
 */

public interface SBInventoryFactoryType
{
  /**
   * Open the inventory in read-only mode.
   *
   * @param configuration The configuration
   *
   * @return A readable inventory
   *
   * @throws SBInventoryException On errors
   */

  SBInventoryReadableType openReadOnly(
    SBInventoryConfiguration configuration)
    throws SBInventoryException;

  /**
   * Open the inventory in read-write mode.
   *
   * @param configuration The configuration
   *
   * @return An inventory
   *
   * @throws SBInventoryException On errors
   */

  SBInventoryType openReadWrite(
    SBInventoryConfiguration configuration)
    throws SBInventoryException;
}
