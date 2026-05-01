# AGENTS.md — tabu-search-tasf

Java 17 Maven project. Tabu Search metaheuristic for luggage delivery scheduling (PUCP 2026-1).

## Build & Run

```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="pe.edu.pucp.tasf.Main" -Dexec.args="E1 5 50"
```

**Always pass `-Dfile.encoding=UTF-8`** when running manually — the program sets UTF-8 stdout and uses accented chars/box-drawing:
```bash
java -Dfile.encoding=UTF-8 -cp target/classes pe.edu.pucp.tasf.Main E1 5 50
```

## Execution Modes

| Mode | Args | Description |
|------|------|-------------|
| `E1` | `[days] [req/day]` | Synthetic simulation (default: 5 days, 50 req/day) |
| `E2` | `0 [req]` | Real-time replanning (≤5 s/event) |
| `E3` | `0 [baseReq]` | Collapse sim — demand +20%/day until RED |
| `E1R` | `<folder> [startDay] [numDays] [planes_vuelo.txt]` | Period sim from real `_envios_XXXX_.txt` files |
| `EXP` | `<folder> [replicas] [output.csv] [planes_vuelo.txt]` | N replicas (seeds 1..N), writes CSV incrementally |

E1R example:
```bash
java -Dfile.encoding=UTF-8 -cp target/classes pe.edu.pucp.tasf.Main E1R ./_envios_preliminar_ 0 3
```

EXP example:
```bash
java -cp target/classes pe.edu.pucp.tasf.Main EXP ./_envios_preliminar_ 30 E1_30_runs_TS.csv
```

## Testing

**No JUnit.** The only test is a hand-rolled integration test at `src/main/java/pe/edu/pucp/tasf/test/RunnerTest.java` (compiled with production code, lives under `src/main/java/`).

```bash
java -Dfile.encoding=UTF-8 -cp target/classes pe.edu.pucp.tasf.test.RunnerTest [folder]
```
- Default folder: `test-data/`
- Pass `_envios_preliminar_/` for real data
- Exits with code `1` on failure
- Runs 50 TS iterations, 30 s time limit

## Architecture

```
Main.java                        — entry point, dispatches 5 modes
model/                           — Airport, Flight (mutable load), LogisticsNetwork, Solution, ShipmentRequest
algorithm/TabuSearchSolver.java  — solve(), replanify(), getCsvRow()
algorithm/TabuSearchConfig.java  — fluent builder for TS parameters
io/EnviosDataLoader.java         — parser for _envios_XXXX_.txt
util/NetworkGenerator.java       — synthetic network/requests (E1/E2/E3)
util/RealNetworkBuilder.java     — network from ICAO codes ± planes_vuelo.txt
test/RunnerTest.java             — integration smoke test
```

## Key Quirks

- **`Flight` is mutable** — tracks assigned load. `LogisticsNetwork.resetLoads()` must be called before each `solve()` call. EXP mode does this between replicas.
- **`planes_vuelo.txt` is optional** — if omitted, `RealNetworkBuilder` synthesizes a flight network from ICAO codes found in shipment files.
- **Shipment filename encodes origin ICAO**: `_envios_SPIM_.txt` = Lima (SPIM).
- **Default seed is `42`**; EXP mode uses seeds 1..N for reproducibility.
- **No CI, no linter, no pre-commit hooks.**

## Input Data

Shipment file line format: `ID-YYYYMMDD-HH-MM-DEST_ICAO-QTY-CLIENT_ID`
Example: `000000001-20260102-00-47-SUAA-002-0032535`

ICAO prefix → continent (used for time windows: 1 day intra, 2 days inter):
- `S, K, C, M, T` → AMERICA
- `E, L, B` → EUROPE
- `O, U, V, R, Z, W, Y` → ASIA
