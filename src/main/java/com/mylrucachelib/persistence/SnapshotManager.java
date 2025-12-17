package com.mylrucachelib.persistence;

import com.mylrucachelib.LRUCache;
import com.mylrucachelib.TimeSource;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

public class SnapshotManager<K, V> {
    private static final int SIGNATURE = 0xCAFEBABE;
    private static final int VERSION = 1;
    private final Path filePath;
    private final Serializer<K> keySerializer;
    private final Serializer<V> valueSerializer;
    private final TimeSource clock;

    public SnapshotManager(String path, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        this(path, keySerializer, valueSerializer, System::currentTimeMillis);
    }

    public SnapshotManager(String path, Serializer<K> keySer, Serializer<V> valSer, TimeSource clock) {
        this.filePath = Path.of(path);
        this.keySerializer = keySer;
        this.valueSerializer = valSer;
        this.clock = clock;
    }

    public void save(LRUCache<K,V> cache) throws IOException {
        Path temp = filePath.resolveSibling(filePath.getFileName() + ".tmp");
        try (
                FileOutputStream fos = new FileOutputStream(temp.toFile());
                CheckedOutputStream cos = new CheckedOutputStream(fos, new CRC32());
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(cos))
        ) {
            // header
            out.writeInt(SIGNATURE);
            out.writeInt(VERSION);
            out.writeLong(clock.currentTimeMillis());
            out.writeInt(cache.size());

            // data
            cache.forEach((key, value, expiryTime) -> {
                try {
                    keySerializer.serialize(out, key);
                    valueSerializer.serialize(out, value);
                    out.writeLong(expiryTime);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            out.flush();

            // checksum
            long checksum = cos.getChecksum().getValue();
            out.writeLong(checksum);
            fos.getFD().sync();
        }
        Files.move(temp, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    public void load(LRUCache<K,V> cache) throws IOException {
        if (!Files.exists(filePath)) {
            return;
        }
        try (
                FileInputStream fis = new FileInputStream(filePath.toFile());
                BufferedInputStream bis = new BufferedInputStream(fis);
                CheckedInputStream cis = new CheckedInputStream(bis, new CRC32());
                DataInputStream in = new DataInputStream(cis)) {
            // header
            int signature = in.readInt();
            if (signature != SIGNATURE) {
                throw new IOException("Invalid file format: bad signature");
            }
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported file version: " + version);
            }
            long timestamp = in.readLong();
            int count = in.readInt();
            System.out.println("Recovering " + count + " items from " + timestamp);

            // data
            for (int i = 0; i < count; i++) {
                K key = keySerializer.deserialize(in);
                V value = valueSerializer.deserialize(in);
                long expiryTime = in.readLong();
                if (expiryTime == 0 || expiryTime > clock.currentTimeMillis()) {
                    long ttl = expiryTime - clock.currentTimeMillis();
                    cache.put(key, value, ttl);
                }
            }
            long checksum = cis.getChecksum().getValue();
            long fileChecksum = in.readLong();
            if (checksum != fileChecksum) {
                throw new IOException("File corrupted: checksums do not match");
            }
        }
    }
}
