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

import com.io7m.sunburst.model.SBPeer;
import com.io7m.sunburst.model.SBPeerException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public final class SBPeerTest
{
  @Test
  public void testImportConflict()
  {
    assertThrows(SBPeerException.class, () -> {
      SBPeer.builder("com.io7m.example")
        .addImportText("com.io7m.ex0:1.0.0")
        .addImportText("com.io7m.ex0:2.0.0")
        .build();
    });
  }

  @Test
  public void testImportUnparseable()
  {
    assertThrows(SBPeerException.class, () -> {
      SBPeer.builder("com.io7m.example")
        .addImportText("com.io7m.ex0")
        .addImportText("com.io7m.ex1:x.y.z")
        .addImportText("com.io7m.ex2:1.0.0:x")
        .build();
    });
  }

  @Test
  public void testBadPackageName()
  {
    assertThrows(SBPeerException.class, () -> {
      SBPeer.builder(" ")
        .build();
    });
  }
}
