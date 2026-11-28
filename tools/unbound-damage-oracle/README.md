# Unbound host damage oracle

Compiles **external, read-only** CFRU `src/damage_calc.c` at
`b637a27898b14e25dd24d0f69a3e302f0069deb8`. No ROM, ARM compiler, emulator,
calculator library, or distributed engine source is needed.

```bash
# ROM-free: schema, numeric inputs, complete matrix, 16 monotone rolls,
# random-multiplier consistency, canonical serialization and harness hash.
python3 tools/unbound-damage-oracle/oracle.py
python3 -m unittest discover -s tools/unbound-damage-oracle -p 'test_*.py' -v

# Developer-only: GCC-compatible host C compiler + clean private source checkout.
python3 tools/unbound-damage-oracle/oracle.py --verify --source /private/path/cfru

# Recreate corpus after an intentional harness/matrix revision.
python3 tools/unbound-damage-oracle/oracle.py --regenerate --source /private/path/cfru
```

Set `TMPDIR` to an isolated existing scratch directory before tests/builds.
`CFRU_SOURCE_DIR` can replace `--source`; `CC` or `--cc` chooses the host compiler.
`--verify` reports **SKIP** and exits 0 if the source path is unspecified or absent,
after checking the committed corpus. An existing invalid/dirty/wrong-commit source
path, compile failure, missing results or damage mismatch fails; regeneration never
skips. Private-source verification is deliberately outside CI.

The Python runner supervises each process group: compile ≤120 s, each other command
≤15 s, total run ≤300 s, combined stdout/stderr ≤1 MiB per command, files ≤16 MiB
each, core dumps disabled, TERM then five-second grace then KILL. At least 20 GiB
free is required at launch; jobs stop below 10 GiB. Temporary binaries/manifests
are removed on success; failures retain bounded diagnostics under `TMPDIR` and
print their directory. There are no engine-native logs, daemons, or background jobs.
A normal verify takes about one second and uses less than 1 MiB of scratch artifacts.

`host.c` includes the private implementation in place. `unsupported.h` contains
fail-closed aliases for unreachable dependencies. `matrix.py` declares only
numeric inputs; `corpus.json` is generated, never hand-edited. The compiler/host
identity records the generating environment; verification permits another compiler
only if every roll and all pinned source/harness/configuration identities match.

See [authority, stub inventory and future consumption](../../docs/UNBOUND_DAMAGE_ORACLE.md).
