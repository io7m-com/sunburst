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

package com.io7m.sunburst.inventory.internal;

import com.io7m.jmulticlose.core.CloseableCollection;
import com.io7m.jmulticlose.core.CloseableCollectionType;
import com.io7m.sunburst.inventory.api.SBInventoryConfiguration;
import com.io7m.sunburst.inventory.api.SBInventoryException;
import com.io7m.sunburst.inventory.api.SBInventoryReadableType;
import com.io7m.sunburst.inventory.api.SBInventoryType;
import com.io7m.sunburst.inventory.api.SBTransactionReadableType;
import com.io7m.sunburst.inventory.api.SBTransactionType;
import com.io7m.sunburst.inventory.datatypes.SBBlobDataType;
import com.io7m.sunburst.inventory.datatypes.SBHashDataType;
import com.io7m.sunburst.inventory.datatypes.SBPackageIdentifierDataType;
import com.io7m.sunburst.model.SBBlob;
import com.io7m.sunburst.model.SBHash;
import com.io7m.sunburst.model.SBPackage;
import com.io7m.sunburst.model.SBPackageIdentifier;
import com.io7m.sunburst.xml.SBPackageParserFactoryType;
import com.io7m.sunburst.xml.SBPackageSerializerFactoryType;
import org.h2.engine.IsolationLevel;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.tx.TransactionMap;
import org.h2.mvstore.tx.TransactionStore;
import org.h2.mvstore.type.ByteArrayDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_CLOSING;
import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_DATABASE;
import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_HASH_MISMATCH;
import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_PACKAGE_MISSING_BLOBS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * The default inventory.
 */

public final class SBInventory implements SBInventoryType
{
  private static final Logger LOG =
    LoggerFactory.getLogger(SBInventory.class);

  private final SBInventoryConfiguration configuration;
  private final Path dbFile;
  private final MVStore store;
  private final TransactionStore transactionStore;
  private final SBStrings strings;
  private final SBPackageParserFactoryType parsers;
  private final SBPackageSerializerFactoryType serializers;
  private final CloseableCollectionType<SBInventoryException> resources;
  private final HexFormat hexFormat;

  private SBInventory(
    final SBInventoryConfiguration inConfiguration,
    final SBStrings inStrings,
    final SBPackageParserFactoryType inParsers,
    final SBPackageSerializerFactoryType inSerializers,
    final CloseableCollectionType<SBInventoryException> inResources,
    final Path inDbFile,
    final MVStore inStore,
    final TransactionStore inTransactionStore)
  {
    this.configuration =
      Objects.requireNonNull(inConfiguration, "inConfiguration");
    this.strings =
      Objects.requireNonNull(inStrings, "inStrings");
    this.parsers =
      Objects.requireNonNull(inParsers, "parsers");
    this.serializers =
      Objects.requireNonNull(inSerializers, "serializers");
    this.resources =
      Objects.requireNonNull(inResources, "resources");
    this.dbFile =
      Objects.requireNonNull(inDbFile, "dbFile");
    this.store =
      Objects.requireNonNull(inStore, "store");
    this.transactionStore =
      Objects.requireNonNull(inTransactionStore, "transactionStore");
    this.hexFormat =
      HexFormat.of().withUpperCase();
  }

  /**
   * Open the inventory in read-only mode.
   *
   * @param strings       The string resources
   * @param parsers       The package parsers
   * @param serializers   The package serializers
   * @param configuration The configuration
   *
   * @return A readable inventory
   *
   * @throws SBInventoryException On errors
   */

  public static SBInventoryReadableType openReadOnly(
    final SBStrings strings,
    final SBPackageParserFactoryType parsers,
    final SBPackageSerializerFactoryType serializers,
    final SBInventoryConfiguration configuration)
    throws SBInventoryException
  {
    final var resources =
      CloseableCollection.create(
        () -> {
          return new SBInventoryException(
            ERROR_CLOSING,
            strings.format("errorClosing")
          );
        });

    final var dbFile =
      configuration.base()
        .resolve("sunburst.db")
        .toAbsolutePath();

    return openReadOnly(
      resources,
      configuration,
      strings,
      parsers,
      serializers,
      dbFile
    );
  }

  /**
   * Open the inventory in read-write mode.
   *
   * @param strings       The string resources
   * @param parsers       The package parsers
   * @param serializers   The package serializers
   * @param configuration The configuration
   *
   * @return An inventory
   *
   * @throws SBInventoryException On errors
   */

