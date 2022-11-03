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


package com.io7m.sunburst.inventory.datatypes;

import com.io7m.sunburst.model.SBHash;
import com.io7m.sunburst.model.SBHashAlgorithm;
import org.h2.mvstore.WriteBuffer;
import org.h2.mvstore.type.BasicDataType;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A hash datatype.
 *
 * Note: DO NOT MOVE OR RENAME THIS CLASS; the fully-qualified name of the class
 * is recorded in databases.
 */

public final class SBHashDataType extends BasicDataType<SBHash>
{
  /**
   * A hash datatype.
   *
   * Note: DO NOT MOVE OR RENAME THIS CLASS; the fully-qualified name of the class
   * is recorded in databases.
   */

  public SBHashDataType()
  {

  }

  static SBHash readHash(
    final ByteBuffer buff)
  {
    final var algo =
      SBHashAlgorithm.ofIdentifier((int) buff.getShort());

    final byte[] bytes = switch (algo) {
      case SHA2_256 -> new byte[32];
    };

    buff.get(bytes);
    return new SBHash(algo, bytes);
  }

  @Override
  public int compare(
    final SBHash a,
    final SBHash b)
  {
    return a.compareTo(b);
  }

  @Override
  public int getMemory(
    final SBHash obj)
  {
    return 1 + obj.value().length;
  }

  @Override
  public boolean isMemoryEstimationAllowed()
  {
    return false;
  }

  @Override
  public void write(
    final WriteBuffer buff,
    final SBHash obj)
  {
    buff.putShort((short) obj.algorithm().index());
    buff.put(obj.value());
  }

  @Override
  public SBHash read(
    final ByteBuffer buff)
  {
    return readHash(buff);
  }

  @Override
  public SBHash[] createStorage(
    final int size)
  {
    return new SBHash[size];
  }
}
