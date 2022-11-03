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
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A package name consisting of a group name and a name.
 *
 * @param groupName The group name
 * @param name      The name
 */

public record SBPackageName(
  String groupName,
  String name)
  implements Comparable<SBPackageName>
{
  private static final Pattern VALID_NAME_SEGMENT =
    Pattern.compile("[a-z][a-z0-9_-]*");

  /**
   * A package name consisting of a group name and a name.
   *
   * @param groupName The group name
   * @param name      The name
   */

  public SBPackageName
  {
    Objects.requireNonNull(groupName, "groupName");
    Objects.requireNonNull(name, "name");

    {
      final var segments = List.of(groupName.split("\\."));
      if (groupName.length() > 255 || segments.isEmpty()) {
        throw groupInvalid(groupName);
      }

      for (final var segment : segments) {
        if (!VALID_NAME_SEGMENT.matcher(segment).matches()) {
          throw groupInvalid(groupName);
        }
      }
    }

    {
      final var segments = List.of(name.split("\\."));
      if (name.length() > 255 || segments.isEmpty()) {
        throw nameInvalid(name);
      }

      for (final var segment : segments) {
        if (!VALID_NAME_SEGMENT.matcher(segment).matches()) {
          throw nameInvalid(name);
        }
      }
    }
  }

  private static IllegalArgumentException groupInvalid(
    final String text)
  {
    return new IllegalArgumentException(
      String.format(
        "Group name '%s' must consist of >= 1 repetitions of %s, and be <= 255 characters long",
        text,
        VALID_NAME_SEGMENT
      )
    );
  }

  private static IllegalArgumentException nameInvalid(
    final String text)
  {
    return new IllegalArgumentException(
      String.format(
        "Name '%s' must consist of >= 1 repetitions of %s, and be <= 255 characters long",
        text,
        VALID_NAME_SEGMENT
      )
    );
  }

  @Override
  public String toString()
  {
    return "%s:%s".formatted(this.groupName, this.name);
  }

  @Override
  public int compareTo(
    final SBPackageName other)
  {
    return Comparator.comparing(SBPackageName::groupName)
      .thenComparing(SBPackageName::name)
      .compare(this, other);
  }
}
