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

import com.io7m.anethum.common.ParseException;
import com.io7m.anethum.common.ParseStatus;
import com.io7m.sunburst.model.SBPeer;
import com.io7m.sunburst.xml.peers.SBPeerParsers;
import com.io7m.sunburst.xml.peers.SBPeerSerializers;
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class SBPeerParsersTest
{
  private static final Logger LOG =
    LoggerFactory.getLogger(SBPeerParsersTest.class);

  private SBPeerParsers parsers;
  private SBPeerSerializers serializers;
  private Path directory;
  private ArrayList<ParseStatus> errors;

  @BeforeEach
  public void setup()
    throws IOException
  {
    this.parsers =
      new SBPeerParsers();
    this.serializers =
      new SBPeerSerializers();
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
        SBPeerParsersTest.class,
        this.directory,
        "hello.txt"
      );

    assertThrows(ParseException.class, () -> {
      this.parsers.parseFile(file, SBPeerParsersTest::onStatus);
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
        SBPeerParsersTest.class,
        this.directory,
        "peer0_invalid.xml"
      );

    assertThrows(ParseException.class, () -> {
      this.parsers.parseFile(file, SBPeerParsersTest::onStatus);
    });
  }

  @Test
  public void testInvalid1()
    throws Exception
  {
    final var file =
      SBTestDirectories.resourceOf(
        SBPeerParsersTest.class,
        this.directory,
        "peer1_invalid.xml"
      );

    assertThrows(ParseException.class, () -> {
      this.parsers.parseFile(file, SBPeerParsersTest::onStatus);
    });
  }

  @Test
  public void testPeer0()
    throws Exception
  {
    final var file =
      SBTestDirectories.resourceOf(
        SBPeerParsersTest.class,
        this.directory,
        "peer0.xml"
      );

    final var pack =
      this.parsers.parseFile(file, parseStatus -> {
        LOG.debug("{}", parseStatus);
        this.errors.add(parseStatus);
      });

    this.roundTrip(pack);
  }

  private void roundTrip(
    final List<SBPeer> pack0)
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
}
