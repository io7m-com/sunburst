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

import com.io7m.sunburst.model.SBPackageVersion;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class SBPackageVersionTest
{
  /**
   * @return A set of invalid version tests
   */

  @TestFactory
  public Stream<DynamicTest> testInvalidVersions()
  {
    return Stream.of(
      "",
      "1",
      "1.0",
      "1.a",
      "1.0.0-",
      "4294967296.0.0",
      "0.4294967296.0",
      "0.0.4294967296",
      "1.0.0-β"
    ).map(SBPackageVersionTest::invalidVersionTestOf);
  }

  /**
   * @return A set of valid version tests
   */

  @TestFactory
  public Stream<DynamicTest> testValidVersions()
  {
    return Stream.of(
      "1.0.0",
      "1.0.0-SNAPSHOT"
    ).map(SBPackageVersionTest::validVersionTestOf);
  }

  /**
   * An invalid version.
   */

  @Test
  public void testInvalidQualifier0()
  {
    assertThrows(IllegalArgumentException.class, () -> {
      new SBPackageVersion(1, 0, 0, "β");
    });
  }

  /**
   * Version ordering.
   */

  @Test
  public void testOrdering0()
  {
    {
      final var v0 = SBPackageVersion.parse("1.0.0");
      final var v1 = SBPackageVersion.parse("1.0.0");
      assertEquals(0, v0.compareTo(v1));
    }

    {
      final var v0 = SBPackageVersion.parse("2.0.0");
      final var v1 = SBPackageVersion.parse("1.0.0");
      assertTrue(v0.compareTo(v1) > 0);
      assertTrue(v1.compareTo(v0) < 0);
    }

    {
      final var v0 = SBPackageVersion.parse("1.2.0");
      final var v1 = SBPackageVersion.parse("1.0.0");
      assertTrue(v0.compareTo(v1) > 0);
      assertTrue(v1.compareTo(v0) < 0);
    }

    {
      final var v0 = SBPackageVersion.parse("1.0.2");
      final var v1 = SBPackageVersion.parse("1.0.0");
      assertTrue(v0.compareTo(v1) > 0);
      assertTrue(v1.compareTo(v0) < 0);
    }

    {
      final var v0 = SBPackageVersion.parse("1.0.0");
      final var v1 = SBPackageVersion.parse("1.0.0-SNAPSHOT");
      assertTrue(v0.compareTo(v1) > 0);
      assertTrue(v1.compareTo(v0) < 0);
    }

    {
      final var v0 = SBPackageVersion.parse("1.0.0-SNAPSHOT");
      final var v1 = SBPackageVersion.parse("1.0.0-SNAPSHOT");
      assertEquals(0, v0.compareTo(v1));
    }

    {
      final var v0 = SBPackageVersion.parse("1.0.0-B");
      final var v1 = SBPackageVersion.parse("1.0.0-A");
      assertTrue(v0.compareTo(v1) > 0);
      assertTrue(v1.compareTo(v0) < 0);
    }
  }

  private static DynamicTest invalidVersionTestOf(
    final String text)
  {
    return DynamicTest.dynamicTest(
      "testInvalidVersion_%s".formatted(text),
      () -> {
        assertThrows(IllegalArgumentException.class, () -> {
          SBPackageVersion.parse(text);
        });
      });
  }

  private static DynamicTest validVersionTestOf(
    final String text)
  {
    return DynamicTest.dynamicTest(
      "testValidVersion_%s".formatted(text),
      () -> {
        final var p0 = SBPackageVersion.parse(text);
        final var p1 = SBPackageVersion.parse(text);
        assertEquals(p0, p1);
        assertEquals(p0, p0);
        assertEquals(p0.hashCode(), p1.hashCode());
        assertEquals(p0.toString(), p1.toString());
        assertEquals(text, p0.toString());
        assertEquals(0, p0.compareTo(p1));
        assertNotEquals(p0, Integer.valueOf(23));
      });
  }
}
