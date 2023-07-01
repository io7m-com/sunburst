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


package com.io7m.sunburst.xml.peers;

import com.io7m.anethum.api.SerializationException;
import com.io7m.sunburst.model.SBPeer;
import com.io7m.sunburst.xml.peers.jaxb.Import;
import com.io7m.sunburst.xml.peers.jaxb.ObjectFactory;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.Objects;

import static java.lang.Boolean.TRUE;
import static java.lang.Integer.toUnsignedLong;

/**
 * The default peer serializers.
 */

public final class SBPeerSerializers
  implements SBPeerSerializerFactoryType
{
  /**
   * The default peer serializers.
   */

  public SBPeerSerializers()
  {

  }

  @Override
  public SBPeerSerializerType createSerializerWithContext(
    final Void context,
    final URI target,
    final OutputStream stream)
  {
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(stream, "stream");
    return new Serializer(target, stream);
  }

  private static final class Serializer implements SBPeerSerializerType
  {
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
      final List<SBPeer> value)
      throws SerializationException
    {
      try {
        final var peers =
          this.objects.createPeers();
        final var peersOut =
          peers.getPeer();

        for (final var v : value) {
          final var p = this.objects.createPeer();
          p.setName(v.packageName());

          final var importList = p.getImport();
          for (final var e : v.imports().entrySet()) {
            final var i = new Import();
            i.setName(e.getKey());

            final var version = e.getValue();
            i.setMajor(toUnsignedLong(version.major()));
            i.setMinor(toUnsignedLong(version.minor()));
            i.setPatch(toUnsignedLong(version.patch()));
            version.qualifier().ifPresent(q -> i.setQualifier(q.text()));

            importList.add(i);
          }

          peersOut.add(p);
        }

        final var context =
          JAXBContext.newInstance("com.io7m.sunburst.xml.peers.jaxb");
        final var marshaller =
          context.createMarshaller();

        marshaller.setProperty("jaxb.formatted.output", TRUE);
        marshaller.marshal(peers, this.stream);
      } catch (final JAXBException e) {
        throw new SerializationException(e.getMessage(), e);
      }
    }

    @Override
    public void close()
      throws IOException
    {
      this.stream.close();
    }
  }
}
