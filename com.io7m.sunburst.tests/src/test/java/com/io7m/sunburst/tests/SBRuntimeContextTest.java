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

import com.io7m.sunburst.inventory.SBInventories;
import com.io7m.sunburst.inventory.api.SBInventoryConfiguration;
import com.io7m.sunburst.inventory.api.SBInventoryType;
import com.io7m.sunburst.model.SBBlob;
import com.io7m.sunburst.model.SBHash;
import com.io7m.sunburst.model.SBPackage;
import com.io7m.sunburst.model.SBPackageEntry;
import com.io7m.sunburst.model.SBPackageIdentifier;
import com.io7m.sunburst.model.SBPackageVersion;
import com.io7m.sunburst.model.SBPath;
import com.io7m.sunburst.model.SBPeer;
import com.io7m.sunburst.model.SBPeerException;
import com.io7m.sunburst.runtime.SBRuntimeBrokenPeerFactory;
import com.io7m.sunburst.runtime.SBRuntimeConflictingPeer;
import com.io7m.sunburst.runtime.SBRuntimeException;
import com.io7m.sunburst.runtime.SBRuntimeInventoryProblem;
import com.io7m.sunburst.runtime.SBRuntimeServiceLoaderType;
import com.io7m.sunburst.runtime.SBRuntimeUnsatisfiedRequirement;
import com.io7m.sunburst.runtime.Sunburst;
import com.io7m.sunburst.runtime.spi.SBPeerFactoryType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.function.Supplier;

