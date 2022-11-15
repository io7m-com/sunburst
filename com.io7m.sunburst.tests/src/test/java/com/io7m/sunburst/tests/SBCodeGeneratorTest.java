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

import com.io7m.sunburst.codegen.SBCodeGeneratorConfiguration;
import com.io7m.sunburst.codegen.SBCodeGenerators;
import com.io7m.sunburst.model.SBPeer;
import com.io7m.sunburst.xml.peers.SBPeerParsers;
import com.sun.source.util.JavacTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ROOT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class SBCodeGeneratorTest
{
  private Path directory;
  private SBCodeGenerators generators;
  private SBPeerParsers parsers;

  @BeforeEach
  public void setup()
    throws IOException
  {
    this.directory = SBTestDirectories.createTempDirectory();
    this.generators = new SBCodeGenerators();
    this.parsers = new SBPeerParsers();
  }

  @AfterEach
  public void tearDown()
    throws IOException
  {
    SBTestDirectories.deleteDirectory(this.directory);
  }

  @Test
  public void testEmpty()
    throws Exception
  {
    this.generators.createGenerator(
      new SBCodeGeneratorConfiguration(
        this.directory,
        List.of(
          SBPeer.builder("com.io7m.example")
            .build()
        )
      )).execute();

    final var generatedJavaFile =
      this.directory.resolve("com")
        .resolve("io7m")
        .resolve("example")
        .resolve("SunburstPeer.java");

    final var generatedServiceFile =
      this.directory.resolve("META-INF")
        .resolve("services")
        .resolve("com.io7m.sunburst.runtime.spi.SBPeerFactoryType");

    final var generatedPeerFile =
      this.directory.resolve("META-INF")
        .resolve("Sunburst")
        .resolve("Peers.xml");

    this.parsers.parseFile(generatedPeerFile);
    assertTrue(Files.exists(generatedJavaFile));
    assertEquals(
      "com.io7m.example.SunburstPeer",
      Files.readString(generatedServiceFile, UTF_8).trim()
    );

    this.compileJava(generatedJavaFile);
  }

  @Test
  public void testBasicImports()
    throws Exception
  {
    this.generators.createGenerator(
      new SBCodeGeneratorConfiguration(
        this.directory,
        List.of(
          SBPeer.builder("com.io7m.example")
            .addImportText("com.io7m.ex0:1.0.0")
            .addImportText("com.io7m.ex1:1.2.0")
            .addImportText("com.io7m.ex2:0.3.1-SNAPSHOT")
            .build()
        )
      )).execute();

    final var generatedJavaFile =
      this.directory.resolve("com")
        .resolve("io7m")
        .resolve("example")
        .resolve("SunburstPeer.java");

    final var generatedServiceFile =
      this.directory.resolve("META-INF")
        .resolve("services")
        .resolve("com.io7m.sunburst.runtime.spi.SBPeerFactoryType");

    final var generatedPeerFile =
      this.directory.resolve("META-INF")
        .resolve("Sunburst")
        .resolve("Peers.xml");

    this.parsers.parseFile(generatedPeerFile);
    assertTrue(Files.exists(generatedJavaFile));
    assertEquals(
      "com.io7m.example.SunburstPeer",
      Files.readString(generatedServiceFile, UTF_8).trim()
    );

    this.compileJava(generatedJavaFile);
  }

  @Test
  public void testBasicMulti()
    throws Exception
  {
    this.generators.createGenerator(
      new SBCodeGeneratorConfiguration(
        this.directory,
        List.of(
          SBPeer.builder("com.io7m.example")
            .addImportText("com.io7m.ex0:1.0.0")
            .addImportText("com.io7m.ex1:1.2.0")
            .addImportText("com.io7m.ex2:0.3.1-SNAPSHOT")
            .build(),
          SBPeer.builder("com.io7m.example.w")
            .addImportText("com.io7m.ex0:1.0.0")
            .addImportText("com.io7m.ex1:1.2.0")
            .addImportText("com.io7m.ex2:0.3.1-SNAPSHOT")
            .build()
        )
      )).execute();

    final var generatedJavaFile0 =
      this.directory.resolve("com")
        .resolve("io7m")
        .resolve("example")
        .resolve("SunburstPeer.java");

    final var generatedJavaFile1 =
      this.directory.resolve("com")
        .resolve("io7m")
        .resolve("example")
        .resolve("w")
        .resolve("SunburstPeer.java");

    final var generatedServiceFile =
      this.directory.resolve("META-INF")
        .resolve("services")
        .resolve("com.io7m.sunburst.runtime.spi.SBPeerFactoryType");

    final var generatedPeerFile =
      this.directory.resolve("META-INF")
        .resolve("Sunburst")
        .resolve("Peers.xml");

    this.parsers.parseFile(generatedPeerFile);
    assertTrue(Files.exists(generatedJavaFile0));
    assertTrue(Files.exists(generatedJavaFile1));

    assertEquals(
      List.of(
        "com.io7m.example.SunburstPeer",
        "com.io7m.example.w.SunburstPeer"
      ),
      Files.readAllLines(generatedServiceFile, UTF_8)
    );

    this.compileJava(generatedJavaFile0);
    this.compileJava(generatedJavaFile1);
  }

  private void compileJava(
    final Path file)
    throws IOException
  {
    final var listener =
      new Diagnostics();
    final var tool =
      ToolProvider.getSystemJavaCompiler();

    try (var fileManager = tool.getStandardFileManager(listener, ROOT, UTF_8)) {
      final var compileArguments =
        List.of(
          "-g",
          "-Werror",
          "-Xdiags:verbose",
          "-Xlint:unchecked",
          "-d",
          this.directory.toAbsolutePath().toString()
        );

      final var task =
        (JavacTask) tool.getTask(
          null,
          fileManager,
          listener,
          compileArguments,
          null,
          List.of(new SourceFile(file))
        );

      final var result =
        task.call();

      assertTrue(
        result.booleanValue(),
        "Compilation of all files must succeed"
      );
    }
  }
}
