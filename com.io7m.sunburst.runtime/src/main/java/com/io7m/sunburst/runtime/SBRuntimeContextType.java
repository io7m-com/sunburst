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

import com.io7m.sunburst.model.SBPath;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

/**
 * The type of runtime contexts.
 */

public interface SBRuntimeContextType
{
  /**
   * Reload all peers.
   */

  void reload();

  /**
   * @return The status of the runtime after the most recent load
   */

  SBRuntimeStatus status();

  /**
   * @return {@code true} if the runtime loaded without any errors
   */

  default boolean isSuccessful()
  {
    return this.status().isSuccessful();
  }

  /**
   * @return {@code true} if the runtime loaded with errors
   */

  default boolean isFailed()
  {
    return !this.isSuccessful();
  }

  /**
   * Open a file channel for the given file.
   *
   * @param requester     The class asking for the file
   * @param targetPackage The target package
   * @param file          The target file
   *
   * @return A file channel
   *
   * @throws IOException        On I/O errors
   * @throws SBRuntimeException On runtime errors
   */

  default FileChannel openChannel(
    final Class<?> requester,
    final String targetPackage,
    final SBPath file)
    throws IOException, SBRuntimeException
  {
    return FileChannel.open(this.findFile(requester, targetPackage, file));
  }

  /**
   * Open a file channel for the given file.
   *
   * @param requester     The class asking for the file
   * @param targetPackage The target package
   * @param file          The target file
   *
   * @return A file channel
   *
   * @throws IOException        On I/O errors
   * @throws SBRuntimeException On runtime errors
   */

  default FileChannel openChannel(
    final Class<?> requester,
    final String targetPackage,
    final String file)
    throws IOException, SBRuntimeException
  {
    return this.openChannel(requester, targetPackage, SBPath.parse(file));
  }

  /**
   * Get the path of the given file.
   *
   * @param requester     The class asking for the file
   * @param targetPackage The target package
   * @param file          The target file
   *
   * @return A path
   *
   * @throws SBRuntimeException On runtime errors
   */

  Path findFile(
    Class<?> requester,
    String targetPackage,
    SBPath file)
    throws SBRuntimeException;

  /**
   * Get the path of the given file.
   *
   * @param requester     The class asking for the file
   * @param targetPackage The target package
   * @param file          The target file
   *
   * @return A path
   *
   * @throws SBRuntimeException On runtime errors
   */

  default Path findFile(
    final Class<?> requester,
    final String targetPackage,
    final String file)
    throws SBRuntimeException
  {
    return this.findFile(
      requester,
      targetPackage,
      SBPath.parse(file)
    );
  }
}
