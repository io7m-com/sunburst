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

import com.io7m.sunburst.model.SBPackageIdentifier;
import com.io7m.sunburst.model.SBPackageName;
import com.io7m.sunburst.model.SBPackageVersion;
import org.h2.mvstore.WriteBuffer;
import org.h2.mvstore.type.BasicDataType;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A package identifier datatype.
 *
 * Note: DO NOT MOVE OR RENAME THIS CLASS; the fully-qualified name of the class
 * is recorded in databases.
 */

public final class SBPackageIdentifierDataType
  extends BasicDataType<SBPackageIdentifier>
{
  /**
   * A package identifier datatype.
   *
   * Note: DO NOT MOVE OR RENAME THIS CLASS; the fully-qualified name of the
   * class is recorded in databases.
   */

  public SBPackageIdentifierDataType()
  {

  }

  @Override
  public int compare(
    final SBPackageIdentifier a,
    final SBPackageIdentifier b)
  {
    return a.compareTo(b);
  }

  @Override
  public int getMemory(
    final SBPackageIdentifier obj)
  {
    var size = 0;

    /*
     * Names.
     */

    final var name = obj.name();
    size += 1;
    size += name.groupName().getBytes(UTF_8).length;
    size += 1;
    size += name.name().getBytes(UTF_8).length;

    /*
     * Version.
     *
     * Major/minor/patch, plus qualifier.
     */

    final var version = obj.version();
    size += 4;
    size += 4;
    size += 4;
    size += 1;
    size += version.qualifier().getBytes(UTF_8).length;

    return size;
  }

  @Override
  public void write(
    final WriteBuffer buff,
    final SBPackageIdentifier obj)
  {
    final var name = obj.name();
    final var version = obj.version();

    final var groupBytes = name.groupName().getBytes(UTF_8);
    buff.put((byte) groupBytes.length);
    buff.put(groupBytes);

    final var nameBytes = name.name().getBytes(UTF_8);
    buff.put((byte) nameBytes.length);
    buff.put(nameBytes);

    buff.putInt(version.major());
    buff.putInt(version.minor());
    buff.putInt(version.patch());

    final var qualBytes = version.qualifier().getBytes(UTF_8);
    buff.put((byte) qualBytes.length);
    buff.put(qualBytes);
  }

  @Override
  public SBPackageIdentifier read(
    final ByteBuffer buff)
  {
    final var groupLen = buff.get();
    final var groupBytes = new byte[(int) groupLen];
    buff.get(groupBytes);

    final var nameLen = buff.get();
    final var nameBytes = new byte[(int) nameLen];
    buff.get(nameBytes);

    final var major = buff.getInt();
    final var minor = buff.getInt();
    final var patch = buff.getInt();

    final var qualLen = buff.get();
    final var qualBytes = new byte[(int) qualLen];
    buff.get(qualBytes);

    return new SBPackageIdentifier(
      new SBPackageName(
        new String(groupBytes, UTF_8),
        new String(nameBytes, UTF_8)
      ),
      new SBPackageVersion(
        major,
        minor,
        patch,
        new String(qualBytes, UTF_8)
      )
    );
  }

  @Override
  public SBPackageIdentifier[] createStorage(
    final int size)
  {
    return new SBPackageIdentifier[size];
  }

  @Override
  public boolean isMemoryEstimationAllowed()
  {
    return false;
  }
}
