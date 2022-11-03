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

import com.io7m.sunburst.model.SBPackageName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class SBPackageNameTest
{
  record Case(String groupName, String name)
  {
  }

  /**
   * @return A set of invalid name tests
   */

  @TestFactory
  public Stream<DynamicTest> testInvalidNames()
  {
    return Stream.of(
      new Case("", ""),
      new Case("A", "a"),
      new Case("a", "A"),
      new Case(
        "abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh."
        + "abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh."
        + "abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh."
        + "abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcd",
        "a"),
      new Case(
        "a",
        "abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh."
        + "abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh."
        + "abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh."
        + "abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcdefgh.abcd")
    ).map(SBPackageNameTest::invalidNameTestOf);
  }

  /**
   * @return A set of valid name tests
   */

  @TestFactory
  public Stream<DynamicTest> testValidNames()
  {
    return Stream.of(
      new Case("a", "a"),
      new Case("com.io7m.example", "com.io7m.example")
    ).map(SBPackageNameTest::validNameTestOf);
  }

  private static DynamicTest invalidNameTestOf(
    final Case caseV)
  {
    return DynamicTest.dynamicTest(
      "testInvalidName_%s".formatted(caseV),
      () -> {
        assertThrows(IllegalArgumentException.class, () -> {
          new SBPackageName(caseV.groupName, caseV.name);
        });
      });
  }

  private static DynamicTest validNameTestOf(
    final Case caseV)
  {
    return DynamicTest.dynamicTest(
      "testValidName_%s".formatted(caseV),
      () -> {
        final var p0 = new SBPackageName(caseV.groupName, caseV.name);
        final var p1 = new SBPackageName(caseV.groupName, caseV.name);
        assertEquals(p0, p1);
        assertEquals(p0, p0);
        assertEquals(p0.hashCode(), p1.hashCode());
        assertEquals(p0.toString(), p1.toString());
        assertEquals(0, p0.compareTo(p1));
        assertNotEquals(p0, Integer.valueOf(23));
      });
  }
}
