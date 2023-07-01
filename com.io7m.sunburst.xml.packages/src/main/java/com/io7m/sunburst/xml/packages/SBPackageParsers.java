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

import com.io7m.anethum.api.ParsingException;
import com.io7m.anethum.api.ParseSeverity;
import com.io7m.anethum.api.ParseStatus;
import com.io7m.jlexing.core.LexicalPosition;
import com.io7m.mime2045.core.MimeType;
import com.io7m.mime2045.parser.api.MimeParseException;
import com.io7m.mime2045.parser.api.MimeParserFactoryType;
import com.io7m.mime2045.parser.api.MimeParserType;
import com.io7m.sunburst.model.SBBlob;
import com.io7m.sunburst.model.SBHash;
import com.io7m.sunburst.model.SBHashAlgorithm;
import com.io7m.sunburst.model.SBPackage;
import com.io7m.sunburst.model.SBPackageEntry;
import com.io7m.sunburst.model.SBPackageIdentifier;
import com.io7m.sunburst.model.SBPath;
import com.io7m.sunburst.xml.packages.jaxb.Entries;
import com.io7m.sunburst.xml.packages.jaxb.Entry;
import com.io7m.sunburst.xml.packages.jaxb.Identifier;
import com.io7m.sunburst.xml.packages.jaxb.Metadata;
import com.io7m.sunburst.xml.packages.jaxb.Package;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;

import static jakarta.xml.bind.ValidationEvent.ERROR;
import static jakarta.xml.bind.ValidationEvent.FATAL_ERROR;
import static jakarta.xml.bind.ValidationEvent.WARNING;

/**
 * The default package parsers.
 */

public final class SBPackageParsers
  implements SBPackageParserFactoryType
{
  private final MimeParserFactoryType mimeParsers;

  /**
   * The default package parsers.
   */

  public SBPackageParsers()
  {
    this(
      ServiceLoader.load(MimeParserFactoryType.class)
        .findFirst()
        .orElseThrow(() -> {
          return new ServiceConfigurationError(
            "No services available of type %s"
              .formatted(MimeParserFactoryType.class)
          );
        })
    );
  }

  /**
   * The default package parsers.
   *
   * @param inMimeParsers A factory of MIME parsers
   */

  public SBPackageParsers(
    final MimeParserFactoryType inMimeParsers)
  {
    this.mimeParsers =
      Objects.requireNonNull(inMimeParsers, "mimeParsers");
  }

  @Override
  public SBPackageParserType createParserWithContext(
    final Void context,
    final URI source,
    final InputStream stream,
    final Consumer<ParseStatus> statusConsumer)
  {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(stream, "stream");
    Objects.requireNonNull(statusConsumer, "statusConsumer");

    return new Parser(
      source,
      stream,
      statusConsumer,
      this.mimeParsers.create(Map.of())
    );
  }

  private static final class Parser implements SBPackageParserType
  {
    private static final HexFormat HEX_FORMAT =
      HexFormat.of()
        .withUpperCase();

    private final URI source;
    private final InputStream stream;
    private final Consumer<ParseStatus> statusConsumer;
    private final MimeParserType mimeParser;
    private ArrayList<ParseStatus> errors;

    Parser(
      final URI inSource,
      final InputStream inStream,
      final Consumer<ParseStatus> inStatusConsumer,
      final MimeParserType mimeParserType)
    {
      this.source =
        Objects.requireNonNull(inSource, "source");
      this.stream =
        Objects.requireNonNull(inStream, "stream");
      this.statusConsumer =
        Objects.requireNonNull(inStatusConsumer, "statusConsumer");
      this.mimeParser =
        Objects.requireNonNull(mimeParserType, "mimeParserType");
      this.errors =
        new ArrayList<ParseStatus>();
    }

    @Override
    public SBPackage execute()
      throws ParsingException
    {
      this.errors.clear();

      try {
        final var schemas =
          SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        final var schema =
          schemas.newSchema(
            SBPackageParsers.class.getResource(
              "/com/io7m/sunburst/xml/packages/package-1.xsd")
          );

        final var context =
          JAXBContext.newInstance("com.io7m.sunburst.xml.packages.jaxb");
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

            this.errors.add(status);
            this.statusConsumer.accept(status);
          } catch (final URISyntaxException e) {
            // Nothing we can do about it
          }
          return true;
        });
        unmarshaller.setSchema(schema);

        final var streamSource =
          new StreamSource(this.stream, this.source.toString());

        return this.processPackage((Package) unmarshaller.unmarshal(streamSource));
      } catch (final Exception e) {
        throw new ParsingException("Parsing failed.", List.copyOf(this.errors));
      }
    }

    private SBPackage processPackage(
      final Package p)
      throws MimeParseException
    {
      return new SBPackage(
        processIdentifier(p.getIdentifier()),
        processMetadata(p.getMetadata()),
        this.processEntries(p.getEntries())
      );
    }

    private SortedMap<SBPath, SBPackageEntry> processEntries(
      final Entries entries)
      throws MimeParseException
    {
      final var map = new TreeMap<SBPath, SBPackageEntry>();
      for (final var entry : entries.getEntry()) {
        final SBPackageEntry pEntry = this.processEntry(entry);
        map.put(pEntry.path(), pEntry);
      }
      return map;
    }

    private SBPackageEntry processEntry(
      final Entry entry)
      throws MimeParseException
    {
      final MimeType type;

      try {
        type = this.mimeParser.parse(entry.getContentType());
      } catch (final MimeParseException e) {
        final var status =
          ParseStatus.builder("error-mine", e.getMessage())
            .withSeverity(ParseSeverity.PARSE_ERROR)
            .withLexical(e.lexical())
            .build();

        this.errors.add(status);
        this.statusConsumer.accept(status);
        throw e;
      }

      return new SBPackageEntry(
        SBPath.parse(entry.getPath()),
        new SBBlob(
          entry.getSize().longValue(),
          type,
          new SBHash(
            SBHashAlgorithm.valueOf(entry.getHashAlgorithm().value()),
            HEX_FORMAT.parseHex(entry.getHashValue())
          )
        )
      );
    }

    private static SortedMap<String, String> processMetadata(
      final Metadata metadata)
    {
      final var map = new TreeMap<String, String>();
      if (metadata != null) {
        for (final var entry : metadata.getMeta()) {
          map.put(entry.getKey(), entry.getValue());
        }
      }
      return map;
    }

    private static SBPackageIdentifier processIdentifier(
      final Identifier identifier)
    {
      final var version = identifier.getVersion();

      final var qs = version.getQualifier();
      final Optional<VersionQualifier> q;
      if (qs != null) {
        q = Optional.of(new VersionQualifier(qs));
      } else {
        q = Optional.empty();
      }

      return new SBPackageIdentifier(
        identifier.getName(),
        new Version(
          (int) version.getMajor(),
          (int) version.getMinor(),
          (int) version.getPatch(),
          q
        )
      );
    }

    @Override
    public void close()
      throws IOException
    {
      this.stream.close();
    }
  }
}
