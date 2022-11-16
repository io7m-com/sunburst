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


package com.io7m.sunburst.pkggen;

import com.io7m.sunburst.pkggen.internal.SBPackageGenerator;
import com.io7m.sunburst.xml.packages.SBPackageSerializerFactoryType;

import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * The default factory of package generators.
 */

public final class SBPackageGenerators
  implements SBPackageGeneratorFactoryType
{
  private final SBPackageSerializerFactoryType packageSerializers;

  /**
   * The default factory of package generators.
   *
   * @param inPeerSerializers The package serializer factory
   */

  public SBPackageGenerators(
    final SBPackageSerializerFactoryType inPeerSerializers)
  {
    this.packageSerializers =
      Objects.requireNonNull(inPeerSerializers, "packageSerializers");
  }

  /**
   * The default factory of package generators.
   */

  public SBPackageGenerators()
  {
    this(
      ServiceLoader.load(SBPackageSerializerFactoryType.class)
        .findFirst()
        .orElseThrow(() -> noSuchService(SBPackageSerializerFactoryType.class))
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
  public SBPackageGeneratorType createGenerator(
    final SBPackageGeneratorConfiguration configuration)
  {
    return new SBPackageGenerator(this.packageSerializers, configuration);
  }
}
