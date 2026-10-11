package com.faber.api.app.release.biz;

import com.github.luben.zstd.ZstdInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.zip.CRC32;

/** 有界、只读 WGT 归档；不将条目解压至文件系统。 */
final class WgtArchive implements AutoCloseable {
    static final long MAX_ARCHIVE = 256L * 1024 * 1024;
    final File file;
    final boolean vapor;
    private final Path temporary;

    private WgtArchive(File file, boolean vapor, Path temporary) {
        this.file = file;
        this.vapor = vapor;
        this.temporary = temporary;
    }

    static WgtArchive open(File source) throws IOException {
        if (source.length() <= 0 || source.length() > 200L * 1024 * 1024) throw new IOException("WGT文件大小超限");
        byte[] header;
        try (InputStream input = Files.newInputStream(source.toPath())) { header = input.readNBytes(4); }
        boolean vapor = java.util.Arrays.equals(header, new byte[]{0x28, (byte) 0xb5, 0x2f, (byte) 0xfd});
        if (vapor && source.length() > 64L * 1024 * 1024) throw new IOException("Vapor WGT文件超过64MiB");
        if (!vapor && !java.util.Arrays.equals(header, new byte[]{0x50, 0x4b, 0x03, 0x04})) throw new IOException("不支持的WGT文件头");
        Path temporary = vapor ? Files.createTempFile("fa-vapor-wgt-", ".zip") : null;
        try {
            if (vapor) {
                try (InputStream input = new ZstdInputStream(Files.newInputStream(source.toPath())).setLongMax(27);
                     var output = Files.newOutputStream(temporary)) {
                    byte[] buffer = new byte[8192];
                    long total = 0;
                    int length;
                    while ((length = input.read(buffer)) != -1) {
                        total += length;
                        if (total > MAX_ARCHIVE) throw new IOException("WGT归档解压量超限");
                        output.write(buffer, 0, length);
                    }
                }
            }
            File file = vapor ? temporary.toFile() : source;
            validate(file, vapor);
            return new WgtArchive(file, vapor, temporary);
        } catch (IOException | RuntimeException error) {
            if (temporary != null) Files.deleteIfExists(temporary);
            throw error;
        }
    }

    private static void validate(File file, boolean vapor) throws IOException {
        try (ZipFile zip = ZipFile.builder().setFile(file).get()) {
            var names = new HashSet<String>();
            var entries = zip.getEntries();
            long total = 0;
            int count = 0;
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                String name = entry.getName();
                if (vapor && (!name.startsWith("www/") || entry.getMethod() != 0)) throw new IOException("Vapor包仅支持www目录下的stored条目");
                if (++count > 10000 || name.startsWith("/") || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0
                        || name.chars().anyMatch(c -> c < 32) || entry.isUnixSymlink() || !names.add(name)) throw new IOException("WGT条目路径或类型无效");
                for (String part : name.replaceAll("/$", "").split("/", -1)) {
                    if (part.isEmpty() || part.equals(".") || part.equals("..")) throw new IOException("WGT路径穿越或空路径");
                }
                int type = entry.getUnixMode() & 0170000;
                if (type != 0 && type != 0100000 && type != 0040000) throw new IOException("WGT特殊文件不支持");
                if (entry.getSize() < 0 || !zip.canReadEntryData(entry)) throw new IOException("WGT条目无法读取");
                CRC32 crc = new CRC32();
                long size = 0;
                try (InputStream input = zip.getInputStream(entry)) {
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = input.read(buffer)) != -1) {
                        total += length;
                        size += length;
                        if (total > MAX_ARCHIVE || (name.endsWith("manifest.json") && size > 1024 * 1024)) throw new IOException("WGT条目解压量超限");
                        crc.update(buffer, 0, length);
                    }
                }
                if (size != entry.getSize() || crc.getValue() != entry.getCrc()) throw new IOException("WGT条目大小或CRC错误");
            }
            if (count == 0) throw new IOException("WGT归档为空");
        }
    }

    @Override public void close() throws IOException { if (temporary != null) Files.deleteIfExists(temporary); }
}
