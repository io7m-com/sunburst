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


package com.io7m.sunburst.codegen;

import com.io7m.sunburst.codegen.internal.SBCodeGenerator;
import com.io7m.sunburst.xml.peers.SBPeerSerializerFactoryType;

import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * The default factory of code generators.
 */

public final class SBCodeGenerators
  implements SBCodeGeneratorFactoryType
{
  private final SBPeerSerializerFactoryType peerSerializers;

  /**
   * The default factory of code generators.
   *
   * @param inPeerSerializers The peer serializer factory
   */

  public SBCodeGenerators(
    final SBPeerSerializerFactoryType inPeerSerializers)
  {
    this.peerSerializers =
      Objects.requireNonNull(inPeerSerializers, "peerSerializers");
  }

  /**
   * The default factory of code generators.
   */

  public SBCodeGenerators()
  {
    this(
      ServiceLoader.load(SBPeerSerializerFactoryType.class)
        .findFirst()
        .orElseThrow(() -> noSuchService(SBPeerSerializerFactoryType.class))
    );
  }

  private static ServiceConfigurationError noSuchService(
    final Class<?> clazz)
  {
    return new ServiceConfigurationError(
      "No services available of type: %s".formatted(clazz)
    );
  }

  @Override
  public SBCodeGeneratorType createGenerator(
    final SBCodeGeneratorConfiguration configuration)
  {
    return new SBCodeGenerator(this.peerSerializers, configuration);
  }
}
