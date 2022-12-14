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


package com.io7m.sunburst.xml.packages;

import com.io7m.anethum.common.SerializeException;
import com.io7m.sunburst.model.SBPackage;
import com.io7m.sunburst.model.SBPackageEntry;
import com.io7m.sunburst.model.SBPackageIdentifier;
import com.io7m.sunburst.model.SBPath;
import com.io7m.sunburst.xml.packages.jaxb.Entries;
import com.io7m.sunburst.xml.packages.jaxb.Entry;
import com.io7m.sunburst.xml.packages.jaxb.HashAlgorithmT;
import com.io7m.sunburst.xml.packages.jaxb.Identifier;
import com.io7m.sunburst.xml.packages.jaxb.Metadata;
import com.io7m.sunburst.xml.packages.jaxb.ObjectFactory;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

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
          JAXBContext.newInstance("com.io7m.sunburst.xml.packages.jaxb");
        final var marshaller =
          context.createMarshaller();

        marshaller.setProperty("jaxb.formatted.output", TRUE);
        marshaller.marshal(p, this.stream);
      } catch (final JAXBException e) {
        throw new SerializeException(e.getMessage(), e);
      }
    }

    private Entries processEntries(
      final Map<SBPath, SBPackageEntry> entries)
    {
      final var e = this.objects.createEntries();
      final var ee = e.getEntry();

      final var sorted = new TreeSet<>(entries.keySet());
      for (final var path : sorted) {
        final var pe = entries.get(path);
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
      e.setContentType(blob.contentType().toString());
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
      final Map<String, String> metadata)
    {
      final var m = this.objects.createMetadata();
      final var mm = m.getMeta();

      final var keys = metadata.keySet();
      for (final var key : keys) {
        final var val = metadata.get(key);
        final var me = this.objects.createMeta();
        me.setKey(key);
        me.setValue(val);
        mm.add(me);
      }
      return m;
    }

    private Identifier processIdentifier(
      final SBPackageIdentifier identifier)
    {
      final var v = this.objects.createVersion();
      final var iv = identifier.version();
      v.setMajor(toUnsignedLong(iv.major()));
      v.setMinor(toUnsignedLong(iv.minor()));
      v.setPatch(toUnsignedLong(iv.patch()));
      iv.qualifier().ifPresent(q -> v.setQualifier(q.text()));

      final var id = this.objects.createIdentifier();
      id.setName(identifier.name());
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
