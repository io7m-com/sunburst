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


package com.io7m.sunburst.xml;

import com.io7m.anethum.common.SerializeException;
import com.io7m.sunburst.model.SBPackage;
import com.io7m.sunburst.model.SBPackageEntry;
import com.io7m.sunburst.model.SBPackageIdentifier;
import com.io7m.sunburst.model.SBPath;
import com.io7m.sunburst.xml.pack1.Entries;
import com.io7m.sunburst.xml.pack1.Entry;
import com.io7m.sunburst.xml.pack1.HashAlgorithmT;
import com.io7m.sunburst.xml.pack1.Identifier;
import com.io7m.sunburst.xml.pack1.Metadata;
import com.io7m.sunburst.xml.pack1.ObjectFactory;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.util.HexFormat;
import java.util.Objects;
import java.util.SortedMap;

import static java.lang.Boolean.TRUE;
import static java.lang.Integer.toUnsignedLong;

/**
 * The default package serializers.
 */

public final class SBPackageSerializers
  implements SBPackageSerializerFactoryType
{
  /**
   * The default package serializers.
   */

  public SBPackageSerializers()
  {

  }

  @Override
  public SBPackageSerializerType createSerializerWithContext(
    final Void context,
    final URI target,
    final OutputStream stream)
  {
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(stream, "stream");
    return new Serializer(target, stream);
  }

  private static final class Serializer implements SBPackageSerializerType
  {
    private static final HexFormat HEX_FORMAT =
      HexFormat.of()
        .withUpperCase();

    private final URI target;
    private final OutputStream stream;
    private final ObjectFactory objects;

    private Serializer(
      final URI inTarget,
      final OutputStream inStream)
    {
      this.target =
        Objects.requireNonNull(inTarget, "target");
      this.stream =
        Objects.requireNonNull(inStream, "stream");
      this.objects =
        new ObjectFactory();
    }

    @Override
    public void execute(
      final SBPackage value)
      throws SerializeException
    {
      try {
        final var p =
          this.objects.createPackage();

        p.setIdentifier(this.processIdentifier(value.identifier()));
        if (!value.metadata().isEmpty()) {
          p.setMetadata(this.processMetadata(value.metadata()));
        }
        p.setEntries(this.processEntries(value.entries()));

        final var context =
          JAXBContext.newInstance("com.io7m.sunburst.xml.pack1");
        final var marshaller =
          context.createMarshaller();

        marshaller.setProperty("jaxb.formatted.output", TRUE);
        marshaller.marshal(p, this.stream);
      } catch (final JAXBException e) {
        throw new SerializeException(e.getMessage(), e);
      }
    }

    private Entries processEntries(
      final SortedMap<SBPath, SBPackageEntry> entries)
    {
      final var e = this.objects.createEntries();
      final var ee = e.getEntry();
      for (final var pe : entries.values()) {
        final var re = this.processEntry(pe);
        ee.add(re);
      }
      return e;
    }

    private Entry processEntry(
      final SBPackageEntry pe)
    {
      final var e = this.objects.createEntry();
      final var blob = pe.blob();
      e.setContentType(blob.contentType());
      e.setHashAlgorithm(
        switch (blob.hash().algorithm()) {
          case SHA2_256 -> HashAlgorithmT.SHA_2_256;
        }
      );
      e.setSize(BigInteger.valueOf(blob.size()));
      e.setHashValue(HEX_FORMAT.formatHex(blob.hash().value()));
      e.setPath(pe.path().toString());
      return e;
    }

    private Metadata processMetadata(
      final SortedMap<String, String> metadata)
    {
      final var m = this.objects.createMetadata();
      final var mm = m.getMeta();
      for (final var e : metadata.entrySet()) {
        final var me = this.objects.createMeta();
        me.setKey(e.getKey());
        me.setValue(e.getValue());
        mm.add(me);
      }
      return m;
    }

    private Identifier processIdentifier(
      final SBPackageIdentifier identifier)
    {
      final var n = this.objects.createName();
      n.setGroup(identifier.name().groupName());
      n.setName(identifier.name().name());

      final var v = this.objects.createVersion();
      final var iv = identifier.version();
      v.setMajor(toUnsignedLong(iv.major()));
      v.setMinor(toUnsignedLong(iv.minor()));
      v.setPatch(toUnsignedLong(iv.patch()));

      if (!iv.qualifier().isEmpty()) {
        v.setQualifier(iv.qualifier());
      }

      final var id = this.objects.createIdentifier();
      id.setName(n);
      id.setVersion(v);
      return id;
    }

    @Override
    public void close()
      throws IOException
    {
      this.stream.close();
    }
  }
}
