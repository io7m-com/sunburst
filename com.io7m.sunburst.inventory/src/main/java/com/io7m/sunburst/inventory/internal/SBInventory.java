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

import com.io7m.anethum.common.ParseException;
import com.io7m.jdeferthrow.core.ExceptionTracker;
import com.io7m.jmulticlose.core.CloseableCollection;
import com.io7m.jmulticlose.core.CloseableCollectionType;
import com.io7m.sunburst.inventory.api.SBInventoryConfiguration;
import com.io7m.sunburst.inventory.api.SBInventoryException;
import com.io7m.sunburst.inventory.api.SBInventoryReadableType;
import com.io7m.sunburst.inventory.api.SBInventoryType;
import com.io7m.sunburst.inventory.api.SBTransactionReadableType;
import com.io7m.sunburst.inventory.api.SBTransactionType;
import com.io7m.sunburst.model.SBBlob;
import com.io7m.sunburst.model.SBHash;
import com.io7m.sunburst.model.SBHashAlgorithm;
import com.io7m.sunburst.model.SBPackage;
import com.io7m.sunburst.model.SBPackageEntry;
import com.io7m.sunburst.model.SBPackageIdentifier;
import com.io7m.sunburst.model.SBPath;
import com.io7m.trasco.api.TrException;
import com.io7m.trasco.api.TrExecutorConfiguration;
import com.io7m.trasco.api.TrSchemaRevisionSet;
import com.io7m.trasco.vanilla.TrExecutors;
import com.io7m.trasco.vanilla.TrSchemaRevisionSetParsers;
import com.io7m.verona.core.Version;
import com.io7m.verona.core.VersionQualifier;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.Record5;
import org.jooq.Record6;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_BLOB_REFERENCED;
import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_CLOSING;
import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_DATABASE;
import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_DATABASE_TRASCO;
import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_HASH_MISMATCH;
import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_IO;
import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_PACKAGE_DUPLICATE;
import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_PACKAGE_MISSING_BLOBS;
import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_PATH_NONEXISTENT;
import static com.io7m.sunburst.inventory.internal.Tables.BLOBS;
import static com.io7m.sunburst.inventory.internal.Tables.PACKAGES;
import static com.io7m.sunburst.inventory.internal.Tables.PACKAGE_BLOBS;
import static com.io7m.sunburst.inventory.internal.Tables.PACKAGE_META;
import static com.io7m.trasco.api.TrExecutorUpgrade.FAIL_INSTEAD_OF_UPGRADING;
import static com.io7m.trasco.api.TrExecutorUpgrade.PERFORM_UPGRADES;
import static java.lang.Integer.toUnsignedLong;
import static java.math.BigInteger.valueOf;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static org.jooq.SQLDialect.SQLITE;
import static org.sqlite.SQLiteErrorCode.SQLITE_CONSTRAINT_FOREIGNKEY;

/**
 * The default inventory.
 */

public final class SBInventory implements SBInventoryType
{
  private static final Logger LOG =
    LoggerFactory.getLogger(SBInventory.class);

  private final SBInventoryConfiguration configuration;
  private final Path dbFile;
  private final SQLiteDataSource dataSource;
  private final SBStrings strings;
  private final CloseableCollectionType<SBInventoryException> resources;
  private final HexFormat hexFormat;

  private SBInventory(
    final SBInventoryConfiguration inConfiguration,
    final SBStrings inStrings,
    final CloseableCollectionType<SBInventoryException> inResources,
    final Path inDbFile,
    final SQLiteDataSource inDataSource)
  {
    this.configuration =
      Objects.requireNonNull(inConfiguration, "inConfiguration");
    this.strings =
      Objects.requireNonNull(inStrings, "inStrings");
    this.resources =
      Objects.requireNonNull(inResources, "resources");
    this.dbFile =
      Objects.requireNonNull(inDbFile, "dbFile");
    this.dataSource =
      Objects.requireNonNull(inDataSource, "dataSource");
    this.hexFormat =
      HexFormat.of().withUpperCase();
  }

