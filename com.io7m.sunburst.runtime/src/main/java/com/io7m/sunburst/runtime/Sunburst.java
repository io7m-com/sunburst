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

import com.io7m.sunburst.inventory.api.SBInventoryConfiguration;
import com.io7m.sunburst.inventory.api.SBInventoryException;
import com.io7m.sunburst.inventory.api.SBInventoryFactoryType;
import com.io7m.sunburst.inventory.api.SBInventoryReadableType;
import com.io7m.sunburst.inventory.api.SBTransactionReadableType;
import com.io7m.sunburst.model.SBPackageIdentifier;
import com.io7m.sunburst.model.SBPath;
import com.io7m.sunburst.model.SBPeer;
import com.io7m.sunburst.runtime.spi.SBPeerFactoryType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_PEER_IMPORT_MISSING;
import static com.io7m.sunburst.error_codes.SBErrorCodesStandard.ERROR_PEER_MISSING;

/**
 * The {@code sunburst} API.
 */

public final class Sunburst
{
  private Sunburst()
  {

  }

  private static ServiceConfigurationError noSuchService(
    final Class<?> clazz)
  {
    return new ServiceConfigurationError(
      "No available services of type: %s".formatted(clazz)
    );
  }

  private static <T> List<Supplier<T>> serviceLoader(
    final Class<T> tClass)
  {
    final var loader = ServiceLoader.load(tClass);
    loader.reload();
    return loader.stream()
      .map(provider -> (Supplier<T>) provider)
      .toList();
  }

  /**
   * Open a runtime context.
   *
   * @param configuration The inventory configuration
   *
   * @return A runtime context
   *
   * @throws SBInventoryException On inventory errors
   */

  public static SBRuntimeContextType open(
    final SBInventoryConfiguration configuration)
    throws SBInventoryException
  {
    return open(configuration, Sunburst::serviceLoader);
  }

  /**
   * Open a runtime context.
   *
   * @param configuration The inventory configuration
   * @param serviceLoader The service loader
   *
   * @return A runtime context
   *
   * @throws SBInventoryException On inventory errors
   */

  public static SBRuntimeContextType open(
    final SBInventoryConfiguration configuration,
    final SBRuntimeServiceLoaderType serviceLoader)
    throws SBInventoryException
  {
    Objects.requireNonNull(configuration, "configuration");
    Objects.requireNonNull(serviceLoader, "serviceLoader");

    return openUsingFactory(
      ServiceLoader.load(SBInventoryFactoryType.class)
        .findFirst()
        .orElseThrow(() -> noSuchService(SBInventoryFactoryType.class)),
      configuration,
      serviceLoader
    );
  }

  /**
   * Open a runtime context.
   *
   * @param inventories   The inventory factory
   * @param configuration The inventory configuration
   * @param serviceLoader The service loader
   *
   * @return A runtime context
   *
   * @throws SBInventoryException On inventory errors
   */

  public static SBRuntimeContextType openUsingFactory(
    final SBInventoryFactoryType inventories,
    final SBInventoryConfiguration configuration,
    final SBRuntimeServiceLoaderType serviceLoader)
    throws SBInventoryException
  {
    Objects.requireNonNull(inventories, "inventories");
    Objects.requireNonNull(configuration, "configuration");
    Objects.requireNonNull(serviceLoader, "serviceLoader");

    return openUsingInventory(
      inventories.openReadOnly(configuration),
      serviceLoader
    );
  }

  /**
   * Open a runtime context.
   *
   * @param inventories   The inventory factory
   * @param configuration The inventory configuration
   *
   * @return A runtime context
   *
   * @throws SBInventoryException On inventory errors
   */

  public static SBRuntimeContextType openUsingFactory(
    final SBInventoryFactoryType inventories,
    final SBInventoryConfiguration configuration)
    throws SBInventoryException
  {
    Objects.requireNonNull(inventories, "inventories");
    Objects.requireNonNull(configuration, "configuration");

    return openUsingInventory(
      inventories.openReadOnly(configuration),
      Sunburst::serviceLoader
    );
  }

  /**
   * Open a runtime context.
   *
   * @param inventory The inventory
   *
   * @return A runtime context
   */

  public static SBRuntimeContextType openUsingInventory(
    final SBInventoryReadableType inventory)
  {
    Objects.requireNonNull(inventory, "inventory");
    return openUsingInventory(inventory, Sunburst::serviceLoader);
  }

  /**
   * Open a runtime context.
   *
   * @param inventory     The inventory
   * @param serviceLoader The service loader
   *
   * @return A runtime context
   */

