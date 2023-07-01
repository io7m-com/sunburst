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

import com.io7m.anethum.api.ParsingException;
import com.io7m.anethum.api.ParseStatus;
import com.io7m.mime2045.core.MimeType;
import com.io7m.sunburst.model.SBBlob;
import com.io7m.sunburst.model.SBHash;
import com.io7m.sunburst.model.SBPackage;
import com.io7m.sunburst.model.SBPackageEntry;
import com.io7m.sunburst.model.SBPath;
import com.io7m.sunburst.xml.packages.SBPackageParsers;
import com.io7m.sunburst.xml.packages.SBPackageSerializers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static com.io7m.sunburst.model.SBHashAlgorithm.SHA2_256;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class SBPackageParsersTest
{
  private static final MimeType TEXT_PLAIN =
    MimeType.of("text", "plain");

  private static final Logger LOG =
    LoggerFactory.getLogger(SBPackageParsersTest.class);

  private SBPackageParsers parsers;
  private SBPackageSerializers serializers;
  private Path directory;
  private ArrayList<ParseStatus> errors;

  @BeforeEach
  public void setup()
    throws IOException
  {
    this.parsers =
      new SBPackageParsers();
    this.serializers =
      new SBPackageSerializers();
    this.directory =
      SBTestDirectories.createTempDirectory();
    this.errors =
      new ArrayList<ParseStatus>();
  }

  @AfterEach
  public void tearDown()
    throws IOException
  {
    SBTestDirectories.deleteDirectory(this.directory);
  }

  @Test
  public void testUnparseable()
    throws Exception
  {
    final var file =
      SBTestDirectories.resourceOf(
        SBPackageParsersTest.class,
        this.directory,
        "hello.txt"
      );

    assertThrows(ParsingException.class, () -> {
      this.parsers.parseFile(file, SBPackageParsersTest::onStatus);
    });
  }

  private static void onStatus(
    final ParseStatus status)
  {
    LOG.error(
      "{}:{}: {}",
      Integer.valueOf(status.lexical().line()),
      Integer.valueOf(status.lexical().column()),
      status.message()
    );
  }

  @Test
  public void testInvalid0()
    throws Exception
  {
    final var file =
      SBTestDirectories.resourceOf(
        SBPackageParsersTest.class,
        this.directory,
        "p0_invalid.xml"
      );

    assertThrows(ParsingException.class, () -> {
      this.parsers.parseFile(file, SBPackageParsersTest::onStatus);
    });
  }

  @Test
  public void testInvalid1()
    throws Exception
  {
    final var file =
      SBTestDirectories.resourceOf(
        SBPackageParsersTest.class,
        this.directory,
        "p1_invalid.xml"
      );

    assertThrows(ParsingException.class, () -> {
      this.parsers.parseFile(file, SBPackageParsersTest::onStatus);
    });
  }

  @Test
  public void testPackage0()
    throws Exception
  {
    final var file =
      SBTestDirectories.resourceOf(
        SBPackageParsersTest.class,
        this.directory,
        "p0.xml"
      );

    final var pack =
      this.parsers.parseFile(file, parseStatus -> {
        LOG.debug("{}", parseStatus);
        this.errors.add(parseStatus);
      });

    final var id = pack.identifier();
    assertEquals("com.io7m.example.main", id.name());
    assertEquals(List.of("com", "io7m", "example", "main"), id.nameSegments());
    assertEquals(1, id.version().major());
    assertEquals(0, id.version().minor());
    assertEquals(0, id.version().patch());
    assertEquals(Optional.empty(), id.version().qualifier());

    final var meta = pack.metadata();
    assertEquals(
      "https://www.io7m.com/software/sunburst", meta.get("project.url")
    );
    assertEquals(
      "sunburst", meta.get("project.name")
    );
    assertEquals(
      "https://www.github.com/io7m/sunburst", meta.get("project.scm")
    );
    assertEquals(3, meta.size());

    final var entries = pack.entries();
    assertEquals(
      new SBPackageEntry(
        SBPath.parse("/a"),
        new SBBlob(
          23L,
          TEXT_PLAIN,
          new SBHash(SHA2_256,
                     unhex(
                       "F74F221E3C374175B074E6A11A1CB17466015DCE03B1DAB1288D7EAB1D2A6862"))
        )
      ),
      entries.get(SBPath.parse("/a"))
    );
    assertEquals(
      new SBPackageEntry(
        SBPath.parse("/b"),
        new SBBlob(
          13L,
          TEXT_PLAIN,
          new SBHash(SHA2_256,
                     unhex(
                       "5891B5B522D5DF086D0FF0B110FBD9D21BB4FC7163AF34D08286A2E846F6BE03"))
        )
      ),
      entries.get(SBPath.parse("/b"))
    );
    assertEquals(
      new SBPackageEntry(
        SBPath.parse("/c"),
        new SBBlob(
          16L,
          TEXT_PLAIN,
          new SBHash(SHA2_256,
                     unhex(
                       "ABC6FD595FC079D3114D4B71A4D84B1D1D0F79DF1E70F8813212F2A65D8916DF"))
        )
      ),
      entries.get(SBPath.parse("/c"))
    );
    assertEquals(3, entries.size());

    this.roundTrip(pack);
  }

  private void roundTrip(
    final SBPackage pack0)
    throws Exception
  {
    final var out =
      new ByteArrayOutputStream();
    this.serializers.serialize(URI.create("urn:0"), out, pack0);

    LOG.debug("{}", out.toString(UTF_8));

    final var in =
      new ByteArrayInputStream(out.toByteArray());
    final var pack1 =
      this.parsers.parse(URI.create("urn:1"), in);

    assertEquals(pack0, pack1);
  }

  private static byte[] unhex(
    final String text)
  {
    return HexFormat.of().withUpperCase().parseHex(text);
  }
}