  /**
   * Open the inventory in read-only mode.
   *
   * @param strings       The string resources
   * @param configuration The configuration
   *
   * @return A readable inventory
   *
   * @throws SBInventoryException On errors
   */

  public static SBInventoryReadableType openReadOnly(
    final SBStrings strings,
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
      dbFile
    );
  }

  /**
   * Open the inventory in read-write mode.
   *
   * @param strings       The string resources
   * @param configuration The configuration
   *
   * @return An inventory
   *
   * @throws SBInventoryException On errors
   */

  public static SBInventoryType openReadWrite(
    final SBStrings strings,
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
      dbFile
    );
  }

  private static SBInventoryType openReadWrite(
    final CloseableCollectionType<SBInventoryException> resources,
    final SBInventoryConfiguration configuration,
    final SBStrings strings,
    final Path dbFile)
    throws SBInventoryException
  {
    try {
      final var sqlParsers = new TrSchemaRevisionSetParsers();
      final TrSchemaRevisionSet revisions;
      try (var stream = SBInventory.class.getResourceAsStream(
        "/com/io7m/sunburst/inventory/database.xml")) {
        revisions = sqlParsers.parse(URI.create("urn:source"), stream);
      }

      final var url = new StringBuilder(128);
      url.append("jdbc:sqlite:");
      url.append(dbFile);

      final var config = new SQLiteConfig();
      config.enforceForeignKeys(true);

      final var dataSource = new SQLiteDataSource(config);
      dataSource.setUrl(url.toString());

      try (var connection = dataSource.getConnection()) {
        connection.setAutoCommit(false);
        new TrExecutors().create(
          new TrExecutorConfiguration(
            SBInventory::schemaVersionGet,
            SBInventory::schemaVersionSet,
            event -> {

            },
            revisions,
            PERFORM_UPGRADES,
            connection
          )
        ).execute();
        connection.commit();
      }

      return new SBInventory(
        configuration,
        strings,
        resources,
        dbFile,
        dataSource
      );
    } catch (final IOException e) {
      throw new SBInventoryException(ERROR_IO, e.getMessage(), e);
    } catch (final TrException e) {
      throw new SBInventoryException(ERROR_DATABASE_TRASCO, e.getMessage(), e);
    } catch (final ParseException | SQLException e) {
      throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
    }
  }

  private static void schemaVersionSet(
    final BigInteger version,
    final Connection connection)
    throws SQLException
  {
    final String statementText;
    if (Objects.equals(version, BigInteger.ZERO)) {
      statementText = "insert into schema_version (version_number) values (?)";
    } else {
      statementText = "update schema_version set version_number = ?";
    }

    try (var statement = connection.prepareStatement(statementText)) {
      statement.setLong(1, version.longValueExact());
      statement.execute();
    }
  }

  private static Optional<BigInteger> schemaVersionGet(
    final Connection connection)
    throws SQLException
  {
    Objects.requireNonNull(connection, "connection");

    try {
      final var statementText = "SELECT version_number FROM schema_version";
      LOG.debug("execute: {}", statementText);

      try (var statement = connection.prepareStatement(statementText)) {
        try (var result = statement.executeQuery()) {
          if (!result.next()) {
            throw new SQLException("schema_version table is empty!");
          }
          return Optional.of(valueOf(result.getLong(1)));
        }
      }
    } catch (final SQLException e) {
      if (e.getErrorCode() == SQLiteErrorCode.SQLITE_ERROR.code) {
        connection.rollback();
        return Optional.empty();
      }
      throw e;
    }
  }

  private static SBInventoryType openReadOnly(
    final CloseableCollectionType<SBInventoryException> resources,
    final SBInventoryConfiguration configuration,
    final SBStrings strings,
    final Path dbFile)
    throws SBInventoryException
  {
    try {
      final var sqlParsers = new TrSchemaRevisionSetParsers();
      final TrSchemaRevisionSet revisions;
      try (var stream = SBInventory.class.getResourceAsStream(
        "/com/io7m/sunburst/inventory/database.xml")) {
        revisions = sqlParsers.parse(URI.create("urn:source"), stream);
      }

      final var url = new StringBuilder(128);
      url.append("jdbc:sqlite:");
      url.append(dbFile);

      final var config = new SQLiteConfig();
      config.setReadOnly(true);

      final var dataSource = new SQLiteDataSource(config);
      dataSource.setUrl(url.toString());

      try (var connection = dataSource.getConnection()) {
        connection.setAutoCommit(false);
        new TrExecutors().create(
          new TrExecutorConfiguration(
            SBInventory::schemaVersionGet,
            SBInventory::schemaVersionSet,
            event -> {

            },
            revisions,
            FAIL_INSTEAD_OF_UPGRADING,
            connection
          )
        ).execute();
      }

      return new SBInventory(
        configuration,
        strings,
        resources,
        dbFile,
        dataSource
      );
    } catch (final IOException e) {
      throw new SBInventoryException(ERROR_IO, e.getMessage(), e);
    } catch (final TrException e) {
      throw new SBInventoryException(ERROR_DATABASE_TRASCO, e.getMessage(), e);
    } catch (final ParseException | SQLException e) {
      throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
    }
  }

  @Override
  public SBTransactionType openTransaction()
    throws SBInventoryException
  {
    final Connection connection;
    try {
      connection = this.dataSource.getConnection();
      connection.setAutoCommit(false);
    } catch (final SQLException e) {
      throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
    }
    return new Transaction(this, connection);
  }

  @Override
  public SBInventoryConfiguration configuration()
  {
    return this.configuration;
  }

  @Override
  public SBTransactionReadableType openTransactionReadable()
    throws SBInventoryException
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
    private final SBInventory inventory;
    private final Connection connection;

    Transaction(
      final SBInventory inInventory,
      final Connection inConnection)
    {
      this.inventory =
        Objects.requireNonNull(inInventory, "inventory");
      this.connection =
        Objects.requireNonNull(inConnection, "connection");
    }

    @Override
    public void close()
      throws SBInventoryException
    {
      final var exceptions =
        new ExceptionTracker<SBInventoryException>();

      try {
        this.connection.rollback();
      } catch (final SQLException e) {
        exceptions.addException(
          new SBInventoryException(ERROR_DATABASE, e.getMessage(), e));
      }

      try {
        this.connection.close();
      } catch (final SQLException e) {
        exceptions.addException(
          new SBInventoryException(ERROR_DATABASE, e.getMessage(), e));
      }

      exceptions.throwIfNecessary();
    }

    @Override
    public Path blobFile(
      final SBPackageIdentifier identifier,
      final SBPath path)
      throws SBInventoryException
    {
      final var context = this.createContext();

      try {
        final var tables =
          BLOBS.join(PACKAGE_BLOBS).on(PACKAGE_BLOBS.BLOB_ID.eq(BLOBS.ID))
            .join(PACKAGES).on(PACKAGES.ID.eq(PACKAGE_BLOBS.PACKAGE_ID));

        final var conditions =
          DSL.and(
            packageMatches(identifier),
            PACKAGE_BLOBS.PATH.eq(path.toString())
          );

        final var hashRecord =
          context.select(BLOBS.HASH_ALGORITHM, BLOBS.HASH)
            .from(tables)
            .where(conditions)
            .fetchOptional()
            .orElseThrow(() -> {
              return new SBInventoryException(
                ERROR_PATH_NONEXISTENT,
                this.inventory.strings.format(
                  "errorPathNonexistent", identifier, path)
              );
            });

        final var pathBase =
          this.inventory.blobPath(
            new SBHash(
              SBHashAlgorithm.ofJSSName(hashRecord.get(BLOBS.HASH_ALGORITHM)),
              this.inventory.hexFormat.parseHex(hashRecord.get(BLOBS.HASH))
            )
          );

        return pathBase.resolveSibling(pathBase.getFileName() + ".b");
      } catch (final DataAccessException e) {
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
    }

    @Override
    public Set<SBPackageIdentifier> packagesUpdatedSince(
      final OffsetDateTime time)
      throws SBInventoryException
    {
      final var context = this.createContext();

      try {
        return context.select(
            PACKAGES.ID,
            PACKAGES.NAME,
            PACKAGES.VERSION_MAJOR,
            PACKAGES.VERSION_MINOR,
            PACKAGES.VERSION_PATCH,
            PACKAGES.VERSION_QUALIFIER
          )
          .from(PACKAGES)
          .where(PACKAGES.UPDATED.greaterThan(time.toString()))
          .orderBy(PACKAGES.ID)
          .stream()
          .map(Transaction::mapPackageIdentifier)
          .collect(Collectors.toUnmodifiableSet());
      } catch (final DataAccessException e) {
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
    }

    @Override
    public Set<SBPackageIdentifier> packages()
      throws SBInventoryException
    {
      final var context = this.createContext();

      try {
        return context.select(
            PACKAGES.ID,
            PACKAGES.NAME,
            PACKAGES.VERSION_MAJOR,
            PACKAGES.VERSION_MINOR,
            PACKAGES.VERSION_PATCH,
            PACKAGES.VERSION_QUALIFIER
          )
          .from(PACKAGES)
          .orderBy(PACKAGES.ID)
          .stream()
          .map(Transaction::mapPackageIdentifier)
          .collect(Collectors.toUnmodifiableSet());
      } catch (final DataAccessException e) {
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
    }

    private static SBPackageIdentifier mapPackageIdentifier(
      final Record6<Long, String, Long, Long, Long, String> rec)
    {
      final var qText = rec.get(PACKAGES.VERSION_QUALIFIER);
      final Optional<VersionQualifier> q;
      if (!qText.isEmpty()) {
        q = Optional.of(new VersionQualifier(qText));
      } else {
        q = Optional.empty();
      }

      return new SBPackageIdentifier(
        rec.get(PACKAGES.NAME),
        new Version(
          rec.get(PACKAGES.VERSION_MAJOR).intValue(),
          rec.get(PACKAGES.VERSION_MINOR).intValue(),
          rec.get(PACKAGES.VERSION_PATCH).intValue(),
          q
        )
      );
    }

    @Override
    public Optional<SBPackage> packageGet(
      final SBPackageIdentifier identifier)
      throws SBInventoryException
    {
      final var context = this.createContext();

      try {
        final var idOpt =
          context.select(PACKAGES.ID)
            .from(PACKAGES)
            .where(packageMatches(identifier))
            .fetchOptional(PACKAGES.ID);

        if (idOpt.isEmpty()) {
          return Optional.empty();
        }

        final var id = idOpt.get();

        final var entries =
          context.select(
              BLOBS.HASH,
              BLOBS.HASH_ALGORITHM,
              BLOBS.SIZE,
              BLOBS.CONTENT_TYPE,
              PACKAGE_BLOBS.PATH
            )
            .from(BLOBS.join(PACKAGE_BLOBS).on(PACKAGE_BLOBS.BLOB_ID.eq(BLOBS.ID)))
            .where(PACKAGE_BLOBS.PACKAGE_ID.eq(id))
            .orderBy(BLOBS.ID.asc())
            .stream()
            .map(this::mapEntry)
            .collect(toUnmodifiableMap(SBPackageEntry::path, identity()));

        final var meta =
          context.select(PACKAGE_META.META_KEY, PACKAGE_META.META_VALUE)
            .from(PACKAGE_META)
            .where(PACKAGE_META.PACKAGE_ID.eq(id))
            .orderBy(PACKAGE_META.META_KEY)
            .stream()
            .map(r -> Map.entry(
              r.get(PACKAGE_META.META_KEY),
              r.get(PACKAGE_META.META_VALUE)))
            .collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

        return Optional.of(new SBPackage(identifier, meta, entries));
      } catch (final DataAccessException e) {
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
    }

    private SBPackageEntry mapEntry(
      final Record5<String, String, Long, String, String> rec)
    {
      return new SBPackageEntry(
        SBPath.parse(rec.get(PACKAGE_BLOBS.PATH)),
        new SBBlob(
          rec.get(BLOBS.SIZE).longValue(),
          rec.get(BLOBS.CONTENT_TYPE),
          new SBHash(
            SBHashAlgorithm.ofJSSName(rec.get(BLOBS.HASH_ALGORITHM)),
            this.inventory.hexFormat.parseHex(rec.get(BLOBS.HASH))
          )
        )
      );
    }

    @Override
    public Optional<SBBlob> blobGet(
      final SBHash hash)
      throws SBInventoryException
    {
      final var context = this.createContext();

      try {
        final var hashV =
          this.inventory.hexFormat.formatHex(hash.value());
        final var hashA =
          hash.algorithm().jssAlgorithmName();

        return context.selectFrom(BLOBS)
          .where(BLOBS.HASH.eq(hashV).and(BLOBS.HASH_ALGORITHM.eq(hashA)))
          .orderBy(BLOBS.ID)
          .fetchOptional()
          .map(this::mapBlobRecord);
      } catch (final DataAccessException e) {
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
    }

    private SBBlob mapBlobRecord(
      final Record rec)
    {
      return new SBBlob(
        rec.get(BLOBS.SIZE).longValue(),
        rec.get(BLOBS.CONTENT_TYPE),
        new SBHash(
          SBHashAlgorithm.ofJSSName(rec.get(BLOBS.HASH_ALGORITHM)),
          this.inventory.hexFormat.parseHex(rec.get(BLOBS.HASH))
        )
      );
    }

    @Override
    public Map<SBHash, SBBlob> blobList()
      throws SBInventoryException
    {
      final var context = this.createContext();

      try {
        return context.selectFrom(BLOBS)
          .stream()
          .map(this::mapBlobRecord)
          .collect(toUnmodifiableMap(SBBlob::hash, identity()));
      } catch (final DataAccessException e) {
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
    }

    @Override
    public Map<SBHash, SBBlob> blobsUnreferenced()
      throws SBInventoryException
    {
      final var context = this.createContext();

      try {
        return context.select(
            BLOBS.ID,
            BLOBS.SIZE,
            BLOBS.HASH,
            BLOBS.HASH_ALGORITHM,
            BLOBS.CONTENT_TYPE
          )
          .from(BLOBS)
          .where(BLOBS.ID.notIn(
            context.select(PACKAGE_BLOBS.BLOB_ID)
              .from(PACKAGE_BLOBS)
          ))
          .stream()
          .map(this::mapBlobRecord)
          .collect(toUnmodifiableMap(SBBlob::hash, identity()));
      } catch (final DataAccessException e) {
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
    }

    @Override
    public void rollback()
      throws SBInventoryException
    {
      try {
        this.connection.rollback();
      } catch (final SQLException e) {
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
    }

    @Override
    public void commit()
      throws SBInventoryException
    {
      try {
        this.connection.commit();
      } catch (final SQLException e) {
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
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

        this.blobRecordSave(blob);
      } catch (final Exception e) {
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
    }

    @Override
    public void blobRemove(
      final SBBlob blob)
      throws SBInventoryException
    {
      final var context = this.createContext();
      final var hash = blob.hash();

      try {

        final var hashV = this.inventory.hexFormat.formatHex(hash.value());
        final var hashA = hash.algorithm().jssAlgorithmName();

        context.deleteFrom(BLOBS)
          .where(BLOBS.HASH.eq(hashV).and(BLOBS.HASH_ALGORITHM.eq(hashA)))
          .execute();

        final var pathBase =
          this.inventory.blobPath(hash);
        final var pathBlob =
          pathBase.resolveSibling(pathBase.getFileName() + ".b");
        final var pathLock =
          pathBase.resolveSibling(pathBase.getFileName() + ".l");

        try {
          Files.createDirectories(pathBlob.getParent());

          final var fileOptions =
            new OpenOption[]{CREATE, WRITE, TRUNCATE_EXISTING};

          try (var lockChannel =
                 FileChannel.open(pathLock, fileOptions)) {
            try (var ignored = lockChannel.lock()) {
              Files.deleteIfExists(pathBlob);
            }
          }
        } catch (final Exception e) {
          throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
        }

      } catch (final DataAccessException e) {
        if (e.getCause() instanceof SQLiteException ex) {
          if (ex.getResultCode() == SQLITE_CONSTRAINT_FOREIGNKEY) {
            throw new SBInventoryException(
              ERROR_BLOB_REFERENCED,
              this.inventory.strings.format("errorBlobReferenced", hash),
              e
            );
          }
        }
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
    }

    private DSLContext createContext()
    {
      return DSL.using(this.connection, SQLITE);
    }

    private void blobRecordSave(
      final SBBlob blob)
      throws SBInventoryException
    {
      final var context = this.createContext();

      try {
        final var hash = blob.hash();
        context.insertInto(BLOBS)
          .set(BLOBS.HASH, this.inventory.hexFormat.formatHex(hash.value()))
          .set(BLOBS.HASH_ALGORITHM, hash.algorithm().jssAlgorithmName())
          .set(BLOBS.CONTENT_TYPE, blob.contentType())
          .set(BLOBS.SIZE, Long.valueOf(blob.size()))
          .onConflictDoNothing()
          .execute();

      } catch (final DataAccessException e) {
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
    }

    private void blobWriteLocked(
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
      }
    }

    private static Condition packageMatches(
      final SBPackageIdentifier identifier)
    {
      final var version =
        identifier.version();

      final var qText =
        version.qualifier()
          .map(VersionQualifier::text)
          .orElse("");

      return DSL.and(
        PACKAGES.NAME.eq(identifier.name()),
        PACKAGES.VERSION_MAJOR.eq(toUnsignedLong(version.major())),
        PACKAGES.VERSION_MINOR.eq(toUnsignedLong(version.minor())),
        PACKAGES.VERSION_PATCH.eq(toUnsignedLong(version.patch())),
        PACKAGES.VERSION_QUALIFIER.eq(qText)
      );
    }

    @Override
    public void packagePut(
      final SBPackage pack)
      throws SBInventoryException
    {
      final var context = this.createContext();

      try {
        final var identifier =
          pack.identifier();
        final var blobIds =
          this.blobIdsForPackage(pack, context, identifier);
        final var packageMatches =
          packageMatches(identifier);
        final var existingId =
          context.select(PACKAGES.ID)
            .from(PACKAGES)
            .where(packageMatches)
            .fetchOptional(PACKAGES.ID);
        final var timeNow =
          OffsetDateTime.now(ZoneId.of("UTC"));

        final var idName =
          identifier.name();
        final var version =
          identifier.version();

        if (existingId.isPresent()) {
          if (!version.isSnapshot()) {
            throw new SBInventoryException(
              ERROR_PACKAGE_DUPLICATE,
              this.inventory.strings.format("errorPackageDuplicate", identifier)
            );
          }
          packagePutUpdateSnapshot(
            existingId.get(),
            pack,
            context,
            blobIds,
            timeNow
          );
        } else {
          packagePutNew(pack, context, blobIds, timeNow, idName, version);
        }

      } catch (final DataAccessException e) {
        throw new SBInventoryException(ERROR_DATABASE, e.getMessage(), e);
      }
    }

    private static void packagePutUpdateSnapshot(
      final Long packageId,
      final SBPackage pack,
      final DSLContext context,
      final HashMap<SBHash, Long> blobIds,
      final OffsetDateTime timeNow)
    {
      final var batchQueries = new ArrayList<Query>();

      batchQueries.add(
        context.update(PACKAGES)
          .set(PACKAGES.UPDATED, timeNow.toString())
          .where(PACKAGES.ID.eq(packageId))
      );
      batchQueries.add(
        context.deleteFrom(PACKAGE_BLOBS)
          .where(PACKAGE_BLOBS.PACKAGE_ID.eq(packageId))
      );
      batchQueries.add(
        context.deleteFrom(PACKAGE_META)
          .where(PACKAGE_META.PACKAGE_ID.eq(packageId))
      );

      for (final var entry : pack.entries().values()) {
        final var blobId = blobIds.get(entry.blob().hash());
        batchQueries.add(
          context.insertInto(PACKAGE_BLOBS)
            .set(PACKAGE_BLOBS.PACKAGE_ID, packageId)
            .set(PACKAGE_BLOBS.BLOB_ID, blobId)
            .set(PACKAGE_BLOBS.PATH, entry.path().toString())
        );
      }

      for (final var entry : pack.metadata().entrySet()) {
        batchQueries.add(
          context.insertInto(PACKAGE_META)
            .set(PACKAGE_META.PACKAGE_ID, packageId)
            .set(PACKAGE_META.META_KEY, entry.getKey())
            .set(PACKAGE_META.META_VALUE, entry.getValue())
        );
      }

      context.batch(batchQueries)
        .execute();
    }

    private static void packagePutNew(
      final SBPackage pack,
      final DSLContext context,
      final HashMap<SBHash, Long> blobIds,
      final OffsetDateTime timeNow,
      final String name,
      final Version version)
    {
      final var qualifierText =
        version.qualifier()
          .map(VersionQualifier::text)
          .orElse("");

      final var packageId =
        context.insertInto(PACKAGES)
          .set(PACKAGES.NAME, name)
          .set(PACKAGES.VERSION_MAJOR, toUnsignedLong(version.major()))
          .set(PACKAGES.VERSION_MINOR, toUnsignedLong(version.minor()))
          .set(PACKAGES.VERSION_PATCH, toUnsignedLong(version.patch()))
          .set(PACKAGES.VERSION_QUALIFIER, qualifierText)
          .set(PACKAGES.UPDATED, timeNow.toString())
          .returning(PACKAGES.ID)
          .fetchOne(PACKAGES.ID);

      final var batchQueries = new ArrayList<Query>();
      for (final var entry : pack.entries().values()) {
        final var blobId = blobIds.get(entry.blob().hash());
        batchQueries.add(
          context.insertInto(PACKAGE_BLOBS)
            .set(PACKAGE_BLOBS.PACKAGE_ID, packageId)
            .set(PACKAGE_BLOBS.BLOB_ID, blobId)
            .set(PACKAGE_BLOBS.PATH, entry.path().toString())
        );
      }

      for (final var entry : pack.metadata().entrySet()) {
        batchQueries.add(
          context.insertInto(PACKAGE_META)
            .set(PACKAGE_META.PACKAGE_ID, packageId)
            .set(PACKAGE_META.META_KEY, entry.getKey())
            .set(PACKAGE_META.META_VALUE, entry.getValue())
        );
      }

      context.batch(batchQueries)
        .execute();
    }

    private HashMap<SBHash, Long> blobIdsForPackage(
      final SBPackage pack,
      final DSLContext context,
      final SBPackageIdentifier id)
      throws SBInventoryException
    {
      final var blobIds = new HashMap<SBHash, Long>();
      final var missing = new ArrayList<String>();
      for (final var entry : pack.entries().values()) {
        final var blob = entry.blob();
        final var hash = blob.hash();

        final var algoName =
          hash.algorithm().jssAlgorithmName();
        final var hashValue =
          this.inventory.hexFormat.formatHex(hash.value());
        final var blobMatches =
          BLOBS.HASH_ALGORITHM.eq(algoName).and(BLOBS.HASH.eq(hashValue));

        final var blobId =
          context.select(BLOBS.ID)
            .from(BLOBS)
            .where(blobMatches)
            .fetchOptional(BLOBS.ID);

        if (blobId.isEmpty()) {
          missing.add(hash.toString());
        } else {
          blobIds.put(hash, blobId.get());
        }
      }

      if (!missing.isEmpty()) {
        throw new SBInventoryException(
          ERROR_PACKAGE_MISSING_BLOBS,
          this.inventory.strings.format(
            "errorPackageMissingBlobs",
            id,
            String.join("\n", missing)
          )
        );
      }
      return blobIds;
    }
  }

  private Path blobPath(
    final SBHash hash)
  {
    final var name =
      this.hexFormat.formatHex(hash.value());
    final var algo =
      hash.algorithm().jssAlgorithmName();
    final var start =
      name.substring(0, 2);
    final var end =
      name.substring(2);

    return this.configuration.base()
      .resolve("blob")
      .resolve(algo)
      .resolve(start)
      .resolve(end)
      .toAbsolutePath();
  }
}
