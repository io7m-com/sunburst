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


package com.io7m.sunburst.inventory;

import com.io7m.sunburst.inventory.api.SBInventoryConfiguration;
import com.io7m.sunburst.inventory.api.SBInventoryException;
import com.io7m.sunburst.inventory.api.SBInventoryFactoryType;
import com.io7m.sunburst.inventory.api.SBInventoryReadableType;
import com.io7m.sunburst.inventory.api.SBInventoryType;
import com.io7m.sunburst.inventory.internal.SBInventory;
import com.io7m.sunburst.inventory.internal.SBStrings;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * The default local inventory.
 */

public final class SBInventories implements SBInventoryFactoryType
{

  /**
   * The default local inventory.
   */

  public SBInventories()
  {

  }

  @Override
  public SBInventoryReadableType openReadOnly(
    final SBInventoryConfiguration configuration)
    throws SBInventoryException
  {
    try {
      return SBInventory.openReadOnly(
        new SBStrings(configuration.locale()),
        configuration
      );
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public SBInventoryType openReadWrite(
    final SBInventoryConfiguration configuration)
    throws SBInventoryException
  {
    try {
      return SBInventory.openReadWrite(
        new SBStrings(configuration.locale()),
        configuration
      );
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
