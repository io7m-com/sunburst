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

import com.io7m.sunburst.model.SBBlob;
import org.h2.mvstore.WriteBuffer;
import org.h2.mvstore.type.BasicDataType;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A blob datatype.
 *
 * Note: DO NOT MOVE OR RENAME THIS CLASS; the name is recorded in databases.
 */

public final class SBBlobDataType extends BasicDataType<SBBlob>
{
  private final SBHashDataType hashType;

  /**
   * A blob datatype.
   *
   * Note: DO NOT MOVE OR RENAME THIS CLASS; the fully-qualified name of the class
   * is recorded in databases.
   */

  public SBBlobDataType()
  {
    this.hashType = new SBHashDataType();
  }

  @Override
  public int compare(
    final SBBlob a,
    final SBBlob b)
  {
    return a.hash().compareTo(b.hash());
  }

  @Override
  public int getMemory(
    final SBBlob obj)
  {
    var size = 0;
    size += this.hashType.getMemory(obj.hash());
    size += 8;
    size += 2;
    size += obj.contentType().getBytes(UTF_8).length;
    return size;
  }

  @Override
  public boolean isMemoryEstimationAllowed()
  {
    return false;
  }

  @Override
  public void write(
    final WriteBuffer buff,
    final SBBlob obj)
  {
    this.hashType.write(buff, obj.hash());
    buff.putLong(obj.size());

    final var ctBytes = obj.contentType().getBytes(UTF_8);
    buff.putShort((short) ctBytes.length);
    buff.put(ctBytes);
  }

  @Override
  public SBBlob read(
    final ByteBuffer buff)
  {
    final var hash =
      this.hashType.read(buff);
    final var size =
      buff.getLong();

    final var ctLen = buff.getShort();
    final var ctBytes = new byte[ctLen];
    buff.get(ctBytes);

    return new SBBlob(size, new String(ctBytes, UTF_8), hash);
  }

  @Override
  public SBBlob[] createStorage(
    final int size)
  {
    return new SBBlob[size];
  }
}

