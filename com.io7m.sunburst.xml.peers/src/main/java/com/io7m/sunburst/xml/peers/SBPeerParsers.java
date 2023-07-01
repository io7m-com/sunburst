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

import com.io7m.anethum.api.ParsingException;
import com.io7m.anethum.api.ParseSeverity;
import com.io7m.anethum.api.ParseStatus;
import com.io7m.jlexing.core.LexicalPosition;
import com.io7m.sunburst.model.SBPackageIdentifier;
import com.io7m.sunburst.model.SBPeer;
import com.io7m.sunburst.model.SBPeerException;
import com.io7m.sunburst.xml.peers.jaxb.Peer;
import com.io7m.sunburst.xml.peers.jaxb.Peers;
import com.io7m.verona.core.Version;
import com.io7m.verona.core.VersionQualifier;
import jakarta.xml.bind.JAXBContext;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import static jakarta.xml.bind.ValidationEvent.ERROR;
import static jakarta.xml.bind.ValidationEvent.FATAL_ERROR;
import static jakarta.xml.bind.ValidationEvent.WARNING;

/**
 * The default peer parsers.
 */

public final class SBPeerParsers
  implements SBPeerParserFactoryType
{
  /**
   * The default peer parsers.
   */

  public SBPeerParsers()
  {

  }

  @Override
  public SBPeerParserType createParserWithContext(
    final Void context,
    final URI source,
    final InputStream stream,
    final Consumer<ParseStatus> statusConsumer)
  {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(stream, "stream");
    Objects.requireNonNull(statusConsumer, "statusConsumer");

    return new Parser(source, stream, statusConsumer);
  }

  private static final class Parser implements SBPeerParserType
  {
    private final URI source;
    private final InputStream stream;
    private final Consumer<ParseStatus> statusConsumer;

    Parser(
      final URI inSource,
      final InputStream inStream,
      final Consumer<ParseStatus> inStatusConsumer)
    {
      this.source =
        Objects.requireNonNull(inSource, "source");
      this.stream =
        Objects.requireNonNull(inStream, "stream");
      this.statusConsumer =
        Objects.requireNonNull(inStatusConsumer, "statusConsumer");
    }

    @Override
    public List<SBPeer> execute()
      throws ParsingException
    {
      final var errors = new ArrayList<ParseStatus>();

      try {
        final var schemas =
          SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        final var schema =
          schemas.newSchema(
            SBPeerParsers.class.getResource(
              "/com/io7m/sunburst/xml/peers/peer-1.xsd")
          );

        final var context =
          JAXBContext.newInstance("com.io7m.sunburst.xml.peers.jaxb");
        final var unmarshaller =
          context.createUnmarshaller();

        unmarshaller.setEventHandler(event -> {
          try {
            final var status =
              ParseStatus.builder("xml", event.getMessage())
                .withSeverity(
                  switch (event.getSeverity()) {
                    case WARNING -> ParseSeverity.PARSE_WARNING;
                    case ERROR -> ParseSeverity.PARSE_ERROR;
                    case FATAL_ERROR -> ParseSeverity.PARSE_ERROR;
                    default -> ParseSeverity.PARSE_ERROR;
                  })
                .withLexical(LexicalPosition.of(
                  event.getLocator().getLineNumber(),
                  event.getLocator().getColumnNumber(),
                  Optional.of(event.getLocator().getURL().toURI())
                )).build();

            errors.add(status);
            this.statusConsumer.accept(status);
          } catch (final URISyntaxException e) {
            // Nothing we can do about it
          }
          return true;
        });
        unmarshaller.setSchema(schema);

        final var streamSource =
          new StreamSource(this.stream, this.source.toString());
        final var unmarshalled =
          (Peers) unmarshaller.unmarshal(streamSource);

        if (!errors.isEmpty()) {
          throw new IllegalStateException();
        }

        return processPeers(unmarshalled);
      } catch (final Exception e) {
        throw new ParsingException("Parsing failed.", List.copyOf(errors));
      }
    }

    private static List<SBPeer> processPeers(
      final Peers ps)
      throws SBPeerException
    {
      final var results = new ArrayList<SBPeer>();
      for (final var p : ps.getPeer()) {
        results.add(processPeer(p));
      }
      return List.copyOf(results);
    }

    private static SBPeer processPeer(
      final Peer p)
      throws SBPeerException
    {
      final var b =
        SBPeer.builder(p.getName());

      for (final var i : p.getImport()) {
        final var iv = i.getQualifier();
        final Optional<VersionQualifier> q;
        if (iv != null) {
          q = Optional.of(new VersionQualifier(iv));
        } else {
          q = Optional.empty();
        }

        b.addImport(
          new SBPackageIdentifier(
            i.getName(),
            new Version(
              (int) i.getMajor(),
              (int) i.getMinor(),
              (int) i.getPatch(),
              q
            )
          )
        );
      }
      return b.build();
    }

    @Override
    public void close()
      throws IOException
    {
      this.stream.close();
    }
  }
}
