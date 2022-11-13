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

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A normalized path within a package.
 */

public final class SBPath implements Iterable<String>, Comparable<SBPath>
{
  private static final Pattern VALID_FILENAME =
    Pattern.compile("[a-z0-9_\\.-]+");
  private static final Pattern SLASHES =
    Pattern.compile("/+");

  private static final SBPath ROOT =
    new SBPath(List.of());

  private final List<String> elements;

  private SBPath(
    final List<String> inElements)
  {
    this.elements =
      List.copyOf(Objects.requireNonNull(inElements, "elements"));

    inElements.forEach(SBPath::checkFilename);
    if (this.toString().length() > 255) {
      throw new IllegalArgumentException(
        "The maximum length of paths is 255."
      );
    }
  }

  private static void checkFilename(
    final String element)
  {
    final var matcher = VALID_FILENAME.matcher(element);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
        "Path element '%s' does not match the pattern '%s'"
          .formatted(element, VALID_FILENAME)
      );
    }
  }

  /**
   * @return The root path
   */

  public static SBPath root()
  {
    return ROOT;
  }

  /**
   * Parse a string as a path.
   *
   * @param path The path
   *
   * @return A parsed path
   */

  public static SBPath parse(
    final String path)
  {
    if (!path.startsWith("/")) {
      throw new IllegalArgumentException(
        "Paths must begin with /"
      );
    }

    final var normalize =
      SLASHES.matcher(path)
        .replaceAll("/");

    final var elements =
      Arrays.stream(normalize.split("/"))
        .filter(p -> !p.isEmpty())
        .toList();

    return new SBPath(elements);
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
    final SBPath strings = (SBPath) o;
    return this.elements.equals(strings.elements);
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(this.elements);
  }

  /**
   * Add an element to a path.
   *
   * @param file The element to add to the current path
   *
   * @return The path
   */

  public SBPath plus(
    final String file)
  {
    return new SBPath(
      Stream.concat(this.elements.stream(), Stream.of(file))
        .toList()
    );
  }

  @Override
  public String toString()
  {
    return "/%s".formatted(String.join("/", this.elements));
  }

  @Override
  public Iterator<String> iterator()
  {
    return this.elements.iterator();
  }

  /**
   * @return The path elements
   */

  public List<String> elements()
  {
    return this.elements;
  }

  @Override
  public int compareTo(
    final SBPath other)
  {
    return this.toString().compareTo(other.toString());
  }
}
