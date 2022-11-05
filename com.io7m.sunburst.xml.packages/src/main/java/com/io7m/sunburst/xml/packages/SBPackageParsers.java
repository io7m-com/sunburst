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

import com.io7m.anethum.common.ParseException;
import com.io7m.anethum.common.ParseSeverity;
import com.io7m.anethum.common.ParseStatus;
import com.io7m.jlexing.core.LexicalPosition;
import com.io7m.sunburst.model.SBBlob;
import com.io7m.sunburst.model.SBHash;
import com.io7m.sunburst.model.SBHashAlgorithm;
import com.io7m.sunburst.model.SBPackage;
import com.io7m.sunburst.model.SBPackageEntry;
import com.io7m.sunburst.model.SBPackageIdentifier;
import com.io7m.sunburst.model.SBPackageVersion;
import com.io7m.sunburst.model.SBPath;
import com.io7m.sunburst.xml.packages.jaxb.Entries;
import com.io7m.sunburst.xml.packages.jaxb.Entry;
import com.io7m.sunburst.xml.packages.jaxb.Identifier;
import com.io7m.sunburst.xml.packages.jaxb.Metadata;
import com.io7m.sunburst.xml.packages.jaxb.Package;
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
import java.util.Objects;
import java.util.Optional;
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
  /**
   * The default package parsers.
   */

  public SBPackageParsers()
  {

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

    return new Parser(source, stream, statusConsumer);
  }

  private static final class Parser implements SBPackageParserType
  {
    private static final HexFormat HEX_FORMAT =
      HexFormat.of()
        .withUpperCase();

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
    public SBPackage execute()
      throws ParseException
    {
      final var errors = new ArrayList<ParseStatus>();

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
              ParseStatus.builder()
                .setErrorCode("xml")
                .setMessage(event.getMessage())
                .setSeverity(
                  switch (event.getSeverity()) {
                    case WARNING -> ParseSeverity.PARSE_WARNING;
                    case ERROR -> ParseSeverity.PARSE_ERROR;
                    case FATAL_ERROR -> ParseSeverity.PARSE_ERROR;
                    default -> ParseSeverity.PARSE_ERROR;
                  })
                .setLexical(LexicalPosition.of(
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

        return processPackage((Package) unmarshaller.unmarshal(streamSource));
      } catch (final Exception e) {
        throw new ParseException("Parsing failed.", List.copyOf(errors));
      }
    }

    private static SBPackage processPackage(
      final Package p)
    {
      return new SBPackage(
        processIdentifier(p.getIdentifier()),
        processMetadata(p.getMetadata()),
        processEntries(p.getEntries())
      );
    }

    private static SortedMap<SBPath, SBPackageEntry> processEntries(
      final Entries entries)
    {
      final var map = new TreeMap<SBPath, SBPackageEntry>();
      for (final var entry : entries.getEntry()) {
        final SBPackageEntry pEntry = processEntry(entry);
        map.put(pEntry.path(), pEntry);
      }
      return map;
    }

    private static SBPackageEntry processEntry(
      final Entry entry)
    {
      return new SBPackageEntry(
        SBPath.parse(entry.getPath()),
        new SBBlob(
          entry.getSize().longValue(),
          entry.getContentType(),
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
      return new SBPackageIdentifier(
        identifier.getName(),
        new SBPackageVersion(
          (int) version.getMajor(),
          (int) version.getMinor(),
          (int) version.getPatch(),
          Objects.requireNonNullElse(version.getQualifier(), "")
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
