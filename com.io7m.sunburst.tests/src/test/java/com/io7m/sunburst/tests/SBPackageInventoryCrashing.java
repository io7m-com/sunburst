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


package com.io7m.sunburst.tests;

import com.io7m.sunburst.inventory.api.SBInventoryConfiguration;
import com.io7m.sunburst.inventory.api.SBInventoryException;
import com.io7m.sunburst.inventory.api.SBInventoryType;
import com.io7m.sunburst.inventory.api.SBTransactionReadableType;
import com.io7m.sunburst.inventory.api.SBTransactionType;
import com.io7m.sunburst.model.SBBlob;
import com.io7m.sunburst.model.SBHash;
import com.io7m.sunburst.model.SBPackage;
import com.io7m.sunburst.model.SBPackageIdentifier;
import com.io7m.sunburst.model.SBPath;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_IO;

public final class SBPackageInventoryCrashing
  implements SBInventoryType
{
  private final EnumSet<CrashOn> crashOn;

  public enum CrashOn
  {
    CRASH_ON_TRANSACTION_CLOSE,
    CRASH_ON_CLOSE,
    CRASH_ON_FILE_OPEN
  }

  public SBPackageInventoryCrashing(
    final EnumSet<CrashOn> inCrashOn)
  {
    this.crashOn = Objects.requireNonNull(inCrashOn, "crashOn");
  }

  @Override
  public SBInventoryConfiguration configuration()
  {
    return new SBInventoryConfiguration(
      Locale.ROOT,
      Paths.get("")
    );
  }

  @Override
  public SBTransactionReadableType openTransactionReadable()
    throws SBInventoryException
  {
    return new CrashingTransaction();
  }

  @Override
  public void close()
    throws SBInventoryException
  {
    if (this.crashOn.contains(CrashOn.CRASH_ON_CLOSE)) {
      throw new SBInventoryException(ERROR_IO, "IO ERROR!");
    }
  }

  @Override
  public SBTransactionType openTransaction()
    throws SBInventoryException
  {
    return new CrashingTransaction();
  }

  private final class CrashingTransaction implements SBTransactionType
  {
    CrashingTransaction()
    {

    }

    @Override
    public void close()
      throws SBInventoryException
    {
      final var crashes = SBPackageInventoryCrashing.this.crashOn;
      if (crashes.contains(CrashOn.CRASH_ON_TRANSACTION_CLOSE)) {
        throw new SBInventoryException(ERROR_IO, "IO ERROR!");
      }
    }

    @Override
    public Path blobFile(
      final SBPackageIdentifier identifier,
      final SBPath path)
      throws SBInventoryException
    {
      final var crashes = SBPackageInventoryCrashing.this.crashOn;
      if (crashes.contains(CrashOn.CRASH_ON_FILE_OPEN)) {
        throw new SBInventoryException(ERROR_IO, "IO ERROR!");
      }
      return Paths.get("");
    }

    @Override
    public Set<SBPackageIdentifier> packagesUpdatedSince(
      final OffsetDateTime time)
      throws SBInventoryException
    {
      throw new SBInventoryException(ERROR_IO, "IO ERROR!");
    }

    @Override
    public Set<SBPackageIdentifier> packages()
      throws SBInventoryException
    {
      throw new SBInventoryException(ERROR_IO, "IO ERROR!");
    }

    @Override
    public Optional<SBPackage> packageGet(
      final SBPackageIdentifier identifier)
      throws SBInventoryException
    {
      throw new SBInventoryException(ERROR_IO, "IO ERROR!");
    }

    @Override
    public Optional<SBBlob> blobGet(
      final SBHash hash)
      throws SBInventoryException
    {
      throw new SBInventoryException(ERROR_IO, "IO ERROR!");
    }

    @Override
    public Map<SBHash, SBBlob> blobList()
      throws SBInventoryException
    {
      throw new SBInventoryException(ERROR_IO, "IO ERROR!");
    }

    @Override
    public Map<SBHash, SBBlob> blobsUnreferenced()
      throws SBInventoryException
    {
      throw new SBInventoryException(ERROR_IO, "IO ERROR!");
    }

    @Override
    public void rollback()
      throws SBInventoryException
    {

    }

    @Override
    public void commit()
      throws SBInventoryException
    {

    }

    @Override
    public void blobAdd(
      final SBBlob blob,
      final InputStream stream)
      throws SBInventoryException
    {
      throw new SBInventoryException(ERROR_IO, "IO ERROR!");
    }

    @Override
    public void blobRemove(
      final SBBlob blob)
      throws SBInventoryException
    {
      throw new SBInventoryException(ERROR_IO, "IO ERROR!");
    }

    @Override
    public void packagePut(
      final SBPackage pack)
      throws SBInventoryException
    {
      throw new SBInventoryException(ERROR_IO, "IO ERROR!");
    }
  }
}
