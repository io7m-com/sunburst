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

import com.io7m.sunburst.model.SBPackageIdentifier;
import com.io7m.sunburst.model.SBPath;
import com.io7m.sunburst.pkggen.SBPackageGeneratorConfiguration;
import com.io7m.sunburst.pkggen.SBPackageGenerators;
import com.io7m.sunburst.xml.packages.SBPackageParsers;
import com.io7m.verona.core.Version;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static org.junit.jupiter.api.Assertions.assertEquals;

public final class SBPackageGeneratorTest
{
  public static final SBPackageIdentifier EXAMPLE_IDENTIFIER = new SBPackageIdentifier(
    "com.io7m.ex",
    Version.of(1, 0, 0)
  );
  private Path sourceDirectory;
  private SBPackageGenerators generators;
  private Path outputDirectory;
  private SBPackageParsers parsers;
  private Path packageFile;
  private Path metaFile;

  @BeforeEach
  public void setup()
    throws Exception
  {
    this.sourceDirectory = SBTestDirectories.createTempDirectory();
    this.outputDirectory = SBTestDirectories.createTempDirectory();
    this.parsers = new SBPackageParsers();
    this.generators = new SBPackageGenerators();

    this.metaFile =
      this.outputDirectory.resolve("meta.properties");
    this.packageFile =
      this.outputDirectory.resolve("package.xml");
  }

  @AfterEach
  public void tearDown()
    throws Exception
  {
    SBTestDirectories.deleteDirectory(this.sourceDirectory);
    SBTestDirectories.deleteDirectory(this.outputDirectory);
  }

  /**
   * A package containing a single text file is built correctly.
   * @throws Exception On errors
   */

  @Test
  public void testPackageOneFile()
    throws Exception
  {
    Files.writeString(
      this.sourceDirectory.resolve("file.txt"),
      "Hello."
    );

    final var generator =
      this.generators.createGenerator(
        new SBPackageGeneratorConfiguration(
          this.sourceDirectory,
          this.packageFile,
          Optional.empty(),
          EXAMPLE_IDENTIFIER
        )
      );

    generator.execute();

    final var pack = this.parsers.parseFile(this.packageFile);
    assertEquals(Map.of(), pack.metadata());
    assertEquals(EXAMPLE_IDENTIFIER, pack.identifier());
    assertEquals(1, pack.entries().size());

    {
      final var path = SBPath.parse("/file.txt");
      final var e = pack.entries().get(path);
      final var b = e.blob();
      assertEquals(path, e.path());
      assertEquals(6L, b.size());
      assertEquals("text/plain", b.contentType());
      assertEquals(
        "SHA2_256:2D8BD7D9BB5F85BA643F0110D50CB506A1FE439E769A22503193EA6046BB87F7",
        b.hash().toString()
      );
    }
  }

  /**
   * A package containing multiple text files and metadata is built correctly.
   * @throws Exception On errors
   */

  @Test
  public void testPackageMultipleFile()
    throws Exception
  {
    Files.writeString(
      this.sourceDirectory.resolve("file0.txt"),
      "Hello 0."
    );
    Files.writeString(
      this.sourceDirectory.resolve("file1.txt"),
      "Hello 1."
    );
    Files.writeString(
      this.sourceDirectory.resolve("file2.txt"),
      "Hello 2."
    );

    this.writeMetadata(ofEntries(
      entry("a", "x"),
      entry("b", "y"),
      entry("c", "z")
    ));

    final var generator =
      this.generators.createGenerator(
        new SBPackageGeneratorConfiguration(
          this.sourceDirectory,
          this.packageFile,
          Optional.of(this.metaFile),
          EXAMPLE_IDENTIFIER
        )
      );

    generator.execute();

    final var pack = this.parsers.parseFile(this.packageFile);
    assertEquals(ofEntries(
      entry("a", "x"),
      entry("b", "y"),
      entry("c", "z")
    ), pack.metadata());
    assertEquals(EXAMPLE_IDENTIFIER, pack.identifier());
    assertEquals(3, pack.entries().size());

    {
      final var path = SBPath.parse("/file0.txt");
      final var e = pack.entries().get(path);
      final var b = e.blob();
      assertEquals(path, e.path());
      assertEquals(8L, b.size());
      assertEquals("text/plain", b.contentType());
      assertEquals(
        "SHA2_256:760BC11721447DDCE010731C5A45E81A53AC8AEF112A9CA077D984CF7898EC21",
        b.hash().toString()
      );
    }

    {
      final var path = SBPath.parse("/file1.txt");
      final var e = pack.entries().get(path);
      final var b = e.blob();
      assertEquals(path, e.path());
      assertEquals(8L, b.size());
      assertEquals("text/plain", b.contentType());
      assertEquals(
        "SHA2_256:9F2EF847D3C8D3AB03EBF5AF689B1AB2BFA14FA8AB251289C82360CA7671E25D",
        b.hash().toString()
      );
    }

    {
      final var path = SBPath.parse("/file2.txt");
      final var e = pack.entries().get(path);
      final var b = e.blob();
      assertEquals(path, e.path());
      assertEquals(8L, b.size());
      assertEquals("text/plain", b.contentType());
      assertEquals(
        "SHA2_256:E3C7868C93C40AA40BC88EEBFB6DC2B76BFC6C8619BB3D65797B2E7063A495FA",
        b.hash().toString()
      );
    }
  }

  private void writeMetadata(
    final Map<String, String> m)
    throws IOException
  {
    final var p = new Properties();
    for (final var e : m.entrySet()) {
      p.setProperty(e.getKey(), e.getValue());
    }

    try (var out = Files.newOutputStream(this.metaFile)) {
      p.store(out, "");
    }
  }
}
