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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * A hash value.
 *
 * @param algorithm The hash algorithm
 * @param value     The hash value
 */

public record SBHash(
  SBHashAlgorithm algorithm,
  byte[] value)
  implements Comparable<SBHash>
{
  @Override
  public int compareTo(
    final SBHash other)
  {
    final var ac =
      this.algorithm().compareTo(other.algorithm());
    if (ac == 0) {
      return Arrays.compare(this.value(), other.value());
    }
    return ac;
  }

  @Override
  public String toString()
  {
    return "%s:%s".formatted(
      this.algorithm,
      HexFormat.of().withUpperCase().formatHex(this.value)
    );
  }

  @Override
  public boolean equals(
    final Object o)
  {
    if (this == o) {
      return true;
    }
    if (o == null || !this.getClass().equals(o.getClass())) {
      return false;
    }
    final SBHash sbHash = (SBHash) o;
    return this.algorithm == sbHash.algorithm
           && Arrays.equals(this.value, sbHash.value);
  }

  @Override
  public int hashCode()
  {
    int result = Objects.hash(this.algorithm);
    result = 31 * result + Arrays.hashCode(this.value);
    return result;
  }

  /**
   * Produce a hash of the given input stream.
   *
   * @param algorithm The algorithm
   * @param stream    The input stream
   *
   * @return A hash value
   *
   * @throws IOException On errors
   */

  public static SBHash hashOf(
    final SBHashAlgorithm algorithm,
    final InputStream stream)
    throws IOException
  {
    try {
      final var digest =
        MessageDigest.getInstance(algorithm.jssAlgorithmName());
      final var output = OutputStream.nullOutputStream();
      try (var digestStream = new DigestOutputStream(output, digest)) {
        stream.transferTo(digestStream);
      }
      return new SBHash(algorithm, digest.digest());
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
