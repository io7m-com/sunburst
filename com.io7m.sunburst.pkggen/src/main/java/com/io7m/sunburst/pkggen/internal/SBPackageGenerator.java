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


package com.io7m.sunburst.pkggen.internal;

import com.io7m.anethum.common.SerializeException;
import com.io7m.sunburst.model.SBBlob;
import com.io7m.sunburst.model.SBHash;
import com.io7m.sunburst.model.SBPackage;
import com.io7m.sunburst.model.SBPackageEntry;
import com.io7m.sunburst.model.SBPath;
import com.io7m.sunburst.pkggen.SBPackageGeneratorConfiguration;
import com.io7m.sunburst.pkggen.SBPackageGeneratorType;
import com.io7m.sunburst.xml.packages.SBPackageSerializerFactoryType;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import static com.io7m.sunburst.model.SBHashAlgorithm.SHA2_256;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * The default package generator implementation.
 */

public final class SBPackageGenerator implements SBPackageGeneratorType
{
  private static final OpenOption[] WRITE_OPTIONS =
    {TRUNCATE_EXISTING, WRITE, CREATE};
  private static final CopyOption[] REPLACE_ATOMICALLY =
    {REPLACE_EXISTING, ATOMIC_MOVE};

  private final SBPackageSerializerFactoryType packageSerializers;
  private final SBPackageGeneratorConfiguration configuration;
  private final Path outputTmp;
  private final HashMap<SBPath, SBPackageEntry> entries;
  private final HashMap<String, String> metadata;

  /**
   * The default package generator implementation.
   *
   * @param inPackageSerializers The package serializers
   * @param inConfiguration      The package generator configuration
   */

  public SBPackageGenerator(
    final SBPackageSerializerFactoryType inPackageSerializers,
    final SBPackageGeneratorConfiguration inConfiguration)
  {
    this.packageSerializers =
      Objects.requireNonNull(inPackageSerializers, "packageSerializers");
    this.configuration =
      Objects.requireNonNull(inConfiguration, "configuration");

    final var outputFile =
      this.configuration.outputFile();
    this.outputTmp =
      outputFile.resolveSibling(outputFile.getFileName() + ".tmp");
    this.entries =
      new HashMap<>();
    this.metadata =
      new HashMap<>();
  }

  @Override
  public void execute()
    throws IOException
  {
    this.entries.clear();
    this.loadMetadata();

    final var base = this.configuration.sourceDirectory();
    try (var stream = Files.walk(base)) {
      final var filePaths =
        stream.filter(Files::isRegularFile)
          .map(Path::toAbsolutePath)
          .toList();

      for (final var filePath : filePaths) {
        final var relative = base.relativize(filePath);

        var sbPath = SBPath.root();
        for (final var segment : relative) {
          sbPath = sbPath.plus(segment.getFileName().toString());
        }

        try (var fileInput = Files.newInputStream(filePath)) {
          final var hash =
            SBHash.hashOf(SHA2_256, fileInput);
          final var blob =
            new SBBlob(
              Files.size(filePath),
              Files.probeContentType(filePath),
              hash
            );
          final var entry = new SBPackageEntry(sbPath, blob);
          this.entries.put(sbPath, entry);
        }
      }
    }

    final var packageV =
      new SBPackage(
        this.configuration.identifier(),
        Map.copyOf(this.metadata),
        this.entries
      );

    try {
      try (var output =
             Files.newOutputStream(this.outputTmp, WRITE_OPTIONS)) {
        this.packageSerializers.serialize(
          this.outputTmp.toUri(),
          output,
          packageV
        );
      }
    } catch (final SerializeException e) {
      throw new IOException(e);
    }

    Files.move(
      this.outputTmp,
      this.configuration.outputFile(),
      REPLACE_ATOMICALLY
    );
  }

  private void loadMetadata()
    throws IOException
  {
    this.metadata.clear();
    final var fileOpt = this.configuration.metaPropertiesFile();
    if (fileOpt.isPresent()) {
      try (var stream = Files.newInputStream(fileOpt.get())) {
        final var properties = new Properties();
        properties.load(stream);
        for (final var entry : properties.entrySet()) {
          this.metadata.put(
            (String) entry.getKey(),
            (String) entry.getValue()
          );
        }
      }
    }
  }
}