  public static SBInventoryType openReadWrite(
    final SBStrings strings,
    final SBPackageParserFactoryType parsers,
    final SBPackageSerializerFactoryType serializers,
    final SBInventoryConfiguration configuration)
    throws SBInventoryException
  {
    final var resources =
      CloseableCollection.create(
        () -> {
          return new SBInventoryException(
            ERROR_CLOSING,
            strings.format("errorClosing")
          );
        });

    final var dbFile =
      configuration.base()
        .resolve("sunburst.db")
        .toAbsolutePath();

    return openReadWrite(
      resources,
      configuration,
      strings,
      parsers,
      serializers,
      dbFile
    );
  }

  private static SBInventoryType openReadWrite(
    final CloseableCollectionType<SBInventoryException> resources,
    final SBInventoryConfiguration configuration,
    final SBStrings strings,
    final SBPackageParserFactoryType parsers,
    final SBPackageSerializerFactoryType serializers,
    final Path dbFile)
  {
    final var store =
      resources.add(
        new MVStore.Builder()
          .fileName(dbFile.toString())
          .autoCommitDisabled()
          .open()
      );

    final var transactions =
      new TransactionStore(store);

    return new SBInventory(
      configuration,
      strings,
      parsers,
      serializers,
      resources,
      dbFile,
      store,
      transactions
    );
  }

  private static SBInventoryType openReadOnly(
    final CloseableCollectionType<SBInventoryException> resources,
    final SBInventoryConfiguration configuration,
    final SBStrings strings,
    final SBPackageParserFactoryType parsers,
    final SBPackageSerializerFactoryType serializers,
    final Path dbFile)
  {
    final var store =
      resources.add(
        new MVStore.Builder()
          .fileName(dbFile.toString())
          .autoCommitDisabled()
          .readOnly()
          .open()
      );

    final var transactions =
      new TransactionStore(store);

    return new SBInventory(
      configuration,
      strings,
      parsers,
      serializers,
      resources,
      dbFile,
      store,
      transactions
    );
  }

  private static final TransactionStore.RollbackListener ROLLBACK_NO_LISTENER =
    (map, key, existingValue, restoredValue) -> {

    };

  @Override
  public SBTransactionType openTransaction()
  {
    final var tx =
      this.transactionStore.begin(
        ROLLBACK_NO_LISTENER,
        10,
        0,
        IsolationLevel.READ_COMMITTED
      );

    return new Transaction(this, tx);
  }

  @Override
  public SBTransactionReadableType openTransactionReadable()
  {
    return this.openTransaction();
  }

  @Override
  public void close()
    throws SBInventoryException
  {
    this.resources.close();
  }

  private static final class Transaction implements SBTransactionType
  {
    private static final URI TEMPORARY =
      URI.create("urn:tmp");

    private final SBInventory inventory;
    private final org.h2.mvstore.tx.Transaction tx;
    private final TransactionMap<SBHash, SBBlob> blobs;
    private final TransactionMap<SBPackageIdentifier, byte[]> packages;

    Transaction(
      final SBInventory inInventory,
      final org.h2.mvstore.tx.Transaction inTx)
    {
      this.inventory =
        Objects.requireNonNull(inInventory, "inventory");
      this.tx =
        Objects.requireNonNull(inTx, "tx");
      this.blobs =
        this.tx.openMap(
          "blobs",
          new SBHashDataType(),
          new SBBlobDataType()
        );
      this.packages =
        this.tx.openMap(
          "packages",
          new SBPackageIdentifierDataType(),
          ByteArrayDataType.INSTANCE
        );
    }

