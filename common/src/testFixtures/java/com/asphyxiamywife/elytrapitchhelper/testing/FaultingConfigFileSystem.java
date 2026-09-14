package com.asphyxiamywife.elytrapitchhelper.testing;

import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigFileSystem;
import com.asphyxiamywife.elytrapitchhelper.config.NioConfigFileSystem;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class FaultingConfigFileSystem implements ConfigFileSystem {
    private final ConfigFileSystem delegate;
    private Consumer<Path> readObserver = path -> {};

    public void observeReads(Consumer<Path> observer) {
        readObserver = observer == null ? path -> {} : observer;
    }

    private Consumer<Path> mutationObserver = path -> {};
    private Runnable beforeHardLink = () -> {};

    public void observeMutations(Consumer<Path> observer) {
        mutationObserver = observer == null ? path -> {} : observer;
    }

    public void beforeHardLink(Runnable action) {
        beforeHardLink = action == null ? () -> {} : action;
    }

    private Predicate<Path> readFailure = path -> false;
    private Predicate<Path> writeFailure = path -> false;
    private Predicate<Path> durableWriteFailure = path -> false;
    private Predicate<Path> forceFailure = path -> false;
    private Predicate<Path> unsupportedDirectorySync = path -> false;
    private Predicate<Path> unsupportedAtomicReplace = path -> false;
    private Predicate<Path> listFailure = path -> false;
    private Predicate<Path> deleteFailure = path -> false;
    private Consumer<Path> temporaryObserver = path -> {
    };
    private Runnable beforeAtomicMove = () -> {
    };

    public FaultingConfigFileSystem(Path configRoot) {
        this(NioConfigFileSystem.rootedAt(configRoot));
    }

    public FaultingConfigFileSystem(ConfigFileSystem delegate) {
        this.delegate = delegate;
    }

    @Override
    public Path configRoot() {
        return delegate.configRoot();
    }

    public ConfigFileSystem undecorated() {
        return delegate;
    }

    public static FaultingConfigFileSystem current() {
        return (FaultingConfigFileSystem) ClientConfigStore.store().fileSystem();
    }

    public void failReadsWhen(Predicate<Path> predicate) {
        readFailure = predicate == null ? path -> false : predicate;
    }

    public void failDurableWritesWhen(Predicate<Path> predicate) {
        durableWriteFailure = predicate == null ? path -> false : predicate;
    }

    public void failWritesWhen(Predicate<Path> predicate) {
        writeFailure = predicate == null ? path -> false : predicate;
    }

    public void failForcesWhen(Predicate<Path> predicate) {
        forceFailure = predicate == null ? path -> false : predicate;
    }

    public void skipDirectorySyncWhen(Predicate<Path> predicate) {
        unsupportedDirectorySync = predicate == null ? path -> false : predicate;
    }

    public void rejectAtomicReplaceWhen(Predicate<Path> predicate) {
        unsupportedAtomicReplace = predicate == null ? path -> false : predicate;
    }

    public void failListsWhen(Predicate<Path> predicate) {
        listFailure = predicate == null ? path -> false : predicate;
    }

    public void failDeletesWhen(Predicate<Path> predicate) {
        deleteFailure = predicate == null ? path -> false : predicate;
    }

    public void observeTemporaryPaths(Consumer<Path> observer) {
        temporaryObserver = observer == null ? path -> {
        } : observer;
    }

    public void beforeAtomicMove(Runnable action) {
        beforeAtomicMove = action == null ? () -> {
        } : action;
    }

    @Override
    public boolean exists(Path path, boolean noFollow) {
        return delegate.exists(path, noFollow);
    }

    @Override
    public boolean isRegularFile(Path path, boolean noFollow) {
        return delegate.isRegularFile(path, noFollow);
    }

    @Override
    public boolean isDirectory(Path path) {
        return delegate.isDirectory(path);
    }

    @Override
    public boolean isSymbolicLink(Path path) {
        return delegate.isSymbolicLink(path);
    }

    @Override
    public Path readSymbolicLink(Path path) throws IOException {
        return delegate.readSymbolicLink(path);
    }

    @Override
    public void createSymbolicLink(Path link, Path target) throws IOException {
        mutationObserver.accept(link);
        delegate.createSymbolicLink(link, target);
    }

    @Override
    public void createHardLink(Path link, Path existing) throws IOException {
        mutationObserver.accept(link);
        beforeHardLink.run();
        delegate.createHardLink(link, existing);
    }

    @Override
    public byte[] read(Path path) throws IOException {
        if (readFailure.test(path)) {
            throw new IOException("Injected config read failure for " + path);
        }
        byte[] contents = delegate.read(path);
        readObserver.accept(path);
        return contents;
    }

    @Override
    public long modifiedAtMillis(Path path) throws IOException {
        return delegate.modifiedAtMillis(path);
    }

    @Override
    public BasicFileAttributes attributes(Path path) throws IOException {
        return delegate.attributes(path);
    }

    @Override
    public List<Path> list(Path directory) throws IOException {
        if (listFailure.test(directory)) {
            throw new IOException("Injected directory listing failure for " + directory);
        }
        return delegate.list(directory);
    }

    @Override
    public void createDirectories(Path directory) throws IOException {
        mutationObserver.accept(directory);
        delegate.createDirectories(directory);
    }

    @Override
    public Path createTempDirectory(Path directory, String prefix) throws IOException {
        mutationObserver.accept(directory);
        return delegate.createTempDirectory(directory, prefix);
    }

    @Override
    public void writeDurable(Path writablePath, Path logicalPath, byte[] contents) throws IOException {
        mutationObserver.accept(logicalPath);
        if (durableWriteFailure.test(logicalPath)) {
            throw new IOException("Injected durable write open failure for " + logicalPath);
        }
        delegate.writeDurable(writablePath, logicalPath, contents);
        if (forceFailure.test(logicalPath)) {
            throw new IOException("Injected durability barrier failure for " + logicalPath);
        }
    }

    @Override
    public void moveAtomic(Path source, Path target) throws IOException {
        mutationObserver.accept(target);
        beforeAtomicMove.run();
        if (writeFailure.test(target)) {
            throw new IOException("Injected config write failure for " + target);
        }
        if (unsupportedAtomicReplace.test(target)) {
            throw new AtomicMoveNotSupportedException(
                    source.toString(), target.toString(), "Injected unsupported atomic replacement");
        }
        delegate.moveAtomic(source, target);
    }

    @Override
    public void move(Path source, Path target) throws IOException {
        mutationObserver.accept(target);
        delegate.move(source, target);
    }

    @Override
    public void fsync(Path directory) throws IOException {
        mutationObserver.accept(directory);
        if (unsupportedDirectorySync.test(directory)) {
            return;
        }
        if (forceFailure.test(directory)) {
            throw new IOException("Injected durability barrier failure for " + directory);
        }
        delegate.fsync(directory);
    }

    @Override
    public void delete(Path path) throws IOException {
        mutationObserver.accept(path);
        if (deleteFailure.test(path)) {
            throw new IOException("Injected delete failure for " + path);
        }
        delegate.delete(path);
    }

    @Override
    public void observeTemporary(Path temporary) {
        temporaryObserver.accept(temporary);
    }
}
