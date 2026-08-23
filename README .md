# dedupe-cli

A command-line tool that finds (and optionally removes) duplicate files
under a directory tree, using SHA-256 content hashing.

## Build

Requires a full JDK (not just a JRE) -- `javac` must be available.

```bash
javac Main.java
```

This produces two files: `Main.class` and `DuplicateFinder.class`.
No errors should print if the compile succeeds.

## Run

```bash
# List duplicate groups under a directory (safe, read-only)
java Main /path/to/directory

# Preview what would be deleted, without deleting anything
java Main /path/to/directory --delete --dry-run

# Actually delete duplicates, keeping one copy per group
java Main /path/to/directory --delete
```

### Quick test

```bash
mkdir -p ~/dedupe-test/sub
echo "hello world" > ~/dedupe-test/a.txt
echo "hello world" > ~/dedupe-test/sub/b.txt
echo "unique content" > ~/dedupe-test/c.txt

java Main ~/dedupe-test
```

Expected output:

```
Group 1 (2 copies):
  keep:   /home/you/dedupe-test/a.txt
  dup:    /home/you/dedupe-test/sub/b.txt

Found 1 duplicate group(s), reclaimable space: 0.00 MB
```

## How it works

Files are grouped by size first, and only hashed if a size collision
exists -- this avoids hashing files that are obviously unique. Within
each duplicate group found, the file with the shortest path is kept and
the rest are treated as removable.

## Notes

This tool performs real filesystem deletion when `--delete` is used
(no undo). Always run with `--dry-run` first on real data.
