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

package com.io7m.sunburst.codegen.internal;

import com.io7m.anethum.common.SerializeException;
import com.io7m.jodist.AnnotationSpec;
import com.io7m.jodist.ClassName;
import com.io7m.jodist.CodeBlock;
import com.io7m.jodist.FieldSpec;
import com.io7m.jodist.JavaFile;
import com.io7m.jodist.MethodSpec;
import com.io7m.jodist.TypeSpec;
import com.io7m.sunburst.codegen.SBCodeGeneratorConfiguration;
import com.io7m.sunburst.codegen.SBCodeGeneratorType;
import com.io7m.sunburst.model.SBPackageIdentifier;
import com.io7m.sunburst.model.SBPeer;
import com.io7m.sunburst.model.SBPeerException;
import com.io7m.sunburst.runtime.spi.SBPeerFactoryType;
import com.io7m.sunburst.xml.peers.SBPeerSerializerFactoryType;
import com.io7m.verona.core.Version;
import org.osgi.service.component.annotations.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * The default code generator.
 */

public final class SBCodeGenerator implements SBCodeGeneratorType
{
  private static final String PEER_CLASS_NAME = "SunburstPeer";

  private final SBPeerSerializerFactoryType peerSerializers;
  private final SBCodeGeneratorConfiguration configuration;

  /**
   * The default code generator.
   *
   * @param inPeerSerializers A factory of peer serializers
   * @param inConfiguration   The code generator configuration
   */

  public SBCodeGenerator(
    final SBPeerSerializerFactoryType inPeerSerializers,
    final SBCodeGeneratorConfiguration inConfiguration)
  {
    this.peerSerializers =
      Objects.requireNonNull(inPeerSerializers, "peerSerializers");
    this.configuration =
      Objects.requireNonNull(inConfiguration, "configuration");
  }

  @Override
  public void execute()
    throws IOException
  {
    for (final var p : this.configuration.peers()) {
      this.writeClass(p, this.generatePeerClass(p));
    }
    this.writeServiceFile();
    this.writePeerFile();
  }

  private void writePeerFile()
    throws IOException
  {
    final var path =
      this.generatePeerFileName();

    Files.createDirectories(path.getParent());
    try {
      this.peerSerializers.serializeFile(path, this.configuration.peers());
    } catch (final SerializeException e) {
      throw new IOException(e);
    }
  }

  private Path generatePeerFileName()
  {
    var path =
      this.configuration.sourceDirectory()
        .toAbsolutePath();

    path = path.resolve("META-INF");
    path = path.resolve("Sunburst");
    path = path.resolve("Peers.xml");
    return path;
  }

  private void writeServiceFile()
    throws IOException
  {
    final var path =
      this.generateServiceFileName();

    Files.createDirectories(path.getParent());

    try (var writer =
           Files.newBufferedWriter(path, WRITE, CREATE, TRUNCATE_EXISTING)) {
      for (final var p : this.configuration.peers()) {
        writer.append(className(p));
        writer.append("\n");
      }
    }
  }

  private static String className(
    final SBPeer peer)
  {
    return "%s.%s".formatted(peer.packageName(), PEER_CLASS_NAME);
  }

  private Path generateServiceFileName()
  {
    var path =
      this.configuration.sourceDirectory()
        .toAbsolutePath();

    path = path.resolve("META-INF");
    path = path.resolve("services");
    path = path.resolve(SBPeerFactoryType.class.getCanonicalName());
    return path;
  }

  private void writeClass(
    final SBPeer peer,
    final TypeSpec classSpec)
    throws IOException
  {
    final var file = this.generateClassFileName(peer);
    Files.createDirectories(file.getParent());

    final var javaFile =
      JavaFile.builder(peer.packageName(), classSpec)
        .build();

    javaFile.writeTo(this.configuration.sourceDirectory());
  }

  private Path generateClassFileName(
    final SBPeer peer)
  {
    var path =
      this.configuration.sourceDirectory()
        .toAbsolutePath();

    final var segments =
      List.of(peer.packageName().split("\\."));

    for (final var item : segments) {
      path = path.resolve(item);
    }
    path = path.resolve("SunburstPeer.java");
    return path;
  }

  private TypeSpec generatePeerClass(
    final SBPeer peer)
  {
    final var className =
      ClassName.get(peer.packageName(), PEER_CLASS_NAME);
    final var classBuilder =
      TypeSpec.classBuilder(className);

    final var annotSpec =
      AnnotationSpec.builder(Component.class)
        .addMember("service", "$T.class", SBPeerFactoryType.class)
        .build();

    classBuilder.addAnnotation(annotSpec);
    classBuilder.addModifiers(PUBLIC, FINAL);
    classBuilder.addSuperinterface(SBPeerFactoryType.class);
    classBuilder.addJavadoc(
      "The Sunburst peer factory for package {@code %s}."
        .formatted(peer.packageName())
    );
    classBuilder.addMethod(
      MethodSpec.constructorBuilder()
        .addModifiers(PUBLIC)
        .addJavadoc(
          "The Sunburst peer factory for package {@code %s}."
            .formatted(peer.packageName())
        ).build()
    );

    final var names = new ArrayList<String>();
    final var nameGen = new SBFreshNames();

    final var importMap = peer.imports();
    final var importPackages = new ArrayList<>(importMap.keySet());
    Collections.sort(importPackages);

    for (final var packName : importPackages) {
      final var v = importMap.get(packName);

      final CodeBlock initializer;
      final var qOpt = v.qualifier();
      if (qOpt.isPresent()) {
        initializer = CodeBlock.builder()
          .add(
            "new $T($S, $T.of($L,$L,$L,$S))",
            SBPackageIdentifier.class,
            packName,
            Version.class,
            Integer.toUnsignedString(v.major()),
            Integer.toUnsignedString(v.minor()),
            Integer.toUnsignedString(v.patch()),
            qOpt.get().text()
          ).build();
      } else {
        initializer = CodeBlock.builder()
          .add(
            "new $T($S, $T.of($L,$L,$L))",
            SBPackageIdentifier.class,
            packName,
            Version.class,
            Integer.toUnsignedString(v.major()),
            Integer.toUnsignedString(v.minor()),
            Integer.toUnsignedString(v.patch())
          ).build();
      }

      final var fieldName = nameGen.freshName();
      names.add(fieldName);

      classBuilder.addField(
        FieldSpec.builder(SBPackageIdentifier.class, fieldName)
          .addModifiers(PRIVATE, STATIC, FINAL)
          .initializer(
            initializer)
          .build()
      );
    }

    final var codeBuilder = CodeBlock.builder();
    final var builderName = nameGen.freshName();
    codeBuilder.add(
      "var $L = $T.builder($S);\n",
      builderName,
      SBPeer.class,
      peer.packageName()
    );
    for (final var field : names) {
      codeBuilder.add("$L.addImport($L);\n", builderName, field);
    }
    codeBuilder.add("return $L.build();\n", builderName);

    classBuilder.addMethod(
      MethodSpec.methodBuilder("openPeer")
        .addModifiers(PUBLIC)
        .addAnnotation(Override.class)
        .addException(SBPeerException.class)
        .returns(SBPeer.class)
        .addCode(codeBuilder.build())
        .build()
    );

    return classBuilder.build();
  }
}
