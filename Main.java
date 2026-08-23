import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * dedupe - find (and optionally remove) duplicate files under a directory.
 *
 * Single-file, dependency-free version for browser-based Java sandboxes
 * that only support one file / no build tool / no external libraries.
 *
 * Usage:
 *   java Main <directory>                    List groups of duplicate files
 *   java Main <directory> --delete           Delete all but one copy in each group
 *   java Main <directory> --delete --dry-run Show what would be deleted, without deleting
 */
public class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java Main <directory> [--delete] [--dry-run]");
            System.exit(1);
            return;
        }

        Path directory = Paths.get(args[0]);
        boolean delete = false;
        boolean dryRun = false;

        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--delete") || args[i].equals("-d")) {
                delete = true;
            } else if (args[i].equals("--dry-run") || args[i].equals("-n")) {
                dryRun = true;
            } else {
                System.err.println("Unknown option: " + args[i]);
                System.exit(1);
                return;
            }
        }

        int exitCode = run(directory, delete, dryRun);
        System.exit(exitCode);
    }

    private static int run(Path directory, boolean delete, boolean dryRun) {
        DuplicateFinder finder = new DuplicateFinder();

        List<List<Path>> groups;
        try {
            groups = finder.findDuplicates(directory,
                    (path, e) -> System.err.println("Skipping unreadable file: " + path + " (" + e.getMessage() + ")"));
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            System.err.println("Error scanning directory: " + e.getMessage());
            return 1;
        }

        if (groups.isEmpty()) {
            System.out.println("No duplicates found under " + directory);
            return 0;
        }

        long totalReclaimable = 0;
        int groupNum = 1;
        for (List<Path> group : groups) {
            List<Path> sorted = group.stream()
                    .sorted(Comparator.comparingInt(p -> p.toString().length()))
                    .collect(Collectors.toList());
            Path keep = sorted.get(0);
            List<Path> removable = sorted.subList(1, sorted.size());

            System.out.println("Group " + groupNum++ + " (" + group.size() + " copies):");
            System.out.println("  keep:   " + keep);
            for (Path dup : removable) {
                System.out.println("  dup:    " + dup);
                try {
                    totalReclaimable += Files.size(dup);
                } catch (IOException ignored) {
                    // Size already read once during scanning; a failure here is unlikely
                    // and not worth aborting the report over.
                }

                if (delete) {
                    if (dryRun) {
                        System.out.println("  would delete: " + dup);
                    } else {
                        try {
                            Files.delete(dup);
                            System.out.println("  deleted: " + dup);
                        } catch (IOException e) {
                            System.err.println("  failed to delete " + dup + ": " + e.getMessage());
                        }
                    }
                }
            }
        }

        System.out.printf("%nFound %d duplicate group(s), reclaimable space: %.2f MB%n",
                groups.size(), totalReclaimable / (1024.0 * 1024.0));

        return 0;
    }
}

/**
 * Finds groups of duplicate files under a directory tree by content hash.
 *
 * Files are first grouped by size (cheap) and only hashed when a size
 * collision exists, avoiding unnecessary hashing of obviously-unique files.
 */
final class DuplicateFinder {

    private static final String HASH_ALGORITHM = "SHA-256";

    public List<List<Path>> findDuplicates(Path root, java.util.function.BiConsumer<Path, IOException> onError)
            throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        Map<Long, List<Path>> bySize = new HashMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> files = walk.filter(Files::isRegularFile).collect(Collectors.toList());
            for (Path file : files) {
                try {
                    long size = Files.size(file);
                    bySize.computeIfAbsent(size, k -> new ArrayList<>()).add(file);
                } catch (IOException e) {
                    onError.accept(file, e);
                }
            }
        }

        List<List<Path>> duplicateGroups = new ArrayList<>();
        for (List<Path> candidates : bySize.values()) {
            if (candidates.size() < 2) {
                continue;
            }
            Map<String, List<Path>> byHash = new HashMap<>();
            for (Path file : candidates) {
                try {
                    String hash = hashFile(file);
                    byHash.computeIfAbsent(hash, k -> new ArrayList<>()).add(file);
                } catch (IOException e) {
                    onError.accept(file, e);
                }
            }
            for (List<Path> group : byHash.values()) {
                if (group.size() > 1) {
                    duplicateGroups.add(group);
                }
            }
        }

        return duplicateGroups;
    }

    private String hashFile(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 unavailable", e);
        }

        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder(hashBytes.length * 2);
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