    @Override
    public void blobAdd(
      final SBBlob blob,
      final InputStream stream)
      throws SBInventoryException
    {
      final var hash =
        blob.hash();
      final var pathBase =
        this.inventory.blobPath(hash);
      final var pathBlob =
        pathBase.resolveSibling(pathBase.getFileName() + ".b");
      final var pathTmp =
        pathBase.resolveSibling(pathBase.getFileName() + ".t");
      final var pathLock =
        pathBase.resolveSibling(pathBase.getFileName() + ".l");

      try {
        Files.createDirectories(pathBlob.getParent());

        final var digest =
          MessageDigest.getInstance(
            hash.algorithm()
              .jssAlgorithmName()
          );

        final var fileOptions =
          new OpenOption[]{CREATE, WRITE, TRUNCATE_EXISTING};

        try (var lockChannel =
               FileChannel.open(pathLock, fileOptions)) {
          try (var ignored = lockChannel.lock()) {
            this.blobWriteLocked(
              blob,
              stream,
              hash,
              pathBlob,
              pathTmp,
              digest,
              fileOptions
            );
          } finally {
            Files.deleteIfExists(pathTmp);
          }
        }
      } catch (final Exception e) {
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
    }

    @Override
    public void packagePut(
      final SBPackage pack)
      throws SBInventoryException
    {
      this.verify(pack);

      try {
        final var output = new ByteArrayOutputStream();
        this.inventory.serializers.serialize(
          TEMPORARY, output, pack);
        this.inventory.parsers.parse(
          TEMPORARY, new ByteArrayInputStream(output.toByteArray()));

        this.packages.put(pack.identifier(), output.toByteArray());
      } catch (final Exception e) {
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
    }

    private void verify(
      final SBPackage pack)
      throws SBInventoryException
    {
      final var missing = new TreeSet<String>();
      for (final var entry : pack.entries().values()) {
        final var blob = entry.blob();
        final var hash = blob.hash();
        if (!this.blobs.containsKey(hash)) {
          missing.add("%s (%s)".formatted(hash, entry.path()));
        }
      }
      if (!missing.isEmpty()) {
        throw new SBInventoryException(
          ERROR_PACKAGE_MISSING_BLOBS,
          this.inventory.strings.format(
            "errorPackageMissingBlobs",
            pack.identifier(),
            missing
          )
        );
      }
    }

    private void blobWriteLocked(
      final SBBlob blob,
      final InputStream stream,
      final SBHash hash,
      final Path pathBlob,
      final Path pathTmp,
      final MessageDigest digest,
      final OpenOption[] fileOptions)
      throws IOException, SBInventoryException
    {
      try (var fileOut = Files.newOutputStream(pathTmp, fileOptions)) {
        try (var digestOut = new DigestOutputStream(fileOut, digest)) {
          stream.transferTo(digestOut);
          digestOut.flush();
        }

        final var hashReceived = digest.digest();
        final var hashExpected = hash.value();
        final var fmt = this.inventory.hexFormat;
        if (LOG.isTraceEnabled()) {
          LOG.trace("expected: {}", fmt.formatHex(hashExpected));
          LOG.trace("received: {}", fmt.formatHex(hashReceived));
        }

        if (!Arrays.equals(hashReceived, hashExpected)) {
          throw new SBInventoryException(
            ERROR_HASH_MISMATCH,
            this.inventory.strings.format(
              "errorHashMismatch",
              fmt.formatHex(hashExpected),
              fmt.formatHex(hashReceived)
            )
          );
        }

        Files.move(pathTmp, pathBlob, REPLACE_EXISTING, ATOMIC_MOVE);
        this.blobs.put(blob.hash(), blob);
      }
    }

    @Override
    public SortedSet<SBPackageIdentifier> packages()
      throws SBInventoryException
    {
      try {
        return new TreeSet<>(this.packages.keySet());
      } catch (final Exception e) {
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
    }

    @Override
    public Optional<SBPackage> packageGet(
      final SBPackageIdentifier identifier)
      throws SBInventoryException
    {
      try {
        final var data = this.packages.get(identifier);
        if (data == null) {
          return Optional.empty();
        }

        return Optional.of(
          this.inventory.parsers.parse(TEMPORARY, new ByteArrayInputStream(data))
        );
      } catch (final Exception e) {
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
    }

    @Override
    public Optional<SBBlob> blobGet(
      final SBHash hash)
      throws SBInventoryException
    {
      try {
        return Optional.ofNullable(this.blobs.get(hash));
      } catch (final Exception e) {
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
    }

    @Override
    public SortedMap<SBHash, SBBlob> blobList()
      throws SBInventoryException
    {
      try {
        return new TreeMap<>(this.blobs);
      } catch (final Exception e) {
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
    }

    @Override
    public void rollback()
      throws SBInventoryException
    {
      try {
        this.tx.rollback();
      } catch (final Exception e) {
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
    }

    @Override
    public void commit()
      throws SBInventoryException
    {
      try {
        this.tx.commit();
      } catch (final Exception e) {
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
    }

    @Override
    public void close()
      throws SBInventoryException
    {
      try {
        this.tx.rollback();
      } catch (final Exception e) {
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
    }
  }

  private Path blobPath(
    final SBHash hash)
  {
    final var name =
      this.hexFormat.formatHex(hash.value());
    final var start =
      name.substring(0, 2);
    final var end =
      name.substring(2);

    return this.configuration.base()
      .resolve("blob")
      .resolve(start)
      .resolve(end)
      .toAbsolutePath();
  }
}
