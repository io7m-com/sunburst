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

import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A (semantic) version number.
 *
 * @param major     The major number
 * @param minor     The minor number
 * @param patch     The patch number
 * @param qualifier The (possibly empty) qualifier
 */

public record SBPackageVersion(
  int major,
  int minor,
  int patch,
  String qualifier)
  implements Comparable<SBPackageVersion>
{
  private static final Pattern VALID_VERSION =
    Pattern.compile("([0-9]+)\\.([0-9]+)\\.([0-9]+)(-([A-Za-z_0-9]+))?");
  private static final Pattern VALID_QUALIFIER =
    Pattern.compile("[A-Za-z_0-9]{0,255}");

  private static final Comparator<Integer> COMPARE_UNSIGNED =
    (x, y) -> Integer.compareUnsigned(x.intValue(), y.intValue());

  private static final Comparator<String> COMPARE_QUALIFIER =
    (x, y) -> {
      if (Objects.equals(x, y)) {
        return 0;
      }
      if (x.isEmpty()) {
        return 1;
      }
      if (y.isEmpty()) {
        return -1;
      }
      return x.compareTo(y);
    };

  /**
   * A (semantic) version number.
   *
   * @param major     The major number
   * @param minor     The minor number
   * @param patch     The patch number
   * @param qualifier The (possibly empty) qualifier
   */

  public SBPackageVersion
  {
    Objects.requireNonNull(qualifier, "qualifier");

    final var matcher = VALID_QUALIFIER.matcher(qualifier);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
        "Qualifier '%s' must match '%s'"
          .formatted(qualifier, VALID_QUALIFIER)
      );
    }
  }

  /**
   * Parse a package version.
   *
   * @param text The version text
   *
   * @return A parsed version
   *
   * @throws IllegalArgumentException On errors
   */

  public static SBPackageVersion parse(
    final String text)
    throws IllegalArgumentException
  {
    final var matcher = VALID_VERSION.matcher(text);
    if (matcher.matches()) {
      return new SBPackageVersion(
        Integer.parseUnsignedInt(matcher.group(1)),
        Integer.parseUnsignedInt(matcher.group(2)),
        Integer.parseUnsignedInt(matcher.group(3)),
        Objects.requireNonNullElse(matcher.group(5), "")
      );
    }

    throw new IllegalArgumentException(
      "Version '%s' must match the pattern '%s'"
        .formatted(text, VALID_VERSION)
    );
  }

  /**
   * @return {@code true} if this version is a snapshot version
   */

  public boolean isSnapshot()
  {
    return Objects.equals(this.qualifier, "SNAPSHOT");
  }

  @Override
  public String toString()
  {
    final var text = new StringBuilder(32);
    text.append(Integer.toUnsignedString(this.major));
    text.append('.');
    text.append(Integer.toUnsignedString(this.minor));
    text.append('.');
    text.append(Integer.toUnsignedString(this.patch));
    if (!this.qualifier.isEmpty()) {
      text.append('-');
      text.append(this.qualifier);
    }
    return text.toString();
  }

  @Override
  public int compareTo(
    final SBPackageVersion other)
  {
    return Comparator.comparing(SBPackageVersion::major, COMPARE_UNSIGNED)
      .thenComparing(SBPackageVersion::minor, COMPARE_UNSIGNED)
      .thenComparing(SBPackageVersion::patch, COMPARE_UNSIGNED)
      .thenComparing(SBPackageVersion::qualifier, COMPARE_QUALIFIER)
      .compare(this, other);
  }
}
