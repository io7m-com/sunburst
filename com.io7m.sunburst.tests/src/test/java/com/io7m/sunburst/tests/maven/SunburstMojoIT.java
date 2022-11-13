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

package com.io7m.sunburst.tests.maven;

import com.soebes.itf.jupiter.extension.MavenGoal;
import com.soebes.itf.jupiter.extension.MavenJupiterExtension;
import com.soebes.itf.jupiter.extension.MavenTest;
import com.soebes.itf.jupiter.maven.MavenExecutionResult;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MavenJupiterExtension
public final class SunburstMojoIT
{
  @MavenGoal("${project.groupId}:com.io7m.sunburst.maven.plugin:${project.version}:generateSources")
  @MavenTest
  void testBasic(
    final MavenExecutionResult result)
    throws Exception
  {
    assertTrue(result.isSuccessful());
  }

  @MavenGoal("${project.groupId}:com.io7m.sunburst.maven.plugin:${project.version}:generateSources")
  @MavenTest
  void testUnparseableImport(
    final MavenExecutionResult result)
    throws Exception
  {
    assertTrue(Files.readString(
      result.getMavenLog()
        .getStdout()
    ).contains("Unparseable import"));
    assertTrue(result.isFailure());
  }

  @MavenGoal("${project.groupId}:com.io7m.sunburst.maven.plugin:${project.version}:generateSources")
  @MavenTest
  void testConflictingImport(
    final MavenExecutionResult result)
    throws Exception
  {
    assertTrue(Files.readString(
      result.getMavenLog()
        .getStdout()
    ).contains("cannot be imported again with version"));
    assertTrue(result.isFailure());
  }
}
