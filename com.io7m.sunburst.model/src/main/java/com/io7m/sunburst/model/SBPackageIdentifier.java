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
import com.io7m.verona.core.VersionException;
import com.io7m.verona.core.VersionParser;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A package identifier.
 *
 * @param name    The name
 * @param version The version
 */

public record SBPackageIdentifier(
  String name,
  Version version)
  implements Comparable<SBPackageIdentifier>
{
  /**
   * A package identifier.
   *
   * @param name    The name
   * @param version The version
   */

  public SBPackageIdentifier
  {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(version, "version");
    SBPackageNames.check(name);
  }

  /**
   * @return The package name as an ordered list of segments
   */

  public List<String> nameSegments()
  {
    return List.of(this.name.split("\\."));
  }

  @Override
  public String toString()
  {
    return "%s:%s".formatted(this.name, this.version);
  }

  @Override
  public int compareTo(
    final SBPackageIdentifier other)
  {
    return Comparator.comparing(SBPackageIdentifier::name)
      .thenComparing(SBPackageIdentifier::version)
      .compare(this, other);
  }

  /**
   * Parse a package identifier.
   *
   * @param text The identifier text
   *
   * @return A package identifier
   */

  public static SBPackageIdentifier parse(
    final String text)
  {
    final var segments = List.of(text.split(":"));
    if (segments.size() != 2) {
      throw new IllegalArgumentException(
        "Received: \"%s\", Expected: <package-name>:<version>"
          .formatted(text));
    }
    try {
      return new SBPackageIdentifier(
        segments.get(0),
        VersionParser.parse(segments.get(1))
      );
    } catch (final VersionException e) {
      throw new IllegalArgumentException(e.getMessage(), e.getCause());
    }
  }
}