import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_IO;
import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_PEER_IMPORT_MISSING;
import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_PEER_MISSING;
import static com.io7m.sunburst.model.SBHashAlgorithm.SHA2_256;
import static java.util.Locale.ROOT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class SBRuntimeContextTest
{
  private Path directory;
  private SBInventoryType inventory;

  @BeforeEach
  public void setup()
    throws Exception
  {
    this.directory =
      SBTestDirectories.createTempDirectory();
    this.inventory =
      new SBInventories()
        .openReadWrite(new SBInventoryConfiguration(ROOT, this.directory));
  }

  @AfterEach
  public void tearDown()
    throws Exception
  {
    this.inventory.close();
  }

  /**
   * If no peers are available, opening succeeds.
   */

  @Test
  public void testNoPeers()
  {
    final var loader =
      new SBRuntimeServiceLoaderType()
      {
        @Override
        public <T> List<Supplier<T>> load(
          final Class<T> clazz)
          throws ServiceConfigurationError
        {
          return List.of();
        }
      };

    final var context =
      Sunburst.openUsingInventory(this.inventory, loader);

    assertTrue(context.isSuccessful());
    assertFalse(context.isFailed());

    context.reload();

    assertTrue(context.isSuccessful());
    assertFalse(context.isFailed());
  }

  /**
   * If a peer crashes on creation, the runtime has failed.
   */

  @Test
  public void testOnePeerCrashes()
  {
    final var exception =
      new SBPeerException(ERROR_IO, "x", List.of());

    final var loader =
      new SBRuntimeServiceLoaderType()
      {
        @Override
        public <T> List<Supplier<T>> load(
          final Class<T> clazz)
          throws ServiceConfigurationError
        {
          return List.of(
            () -> {
              return clazz.cast((SBPeerFactoryType) () -> {
                throw exception;
              });
            }
          );
        }
      };

    final var context =
      Sunburst.openUsingInventory(this.inventory, loader);

    assertTrue(context.isFailed());
    assertFalse(context.isSuccessful());

    final var status = context.status();

    {
      final var e = (SBRuntimeBrokenPeerFactory) status.problems().get(0);
      assertEquals(exception, e.exception());
    }

    context.reload();

    assertTrue(context.isFailed());
    assertFalse(context.isSuccessful());
    assertEquals(status, context.status());
  }

  /**
   * If a peer supplier crashes, the runtime has failed.
   */

  @Test
  public void testOnePeerCrashesSupplier()
  {
    final var exception =
      new IllegalStateException();

    final var loader =
      new SBRuntimeServiceLoaderType()
      {
        @Override
        public <T> List<Supplier<T>> load(
          final Class<T> clazz)
          throws ServiceConfigurationError
        {
          return List.of(
            () -> {
              throw exception;
            }
          );
        }
      };

    final var context =
      Sunburst.openUsingInventory(this.inventory, loader);

    assertTrue(context.isFailed());
    assertFalse(context.isSuccessful());

    final var status = context.status();

    {
      final var e = (SBRuntimeBrokenPeerFactory) status.problems().get(0);
      assertEquals(exception, e.exception());
    }

    context.reload();

    assertTrue(context.isFailed());
    assertFalse(context.isSuccessful());
    assertEquals(status, context.status());
  }

  /**
   * Multiple peers cannot have the same name.
   *
   * @throws Exception On errors
   */

  @Test
  public void testPeerConflict()
    throws Exception
  {
    final var peer0 =
      SBPeer.builder("com.io7m.example")
        .build();

    final SBPeerFactoryType peer0Factory =
      () -> peer0;

    final var peer1 =
      SBPeer.builder("com.io7m.example")
        .build();

    final SBPeerFactoryType peer1Factory =
      () -> peer1;

    final var loader =
      new SBRuntimeServiceLoaderType()
      {
        @Override
        public <T> List<Supplier<T>> load(
          final Class<T> clazz)
          throws ServiceConfigurationError
        {
          return List.of(
            () -> clazz.cast(peer0Factory),
            () -> clazz.cast(peer1Factory)
          );
        }
      };

    final var context =
      Sunburst.openUsingInventory(this.inventory, loader);

    assertTrue(context.isFailed());
    assertFalse(context.isSuccessful());

    final var status = context.status();

    {
      final var e = (SBRuntimeConflictingPeer) status.problems().get(0);
      assertEquals("com.io7m.example", e.peer0Name());
      assertEquals("com.io7m.example", e.peer1Name());
    }

    context.reload();

    assertTrue(context.isFailed());
    assertFalse(context.isSuccessful());
    assertEquals(status, context.status());
  }

  /**
   * A peer that imports a missing package causes failure.
   *
   * @throws Exception On errors
   */

  @Test
  public void testPeerImportMissing()
    throws Exception
  {
    final var peer0 =
      SBPeer.builder("com.io7m.example")
        .addImportText("com.io7m.ex0:1.0.0")
        .build();

    final SBPeerFactoryType peer0Factory =
      () -> peer0;

    final var loader =
      new SBRuntimeServiceLoaderType()
      {
        @Override
        public <T> List<Supplier<T>> load(
          final Class<T> clazz)
          throws ServiceConfigurationError
        {
          return List.of(
            () -> clazz.cast(peer0Factory)
          );
        }
      };

    final var context =
      Sunburst.openUsingInventory(this.inventory, loader);

    assertTrue(context.isFailed());
    assertFalse(context.isSuccessful());

    final var status = context.status();

    {
      final var e = (SBRuntimeUnsatisfiedRequirement) status.problems().get(0);
      assertEquals("com.io7m.example", e.importer());
      assertEquals("com.io7m.ex0", e.required().name());
      assertEquals("1.0.0", e.required().version().toString());
    }

    context.reload();

    assertTrue(context.isFailed());
    assertFalse(context.isSuccessful());
    assertEquals(status, context.status());
  }

  /**
   * A crashing inventory fails a peer.
   *
   * @throws Exception On errors
   */

  @Test
  public void testPeerInventoryCrashing()
    throws Exception
  {
    final var peer0 =
      SBPeer.builder("com.io7m.example")
        .addImportText("com.io7m.ex0:1.0.0")
        .build();

    final SBPeerFactoryType peer0Factory =
      () -> peer0;

    final var loader =
      new SBRuntimeServiceLoaderType()
      {
        @Override
        public <T> List<Supplier<T>> load(
          final Class<T> clazz)
          throws ServiceConfigurationError
        {
          return List.of(
            () -> clazz.cast(peer0Factory)
          );
        }
      };

    final var context =
      Sunburst.openUsingInventory(
        new SBPackageInventoryCrashing(
          EnumSet.noneOf(SBPackageInventoryCrashing.CrashOn.class)
        ),
        loader
      );

    assertTrue(context.isFailed());
    assertFalse(context.isSuccessful());

    final var status = context.status();

    {
      final var e = (SBRuntimeInventoryProblem) status.problems().get(0);
      final var ex = e.exception();
      assertEquals(ERROR_IO, ex.errorCode());
    }

    context.reload();

    assertTrue(context.isFailed());
    assertFalse(context.isSuccessful());
  }

  /**
   * A crashing inventory fails a peer.
   *
   * @throws Exception On errors
   */

  @Test
  public void testPeerInventoryCrashingClose()
    throws Exception
  {
    final var peer0 =
      SBPeer.builder("com.io7m.example")
        .addImportText("com.io7m.ex0:1.0.0")
        .build();

    final SBPeerFactoryType peer0Factory =
      () -> peer0;

    final var loader =
      new SBRuntimeServiceLoaderType()
      {
        @Override
        public <T> List<Supplier<T>> load(
          final Class<T> clazz)
          throws ServiceConfigurationError
        {
          return List.of(
            () -> clazz.cast(peer0Factory)
          );
        }
      };

    final var context =
      Sunburst.openUsingInventory(
        new SBPackageInventoryCrashing(
          EnumSet.allOf(SBPackageInventoryCrashing.CrashOn.class)
        ),
        loader
      );

    assertTrue(context.isFailed());
    assertFalse(context.isSuccessful());

    final var status = context.status();

    {
      final var e = (SBRuntimeInventoryProblem) status.problems().get(0);
      final var ex = e.exception();
      assertEquals(ERROR_IO, ex.errorCode());
    }

    context.reload();

    assertTrue(context.isFailed());
    assertFalse(context.isSuccessful());
  }

  /**
   * A file cannot be opened from a package that isn't imported.
   *
   * @throws Exception On errors
   */

  @Test
  public void testFileOpenNotImported()
    throws Exception
  {
    final var peer0 =
      SBPeer.builder("com.io7m.sunburst.tests")
        .build();

    final SBPeerFactoryType peer0Factory =
      () -> peer0;

    final var loader =
      new SBRuntimeServiceLoaderType()
      {
        @Override
        public <T> List<Supplier<T>> load(
          final Class<T> clazz)
          throws ServiceConfigurationError
        {
          return List.of(
            () -> clazz.cast(peer0Factory)
          );
        }
      };

    final var context =
      Sunburst.openUsingInventory(this.inventory, loader);

    assertTrue(context.isSuccessful());

    final var ex =
      assertThrows(SBRuntimeException.class, () -> {
        context.findFile(
          SBRuntimeContextTest.class,
          "not.imported",
          "/a/b/c"
        );
      });

    assertEquals(ERROR_PEER_IMPORT_MISSING, ex.errorCode());
  }

  /**
   * A file cannot be opened by a peer that doesn't exist.
   *
   * @throws Exception On errors
   */

  @Test
  public void testFileOpenNotExisting()
    throws Exception
  {
    final var peer0 =
      SBPeer.builder("com.io7m.sunburst.not_tests")
        .build();

    final SBPeerFactoryType peer0Factory =
      () -> peer0;

    final var loader =
      new SBRuntimeServiceLoaderType()
      {
        @Override
        public <T> List<Supplier<T>> load(
          final Class<T> clazz)
          throws ServiceConfigurationError
        {
          return List.of(
            () -> clazz.cast(peer0Factory)
          );
        }
      };

    final var context =
      Sunburst.openUsingInventory(this.inventory, loader);

    assertTrue(context.isSuccessful());

    final var ex =
      assertThrows(SBRuntimeException.class, () -> {
        context.findFile(
          SBRuntimeContextTest.class,
          "not.imported",
          "/a/b/c"
        );
      });

    assertEquals(ERROR_PEER_MISSING, ex.errorCode());
  }

  /**
   * A file can be opened by a peer that imports the package it lives in.
   *
   * @throws Exception On errors
   */

  @Test
  public void testFileOpenOK()
    throws Exception
  {
    final var packageIdentifier =
      new SBPackageIdentifier(
        "a.b.c",
        new SBPackageVersion(1, 0, 0, "")
      );

    final var rng = SecureRandom.getInstanceStrong();
    final var data = new byte[8192];
    rng.nextBytes(data);
    final var testFile = this.directory.resolve("test.bin");
    Files.write(testFile, data);

    final var hash =
      SBHash.hashOf(SHA2_256, Files.newInputStream(testFile));

    try (var transaction = this.inventory.openTransaction()) {
      final var blob = new SBBlob(8192L, "text/plain", hash);
      transaction.blobAdd(blob, Files.newInputStream(testFile));

      final var path =
        SBPath.parse("/x");

      final var pack =
        new SBPackage(
          packageIdentifier,
          Map.of(),
          Map.of(path, new SBPackageEntry(path, blob))
        );

      transaction.packagePut(pack);
      transaction.commit();
    }

    final var peer0 =
      SBPeer.builder("com.io7m.sunburst.tests")
        .addImport(packageIdentifier)
        .build();

    final SBPeerFactoryType peer0Factory =
      () -> peer0;

    final var loader =
      new SBRuntimeServiceLoaderType()
      {
        @Override
        public <T> List<Supplier<T>> load(
          final Class<T> clazz)
          throws ServiceConfigurationError
        {
          return List.of(
            () -> clazz.cast(peer0Factory)
          );
        }
      };

    final var context =
      Sunburst.openUsingInventory(this.inventory, loader);

    assertTrue(context.isSuccessful());

    final var channel =
      context.openChannel(
        SBRuntimeContextTest.class,
        packageIdentifier.name(),
        "/x"
      );

    final var readData =
      Channels.newInputStream(channel)
        .readAllBytes();

    assertArrayEquals(data, readData);
  }
}
