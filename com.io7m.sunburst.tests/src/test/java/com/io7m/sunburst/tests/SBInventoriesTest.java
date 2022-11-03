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
import com.io7m.sunburst.inventory.api.SBInventoryException;
import com.io7m.sunburst.model.SBBlob;
import com.io7m.sunburst.model.SBHash;
import com.io7m.sunburst.model.SBPackage;
import com.io7m.sunburst.model.SBPackageEntry;
import com.io7m.sunburst.model.SBPackageIdentifier;
import com.io7m.sunburst.model.SBPackageName;
import com.io7m.sunburst.model.SBPackageVersion;
import com.io7m.sunburst.model.SBPath;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.io.input.BrokenInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_PACKAGE_MISSING_BLOBS;
import static com.io7m.sunburst.model.SBHashAlgorithm.SHA2_256;
import static com.io7m.sunburst.tests.SBTestDirectories.resourceOf;
import static java.util.Locale.ROOT;
import static java.util.function.Function.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class SBInventoriesTest
{
  private Path directory;
  private SBInventories inventories;

  @BeforeEach
  public void setup()
    throws Exception
  {
    this.directory =
      SBTestDirectories.createTempDirectory();
    this.inventories =
      new SBInventories();
  }

  @AfterEach
  public void tearDown()
    throws Exception
  {
    SBTestDirectories.deleteDirectory(this.directory);
  }

  /**
   * Opening a nonexistent inventory works.
   *
   * @throws Exception On errors
   */

  @Test
  public void testOpenNonexistent()
    throws Exception
  {
    try (var ignored =
           this.inventories.openReadWrite(
             new SBInventoryConfiguration(ROOT, this.directory))) {

    }

    try (var ignored =
           this.inventories.openReadOnly(
             new SBInventoryConfiguration(ROOT, this.directory))) {

    }

    assertTrue(
      Files.isRegularFile(this.directory.resolve("sunburst.db"))
    );
  }

  /**
   * Adding a blob works.
   *
   * @throws Exception On errors
   */

  @Test
  public void testOpenBlobAdd()
    throws Exception
  {
    final var data =
      resourceOf(SBInventoriesTest.class, this.directory, "hello.txt");

    final var blob =
      new SBBlob(
        6L,
        "text/plain",
        SBHash.hashOf(SHA2_256, Files.newInputStream(data))
      );

    try (var inventory =
           this.inventories.openReadWrite(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransaction()) {
        try (var s = Files.newInputStream(data)) {
          t.blobAdd(blob, s);
        }
        t.commit();
      }
    }

    try (var inventory =
           this.inventories.openReadOnly(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransactionReadable()) {
        final var r = t.blobGet(blob.hash());
        assertEquals(Optional.of(blob), r);
        final var b = t.blobList();
        assertEquals(1, b.size());
        assertTrue(b.containsKey(blob.hash()));
      }
    }
  }

  /**
   * Adding lots of blobs works.
   *
   * @throws Exception On errors
   */

  @Test
  public void testOpenBlobAddMany()
    throws Exception
  {
    final var blobs =
      new HashMap<SBHash, SBBlob>();
    final var blobsData =
      new HashMap<SBHash, byte[]>();

    final var rng =
      SecureRandom.getInstanceStrong();

    final var buffer = new byte[8192];
    for (int index = 0; index < 10000; ++index) {
      rng.nextBytes(buffer);
      final var hash =
        SBHash.hashOf(SHA2_256, new ByteArrayInputStream(buffer));
      blobs.put(
        hash,
        new SBBlob(8192L, "application/octet-stream", hash)
      );
      blobsData.put(
        hash,
        Arrays.copyOf(buffer, buffer.length)
      );
    }

    try (var inventory =
           this.inventories.openReadWrite(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransaction()) {
        for (final var b : blobs.values()) {
          t.blobAdd(b, new ByteArrayInputStream(blobsData.get(b.hash())));
        }
        t.commit();
      }
    }

    try (var inventory =
           this.inventories.openReadOnly(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransactionReadable()) {
        assertEquals(blobs, t.blobList());
      }
    }
  }

  /**
   * On I/O errors, the database remains consistent.
   *
   * @throws Exception On errors
   */

  @Test
  public void testOpenBlobAddBroken()
    throws Exception
  {
    final var data =
      resourceOf(SBInventoriesTest.class, this.directory, "hello.txt");

    final var blob =
      new SBBlob(
        6L,
        "text/plain",
        SBHash.hashOf(SHA2_256, Files.newInputStream(data))
      );

    try (var inventory =
           this.inventories.openReadWrite(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransaction()) {
        assertThrows(SBInventoryException.class, () -> {
          t.blobAdd(blob, new BrokenInputStream());
        });
        t.commit();
      }
    }

    try (var inventory =
           this.inventories.openReadOnly(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransactionReadable()) {
        final var r = t.blobGet(blob.hash());
        assertEquals(Optional.empty(), r);
        final var b = t.blobList();
        assertEquals(0, b.size());
        assertFalse(b.containsKey(blob.hash()));
        assertFalse(Files.exists(
          this.directory.resolve("blob")
            .resolve("A2")
            .resolve(
              "C064616AF4C66C576821616646BDFAD5556A263B4B007847605118971F4389.b")
        ));
      }
    }
  }

  /**
   * If corrupted data is written, the database remains consistent.
   *
   * @throws Exception On errors
   */

  @Test
  public void testOpenBlobAddCorrupted()
    throws Exception
  {
    final var data =
      resourceOf(SBInventoriesTest.class, this.directory, "hello.txt");

    final var blob =
      new SBBlob(
        6L,
        "text/plain",
        SBHash.hashOf(SHA2_256, Files.newInputStream(data))
      );

    try (var inventory =
           this.inventories.openReadWrite(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransaction()) {
        assertThrows(SBInventoryException.class, () -> {
          t.blobAdd(
            blob,
            new BoundedInputStream(Files.newInputStream(data), 2L));
        });
        t.commit();
      }
    }

    try (var inventory =
           this.inventories.openReadOnly(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransactionReadable()) {
        final var r = t.blobGet(blob.hash());
        assertEquals(Optional.empty(), r);
        final var b = t.blobList();
        assertEquals(0, b.size());
        assertFalse(b.containsKey(blob.hash()));
        assertFalse(Files.exists(
          this.directory.resolve("blob")
            .resolve("A2")
            .resolve(
              "C064616AF4C66C576821616646BDFAD5556A263B4B007847605118971F4389.b")
        ));
      }
    }
  }

  /**
   * Packages with missing blobs cannot be added.
   *
   * @throws Exception On errors
   */

  @Test
  public void testPackageAddMissingBlobs()
    throws Exception
  {
    final var entries = new ArrayList<SBPackageEntry>();
    entries.add(
      new SBPackageEntry(
        SBPath.parse("/a/b/c"),
        new SBBlob(
          23L,
          "text/plain",
          SBHash.hashOf(
            SHA2_256, new ByteArrayInputStream(new byte[3])))
      )
    );
    final var entryMap =
      entries.stream()
        .collect(Collectors.toMap(SBPackageEntry::path, identity()));

    final var packageV = new SBPackage(
      new SBPackageIdentifier(
        new SBPackageName("com.io7m.example", "com.io7m.example.main"),
        new SBPackageVersion(1, 0, 0, "")
      ),
      new TreeMap<>(),
      new TreeMap<>(entryMap)
    );

    try (var inventory =
           this.inventories.openReadWrite(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransaction()) {
        final var ex =
          assertThrows(SBInventoryException.class, () -> {
            t.packagePut(packageV);
          });
        assertEquals(ERROR_PACKAGE_MISSING_BLOBS, ex.errorCode());
        t.commit();
      }
    }
  }

  /**
   * Packages with all blobs present can be added.
   *
   * @throws Exception On errors
   */

  @Test
  public void testPackageAddOK()
    throws Exception
  {
    final var blobs =
      new HashMap<SBHash, SBBlob>();
    final var blobsData =
      new HashMap<SBHash, byte[]>();
    final var rng =
      SecureRandom.getInstanceStrong();

    final var buffer = new byte[8192];
    final var entries = new ArrayList<SBPackageEntry>();
    for (int index = 0; index < 10000; ++index) {
      rng.nextBytes(buffer);
      final var hash =
        SBHash.hashOf(SHA2_256, new ByteArrayInputStream(buffer));
      final var blob =
        new SBBlob(8192L, "application/octet-stream", hash);
      blobs.put(hash, blob);
      blobsData.put(hash, Arrays.copyOf(buffer, buffer.length));
      entries.add(new SBPackageEntry(SBPath.parse("/a" + index), blob));
    }

    final var entryMap =
      entries.stream()
        .collect(Collectors.toMap(SBPackageEntry::path, identity()));

    final var packageV = new SBPackage(
      new SBPackageIdentifier(
        new SBPackageName("com.io7m.example", "com.io7m.example.main"),
        new SBPackageVersion(1, 0, 0, "")
      ),
      new TreeMap<>(),
      new TreeMap<>(entryMap)
    );

    try (var inventory =
           this.inventories.openReadWrite(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransaction()) {
        for (final var b : blobs.values()) {
          t.blobAdd(b, new ByteArrayInputStream(blobsData.get(b.hash())));
        }
        t.packagePut(packageV);
        t.commit();
      }
    }

    try (var inventory =
           this.inventories.openReadOnly(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransactionReadable()) {
        assertEquals(Set.of(packageV.identifier()), t.packages());
        assertEquals(packageV, t.packageGet(packageV.identifier()).orElseThrow());
      }
    }
  }
}
