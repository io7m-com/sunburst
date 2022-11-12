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

package com.io7m.sunburst.model;

import com.io7m.verona.core.Version;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_PEER_MISCONFIGURED;

/**
 * A well-formed peer.
 */

public final class SBPeer
{
  private static final String RESOURCE_PATH =
    "/com/io7m/sunburst/model/Messages.properties";

  private final String packageName;
  private final Map<String, Version> imports;

  private SBPeer(
    final String inPackageName,
    final Map<String, Version> inImports)
  {
    this.packageName =
      Objects.requireNonNull(inPackageName, "inPackageName");
    this.imports =
      Objects.requireNonNull(inImports, "imports");
  }

  /**
   * @param packageName The package name
   *
   * @return A new mutable import builder
   */

  public static Builder builder(
    final String packageName)
  {
    return new Builder(Strings.open(), packageName);
  }

  @Override
  public String toString()
  {
    return "[SBPeer %s]".formatted(this.packageName);
  }

  @Override
  public boolean equals(final Object o)
  {
    if (this == o) {
      return true;
    }
    if (o == null || !this.getClass().equals(o.getClass())) {
      return false;
    }
    final SBPeer peer = (SBPeer) o;
    return this.packageName.equals(peer.packageName)
           && this.imports.equals(peer.imports);
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(this.packageName, this.imports);
  }

  /**
   * @return The package name
   */

  public String packageName()
  {
    return this.packageName;
  }

  /**
   * @return The map of package names to package versions
   */

  public Map<String, Version> imports()
  {
    return this.imports;
  }

  /**
   * @return The imports as a set of identifiers
   */

  public Set<SBPackageIdentifier> importSet()
  {
    return this.imports.entrySet()
      .stream()
      .map(e -> new SBPackageIdentifier(e.getKey(), e.getValue()))
      .collect(Collectors.toUnmodifiableSet());
  }

  private static final class Strings
  {
    private final Properties properties;

    Strings(
      final Properties inProperties)
    {
      this.properties =
        Objects.requireNonNull(inProperties, "properties");
    }

    public static Strings open()
    {
      try {
        final var properties = new Properties();
        try (var stream =
               SBPeer.class.getResourceAsStream(RESOURCE_PATH)) {
          properties.load(stream);
        }
        return new Strings(properties);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    public String format(
      final String pattern,
      final Object... args)
    {
      final var text = this.properties.getProperty(pattern);
      if (text == null) {
        throw new IllegalArgumentException(
          "No such string resource: %s".formatted(pattern)
        );
      }
      return MessageFormat.format(text, args);
    }
  }

  /**
   * A mutable import builder.
   */

  public static final class Builder
  {
    private final Strings strings;
    private final String packageName;
    private final HashMap<String, Version> imports;
    private final ArrayList<String> problems;

    private Builder(
      final Strings inStrings,
      final String inPackageName)
    {
      this.strings =
        Objects.requireNonNull(inStrings, "strings");
      this.packageName =
        Objects.requireNonNull(inPackageName, "packageName");
      this.imports = new HashMap<>();
      this.problems = new ArrayList<String>();
    }

    /**
     * Add an import to the set.
     *
     * @param identifier The identifier
     *
     * @return A new import
     */

    public Builder addImport(
      final SBPackageIdentifier identifier)
    {
      Objects.requireNonNull(identifier, "identifier");

      final var name =
        identifier.name();
      final var existing =
        this.imports.get(name);

      if (existing != null) {
        this.problems.add(
          this.strings.format(
            "errorImportConflict",
            identifier,
            identifier.version(),
            existing
          )
        );
      } else {
        this.imports.put(name, identifier.version());
      }

      return this;
    }

    /**
     * Add an import to the set.
     *
     * @param text The text to parse as an identifier
     *
     * @return A new import
     */

    public Builder addImportText(
      final String text)
    {
      Objects.requireNonNull(text, "text");

      try {
        this.addImport(SBPackageIdentifier.parse(text));
      } catch (final Exception e) {
        this.problems.add(
          this.strings.format("errorImportParse", e.getMessage())
        );
      }

      return this;
    }

    /**
     * Build the peer.
     *
     * @return The peer
     *
     * @throws SBPeerException If the peer would not be well-formed
     */

    public SBPeer build()
      throws SBPeerException
    {
      try {
        SBPackageNames.check(this.packageName);
      } catch (final Exception e) {
        this.problems.add(e.getMessage());
      }

      if (!this.problems.isEmpty()) {
        throw new SBPeerException(
          ERROR_PEER_MISCONFIGURED,
          this.strings.format("errorImportGeneral"),
          this.problems
        );
      }

      return new SBPeer(this.packageName, Map.copyOf(this.imports));
    }
  }
}
