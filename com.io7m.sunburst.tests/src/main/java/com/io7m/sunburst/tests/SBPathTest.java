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


package com.io7m.sunburst.tests;

import com.io7m.sunburst.model.SBPath;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class SBPathTest
{
  /**
   * The root path is a single slash.
   */

  @Test
  public void testRoot()
  {
    assertEquals("/", SBPath.root().toString());
  }

  /**
   * The root path is a single slash.
   */

  @Test
  public void testPlus()
  {
    final var path =
      SBPath.root()
        .plus("a")
        .plus("b")
        .plus("c");

    assertEquals("/a/b/c", path.toString());
    assertEquals(List.of("a", "b", "c"), path.elements());

    final var ps = new ArrayList<>();
    for (final var p : path) {
      ps.add(p);
    }

    assertEquals(List.of("a", "b", "c"), ps);
  }

  /**
   * @return A set of invalid path tests
   */

  @TestFactory
  public Stream<DynamicTest> testInvalidPaths()
  {
    return Stream.of(
      "",
      "/A",
      "A",
      "a",
      "/abcdefgh/abcdefgh/abcdefgh/abcdefgh/abcdefgh/abcdefgh/abcdefgh/"
      + "abcdefgh/abcdefgh/abcdefgh/abcdefgh/abcdefgh/abcdefgh/abcdefgh/"
      + "abcdefgh/abcdefgh/abcdefgh/abcdefgh/abcdefgh/abcdefgh/abcdefgh/"
      + "abcdefgh/abcdefgh/abcdefgh/abcdefgh/abcdefgh/abcdefgh/abcdefgh/abc"
    ).map(SBPathTest::invalidPathTestOf);
  }

  /**
   * @return A set of valid path tests
   */

  @TestFactory
  public Stream<DynamicTest> testValidPaths()
  {
    return Stream.of(
      "/a",
      "////////////////////////////////////////////////////////////////a"
    ).map(SBPathTest::validPathTestOf);
  }

  private static DynamicTest invalidPathTestOf(
    final String path)
  {
    return DynamicTest.dynamicTest(
      "testInvalidPath_%s".formatted(path),
      () -> {
        assertThrows(IllegalArgumentException.class, () -> {
          SBPath.parse(path);
        });
      });
  }

  private static DynamicTest validPathTestOf(
    final String path)
  {
    return DynamicTest.dynamicTest(
      "testValidPath_%s".formatted(path),
      () -> {
        final var p0 = SBPath.parse(path);
        final var p1 = SBPath.parse(path);
        assertEquals(p0, p1);
        assertEquals(p0, p0);
        assertEquals(p0.hashCode(), p1.hashCode());
        assertNotEquals(p0, Integer.valueOf(23));
      });
  }
}
