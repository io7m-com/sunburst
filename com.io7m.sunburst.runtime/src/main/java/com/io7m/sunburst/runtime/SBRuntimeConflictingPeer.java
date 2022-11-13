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

package com.io7m.sunburst.runtime;

import java.util.Objects;

/**
 * Two peers tried to register with the same name.
 *
 * @param peer0     The peer 0 class
 * @param peer0Name The peer 0 name
 * @param peer1     The peer 1 class
 * @param peer1Name The peer 1 name
 */

public record SBRuntimeConflictingPeer(
  Class<?> peer0,
  String peer0Name,
  Class<?> peer1,
  String peer1Name)
  implements SBRuntimeProblemType
{
  /**
   * Two peers tried to register with the same name.
   *
   * @param peer0     The peer 0 class
   * @param peer0Name The peer 0 name
   * @param peer1     The peer 1 class
   * @param peer1Name The peer 1 name
   */

  public SBRuntimeConflictingPeer
  {
    Objects.requireNonNull(peer0, "peer0");
    Objects.requireNonNull(peer0Name, "peer0Name");
    Objects.requireNonNull(peer1, "peer1");
    Objects.requireNonNull(peer1Name, "peer1Name");
  }
}
