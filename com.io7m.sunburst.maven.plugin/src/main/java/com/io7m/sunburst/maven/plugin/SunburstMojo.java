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

package com.io7m.sunburst.maven.plugin;

import com.io7m.sunburst.codegen.SBCodeGeneratorConfiguration;
import com.io7m.sunburst.codegen.SBCodeGenerators;
import com.io7m.sunburst.model.SBPeer;
import com.io7m.sunburst.model.SBPeerException;
import com.io7m.sunburst.xml.peers.SBPeerSerializers;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * The "generate sources" mojo.
 */

@Mojo(
  name = "generateSources",
  defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public final class SunburstMojo extends AbstractMojo
{
  /**
   * The peers in this module.
   */

  @Parameter(
    name = "peers",
    required = false)
  private List<Peer> peers;

  /**
   * The current Maven settings.
   */

  @Parameter(
    defaultValue = "${settings}",
    readonly = true,
    required = true)
  private Settings settings;

  /**
   * The output directory.
   */

  @Parameter(
    name = "outputDirectory",
    defaultValue = "${project.build.directory}/generated-sources/sunburst",
    required = false)
  private String outputDirectory;

  /**
   * The Maven project.
   */

  @Parameter(readonly = true, defaultValue = "${project}")
  private MavenProject project;

  /**
   * Instantiate the mojo.
   */

  public SunburstMojo()
  {

  }

  @Override
  public void execute()
    throws MojoExecutionException
  {
    try {
      final var parsedPeers =
        this.parsePeers();
      final var codeGenerators =
        new SBCodeGenerators(new SBPeerSerializers());
      final var outputPath =
        Paths.get(this.outputDirectory)
          .toAbsolutePath();

      final var codeGenerator =
        codeGenerators.createGenerator(
          new SBCodeGeneratorConfiguration(outputPath, parsedPeers)
        );

      codeGenerator.execute();

      final var resource = new Resource();
      resource.setDirectory(outputPath.toString());
      resource.setFiltering(false);
      resource.setIncludes(List.of("META-INF/services/**"));
      this.project.addCompileSourceRoot(outputPath.toString());
      this.project.addResource(resource);

    } catch (final Exception e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  private List<SBPeer> parsePeers()
    throws SBPeerException
  {
    if (this.peers == null) {
      this.peers = List.of();
    }

    final var log = this.getLog();
    final var results = new ArrayList<SBPeer>(this.peers.size());
    for (final var p : this.peers) {
      final var builder =
        SBPeer.builder(p.packageName());

      for (final var text : p.imports()) {
        builder.addImportText(text);
      }

      try {
        results.add(builder.build());
      } catch (final SBPeerException e) {
        for (final var problem : e.problems()) {
          log.error(problem);
        }
        throw e;
      }
    }

    return List.copyOf(results);
  }
}