  public static SBRuntimeContextType openUsingInventory(
    final SBInventoryReadableType inventory,
    final SBRuntimeServiceLoaderType serviceLoader)
  {
    Objects.requireNonNull(inventory, "inventory");
    Objects.requireNonNull(serviceLoader, "serviceLoader");

    final SBRuntimeStrings strings;
    try {
      strings = new SBRuntimeStrings(inventory.configuration().locale());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    final var context = new Context(strings, inventory, serviceLoader);
    context.reload();
    return context;
  }

  private static final class Context implements SBRuntimeContextType
  {
    private final SBRuntimeStrings strings;
    private final SBInventoryReadableType inventory;
    private final SBRuntimeServiceLoaderType serviceLoader;
    private volatile List<SBRuntimeProblemType> problems;
    private volatile Map<String, SBPeer> peers;

    private Context(
      final SBRuntimeStrings inStrings,
      final SBInventoryReadableType inInventory,
      final SBRuntimeServiceLoaderType inServiceLoader)
    {
      this.strings =
        Objects.requireNonNull(inStrings, "strings");
      this.inventory =
        Objects.requireNonNull(inInventory, "inventory");
      this.serviceLoader =
        Objects.requireNonNull(inServiceLoader, "serviceLoader");
    }

    private static Optional<SBPeer> reloadPeer(
      final List<SBRuntimeProblemType> newProblems,
      final Map<String, SBPeer> newPeers,
      final SBTransactionReadableType transaction,
      final Supplier<SBPeerFactoryType> provider)
    {
      final SBPeerFactoryType peerFactory;
      final SBPeer peer;

      try {
        peerFactory = provider.get();
      } catch (final Exception e) {
        newProblems.add(
          new SBRuntimeBrokenPeerFactory(provider.getClass(), e));
        return Optional.empty();
      }

      try {
        peer = peerFactory.openPeer();
      } catch (final Exception e) {
        newProblems.add(
          new SBRuntimeBrokenPeerFactory(peerFactory.getClass(), e));
        return Optional.empty();
      }

      final var existing = newPeers.get(peer.packageName());
      if (existing != null) {
        newProblems.add(
          new SBRuntimeConflictingPeer(
            peer.getClass(),
            peer.packageName(),
            existing.getClass(),
            existing.packageName()
          )
        );
        return Optional.empty();
      }

      return processPeerImports(newProblems, transaction, peer);
    }

    private static Optional<SBPeer> processPeerImports(
      final List<SBRuntimeProblemType> newProblems,
      final SBTransactionReadableType transaction,
      final SBPeer peer)
    {
      var peerFailed = false;
      for (final var entry : peer.imports().entrySet()) {
        final var importName =
          entry.getKey();
        final var importVersion =
          entry.getValue();
        final var identifier =
          new SBPackageIdentifier(importName, importVersion);

        try {
          final var packageOpt =
            transaction.packageGet(identifier);

          if (packageOpt.isEmpty()) {
            peerFailed = true;
            newProblems.add(
              new SBRuntimeUnsatisfiedRequirement(
                peer.packageName(),
                identifier)
            );
          }
        } catch (final SBInventoryException e) {
          peerFailed = true;
          newProblems.add(new SBRuntimeInventoryProblem(e));
        }
      }

      if (peerFailed) {
        return Optional.empty();
      }

      return Optional.of(peer);
    }

    @Override
    public void reload()
    {
      final var peerIterator =
        this.serviceLoader.load(SBPeerFactoryType.class)
          .iterator();

      final var newProblems =
        new ArrayList<SBRuntimeProblemType>();
      final var newPeers =
        new HashMap<String, SBPeer>();

      try (var transaction = this.inventory.openTransactionReadable()) {
        while (peerIterator.hasNext()) {
          final var provider = peerIterator.next();
          reloadPeer(newProblems, newPeers, transaction, provider)
            .ifPresent(peer -> newPeers.put(peer.packageName(), peer));
        }
      } catch (final SBInventoryException e) {
        newProblems.add(new SBRuntimeInventoryProblem(e));
      }

      this.problems = List.copyOf(newProblems);
      this.peers = Map.copyOf(newPeers);
    }

    @Override
    public SBRuntimeStatus status()
    {
      return new SBRuntimeStatus(this.problems);
    }

    @Override
    public Path findFile(
      final Class<?> requester,
      final String targetPackage,
      final SBPath file)
      throws SBRuntimeException
    {
      final var requesterName = requester.getPackageName();
      final var requesterPeer = this.peers.get(requesterName);
      if (requesterPeer == null) {
        throw new SBRuntimeException(
          ERROR_PEER_MISSING,
          this.strings.format("errorPeerMissing", requesterName)
        );
      }

      final var imports =
        requesterPeer.imports();
      final var targetVersion =
        imports.get(targetPackage);

      if (targetVersion == null) {
        throw new SBRuntimeException(
          ERROR_PEER_IMPORT_MISSING,
          this.strings.format(
            "errorPeerImportMissing",
            requesterName,
            targetPackage,
            requesterPeer.importSet()
          )
        );
      }

      final var identifier =
        new SBPackageIdentifier(targetPackage, targetVersion);

      try (var transaction =
             this.inventory.openTransactionReadable()) {
        return transaction.blobFile(identifier, file);
      } catch (final SBInventoryException e) {
        throw new SBRuntimeException(
          e.errorCode(),
          e.getMessage(),
          e
        );
      }
    }
  }
}
