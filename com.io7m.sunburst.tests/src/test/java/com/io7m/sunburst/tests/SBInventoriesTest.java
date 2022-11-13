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
import com.io7m.sunburst.model.SBPath;
import com.io7m.verona.core.Version;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.io.input.BrokenInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_BLOB_REFERENCED;
import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_PACKAGE_DUPLICATE;
import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_PACKAGE_MISSING_BLOBS;
import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_PATH_NONEXISTENT;
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

  private static ExamplePackage createExamplePackage()
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
        "com.io7m.example.main",
        Version.of(1, 0, 0)
      ),
      Map.ofEntries(
        Map.entry("title", "A title."),
        Map.entry("something", "Something.")
      ),
      entryMap
    );

    return new ExamplePackage(blobs, blobsData, packageV);
  }

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
    final var packV = createExamplePackage();

    try (var inventory =
           this.inventories.openReadWrite(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransaction()) {
        for (final var b : packV.blobs.values()) {
          t.blobAdd(b, new ByteArrayInputStream(packV.blobsData.get(b.hash())));
        }
        t.commit();
      }
    }

    try (var inventory =
           this.inventories.openReadOnly(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransactionReadable()) {
        assertEquals(packV.blobs, t.blobList());
        assertEquals(packV.blobs, t.blobsUnreferenced());
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
        "com.io7m.example.main",
        Version.of(1, 0, 0)
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
    final var packV =
      createExamplePackage();

    try (var inventory =
           this.inventories.openReadWrite(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransaction()) {
        for (final var b : packV.blobs.values()) {
          t.blobAdd(b, new ByteArrayInputStream(packV.blobsData.get(b.hash())));
        }
        t.packagePut(packV.packageV);
        t.commit();
      }
    }

    try (var inventory =
           this.inventories.openReadOnly(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransactionReadable()) {
        final var identifier = packV.packageV.identifier();
        assertEquals(Set.of(identifier), t.packages());
        assertEquals(packV.packageV, t.packageGet(identifier).orElseThrow());

        assertEquals(
          Set.of(identifier),
          t.packagesUpdatedSince(OffsetDateTime.now().minusDays(1L))
        );
        assertEquals(
          Set.of(),
          t.packagesUpdatedSince(OffsetDateTime.now().plusDays(1L))
        );
        assertEquals(
          Map.of(),
          t.blobsUnreferenced()
        );

        for (final var entry : packV.packageV.entries().values()) {
          final var file =
            t.blobFile(identifier, entry.path());
          final var blob =
            entry.blob();

          try (var stream = Files.newInputStream(file)) {
            final var fileHash =
              SBHash.hashOf(blob.hash().algorithm(), stream);
            assertEquals(blob.hash(), fileHash);
          }
        }

        final var ex =
          assertThrows(SBInventoryException.class, () -> {
            t.blobFile(identifier, SBPath.parse("/nonexistent"));
          });
        assertEquals(ERROR_PATH_NONEXISTENT, ex.errorCode());
      }
    }
  }

  /**
   * Non-snapshot packages cannot be updated.
   *
   * @throws Exception On errors
   */

  @Test
  public void testPackageUpdateDisallowed()
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
        "com.io7m.example.main",
        Version.of(1, 0, 0)
      ),
      Map.ofEntries(
        Map.entry("title", "A title."),
        Map.entry("something", "Something.")
      ),
      entryMap
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
           this.inventories.openReadWrite(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransaction()) {
        final var ex =
          assertThrows(SBInventoryException.class, () -> {
            t.packagePut(packageV);
          });

        assertEquals(ERROR_PACKAGE_DUPLICATE, ex.errorCode());
        t.commit();
      }
    }
  }

  /**
   * Nonexistent packages cannot be retrieved.
   *
   * @throws Exception On errors
   */

  @Test
  public void testPackageNonexistent()
    throws Exception
  {
    try (var inventory =
           this.inventories.openReadWrite(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransaction()) {
        assertEquals(Optional.empty(), t.packageGet(
          new SBPackageIdentifier(
            "com.io7m.example.main",
            Version.of(1, 0, 0)
          )
        ));
      }
    }
  }

  /**
   * Referenced blobs cannot be removed.
   *
   * @throws Exception On errors
   */

  @Test
  public void testPackageRemoveReferenced()
    throws Exception
  {
    final var packV = createExamplePackage();

    try (var inventory =
           this.inventories.openReadWrite(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransaction()) {
        for (final var b : packV.blobs.values()) {
          t.blobAdd(b, new ByteArrayInputStream(packV.blobsData.get(b.hash())));
        }
        t.packagePut(packV.packageV);
        t.commit();
      }

      try (var t = inventory.openTransaction()) {
        final var ex =
          assertThrows(SBInventoryException.class, () -> {
            t.blobRemove(packV.blobs.values().iterator().next());
          });
        assertEquals(ERROR_BLOB_REFERENCED, ex.errorCode());
      }
    }
  }

  /**
   * Snapshot packages can be updated.
   *
   * @throws Exception On errors
   */

  @Test
  public void testPackageAddSnapshotUpdate()
    throws Exception
  {
    final var rng =
      SecureRandom.getInstanceStrong();
    final var buffer =
      new byte[8192];

    final var blobsBefore =
      new HashMap<SBHash, SBBlob>();
    final var blobsBeforeData =
      new HashMap<SBHash, byte[]>();

    final var entriesBefore = new ArrayList<SBPackageEntry>();
    for (int index = 0; index < 1000; ++index) {
      rng.nextBytes(buffer);
      final var hash =
        SBHash.hashOf(SHA2_256, new ByteArrayInputStream(buffer));
      final var blob =
        new SBBlob(8192L, "application/octet-stream", hash);
      blobsBefore.put(hash, blob);
      blobsBeforeData.put(hash, Arrays.copyOf(buffer, buffer.length));
      entriesBefore.add(new SBPackageEntry(SBPath.parse("/a" + index), blob));
    }

    final var blobsAfter =
      new HashMap<SBHash, SBBlob>();
    final var blobsAfterData =
      new HashMap<SBHash, byte[]>();

    final var entriesAfter = new ArrayList<SBPackageEntry>();
    for (int index = 0; index < 1000; ++index) {
      if (index % 2 == 0) {
        final var before = entriesBefore.get(index);
        entriesAfter.add(before);
        final var blob = before.blob();
        final var hash = blob.hash();
        blobsAfter.put(hash, blob);
        final var data =
          Objects.requireNonNull(
            blobsBeforeData.get(hash),
            "blobsBeforeData.get(hash)");
        blobsAfterData.put(hash, data);
        continue;
      }

      rng.nextBytes(buffer);
      final var hash =
        SBHash.hashOf(SHA2_256, new ByteArrayInputStream(buffer));
      final var blob =
        new SBBlob(8192L, "application/octet-stream", hash);
      blobsAfter.put(hash, blob);
      blobsAfterData.put(hash, Arrays.copyOf(buffer, buffer.length));
      entriesAfter.add(new SBPackageEntry(SBPath.parse("/a" + index), blob));
    }

    final var entriesBeforeMap =
      entriesBefore.stream()
        .collect(Collectors.toMap(SBPackageEntry::path, identity()));

    final var entriesAfterMap =
      entriesAfter.stream()
        .collect(Collectors.toMap(SBPackageEntry::path, identity()));

    final var packageBefore = new SBPackage(
      new SBPackageIdentifier(
        "com.io7m.example.main",
        Version.of(1, 0, 0, "SNAPSHOT")
      ),
      Map.ofEntries(
        Map.entry("title", "A title."),
        Map.entry("something", "Something.")
      ),
      entriesBeforeMap
    );

    final var packageAfter = new SBPackage(
      new SBPackageIdentifier(
        "com.io7m.example.main",
        Version.of(1, 0, 0, "SNAPSHOT")
      ),
      Map.ofEntries(
        Map.entry("title", "A title after."),
        Map.entry("something", "Something after.")
      ),
      entriesAfterMap
    );

    /*
     * Add the package.
     */

    try (var inventory =
           this.inventories.openReadWrite(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransaction()) {
        for (final var b : blobsBefore.values()) {
          t.blobAdd(b, new ByteArrayInputStream(blobsBeforeData.get(b.hash())));
        }
        t.packagePut(packageBefore);
        t.commit();
      }
    }

    /*
     * Update the package. This will cause roughly half of the blobs to
     * become unreferenced (from the old package version).
     */

    try (var inventory =
           this.inventories.openReadWrite(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransaction()) {
        for (final var b : blobsAfter.values()) {
          t.blobAdd(b, new ByteArrayInputStream(blobsAfterData.get(b.hash())));
        }
        t.packagePut(packageAfter);
        t.commit();
      }
    }

    /*
     * The blobs that were in the "before" package but are no longer in the
     * "after" package are now unreferenced.
     */

    final var unreferenced = new HashMap<SBHash, SBBlob>();
    for (final var blobBefore : blobsBefore.values()) {
      if (!blobsAfter.containsKey(blobBefore.hash())) {
        unreferenced.put(
          blobBefore.hash(),
          blobBefore
        );
      }
    }

    /*
     * Check the results.
     */

    try (var inventory =
           this.inventories.openReadOnly(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransactionReadable()) {
        assertEquals(Set.of(packageAfter.identifier()), t.packages());
        assertEquals(
          packageAfter,
          t.packageGet(packageAfter.identifier()).orElseThrow());

        assertEquals(
          Set.of(packageAfter.identifier()),
          t.packagesUpdatedSince(OffsetDateTime.now().minusDays(1L))
        );
        assertEquals(
          Set.of(),
          t.packagesUpdatedSince(OffsetDateTime.now().plusDays(1L))
        );
        assertEquals(
          unreferenced,
          t.blobsUnreferenced()
        );
      }
    }

    /*
     * Deleting the unreferenced blobs is now possible.
     */

    try (var inventory =
           this.inventories.openReadWrite(
             new SBInventoryConfiguration(ROOT, this.directory))) {
      try (var t = inventory.openTransaction()) {
        for (final var b : unreferenced.values()) {
          t.blobRemove(b);
        }

        assertEquals(Map.of(), t.blobsUnreferenced());
        t.commit();
      }
    }
  }

  record ExamplePackage(
    HashMap<SBHash, SBBlob> blobs,
    HashMap<SBHash, byte[]> blobsData,
    SBPackage packageV)
  {

  }
}
