# ScanConfigurationBreakdown

Pulls Develocity builds filtered by tag, then fetches the Build Scan
`performance-configuration` data for each one.

Two stages:

1. **List builds** — [geapi-data](https://github.com/cdsap/GEApiData) against `/api/builds`,
   which handles paging past the API's 1000-build ceiling and the per-scan attribute fan-out.
2. **Per-build scan data** — one request each to
   `{server}/scan-data/{buildTool}/{buildScanId}/performance-configuration`, run concurrently.

## Installing

Download the binary from the [latest release](https://github.com/cdsap/ScanConfigurationBreakdown/releases/latest):

```bash
curl -sSL -o scb https://github.com/cdsap/ScanConfigurationBreakdown/releases/latest/download/scb
chmod +x scb
./scb --help
```

It needs **Java 21 or newer** on the PATH and nothing else — no Gradle, no JDK, no unpacking. The
release also carries `scb.sha256` if you want to verify the download:

```bash
curl -sSL -O https://github.com/cdsap/ScanConfigurationBreakdown/releases/latest/download/scb.sha256
sha256sum -c scb.sha256      # macOS: shasum -a 256 -c scb.sha256
```

## Running

Two subcommands. `list` dumps the configuration timings of every build carrying a tag, one CSV
row per build:

```bash
scb list --server https://ge.solutions-team.gradle.com --tag CI
```

```
buildId,project,totalConfiguration,scriptCompilation,modelConfiguration,configurationResolution,pluginBuilding,taskGraphCalculation
i3b7ny6n7uzji,"nowinandroid",188567,44811,86827,15576,39753,365
bopdzix3toskk,"nowinandroid",166583,36134,76022,18474,33608,245
```

Add `--task` to keep only the builds that asked for a given task:

```bash
scb list --server https://ge.solutions-team.gradle.com \
  --tag no_sc --task :core:model:compileKotlin
```

Only the CSV goes to stdout; progress, the per-build reasons for a missing row, and the totals go
to stderr, so `> builds.csv` gives a file a spreadsheet or `csv.DictReader` will take as-is.

`compare` reads two tagged variants **in turn** — all of A, then all of B — and reports how their
configuration timings differ:

```bash
scb compare --server https://ge.solutions-team.gradle.com \
  --variant-a sc_1 --variant-b sc_2
```

```
Comparison
  A  sc_1  46/46 builds with data; config cache: 46 miss
  B  sc_2  50/50 builds with data; config cache: 50 miss

Metric                      A median    B median               Delta
--------------------------------------------------------------------
Total configuration       179,417 ms  175,766 ms  -3,651 ms (-2.03%)
Model configuration       146,854 ms  142,463 ms  -4,391 ms (-2.99%)
Configuration resolution   19,976 ms   20,965 ms    +990 ms (+4.95%)
...

Highlights
  Nothing crossed the 5.0% / 50.0 ms threshold.
  Largest movement: v Configuration caching: 5.98% faster in B  360 ms -> 338 ms
```

Variants are compared on the **median**, not the mean — one cold or contended build would drag a
mean around, and build timings are routinely that skewed. A metric is highlighted only when it
crosses both `--min-percent` (default 5%) and `--min-delta` (default 50 ms); the absolute floor
stops small metrics from showing up as large percentages of nothing. When nothing crosses, the
largest movement is reported anyway.

The report also prints a **Read with care** section when something makes the medians misleading —
variants covering different projects, a differing configuration-cache outcome mix (a hit skips
most of the configuration phase, so that alone moves the numbers), a sample under five builds, or
failed scan-data requests. These are confounders in the data, not tool errors.

| Option | |
|---|---|
| `--server` | Develocity URL (required) |
| `--tag` | `list` only; repeat for several tags |
| `--variant-a` / `--variant-b` | `compare` only; repeat for several tags |
| `--label-a` / `--label-b` | Names in the report; default to the tags |
| `--min-percent` / `--min-delta` | Highlight thresholds (default 5%, 50 ms) |
| `--color` | Force ANSI colour when stdout is not a terminal |
| `--project` | Restrict to one project name |
| `--task` | Restrict to builds that requested this task; repeat to require several |
| `--max-builds` | Matching builds to fetch (default 50) |
| `--concurrency` | In-flight scan-data requests (default 10) |
| `--all-tags` | Require every `--tag` rather than any of them |
| `--client-side-filter` | Filter tags locally; only for Develocity older than 2023.3 |
| `--access-key` | Overrides the resolved access key |
| `--cookie` | Browser session cookie for `/scan-data` on an SSO-gated instance |

### Credentials

Both credentials resolve the same way, each from the first source that has one:

| | Access key | Session cookie |
|---|---|---|
| Flag | `--access-key` | `--cookie` |
| System property | `-Ddevelocity.accessKey` | `-Ddevelocity.sessionCookie` |
| Environment | `DEVELOCITY_ACCESS_KEY` | `DEVELOCITY_SESSION_COOKIE` |
| Properties file | `~/.gradle/develocity/keys.properties` | `~/.gradle/develocity/cookies.properties` |

The properties files are keyed by host, one credential per line, so one file serves every instance
you talk to:

```properties
# ~/.gradle/develocity/cookies.properties
instance=geapp1_0=...; GRADLE_ENTERPRISE_IDENTITY=...; geapp1_lat_0=...
ge.solutions-team.gradle.com=...
```

`keys.properties` is the file Gradle itself writes with `./gradlew provisionDevelocityAccessKey`;
`cookies.properties` is ours, same shape. `chmod 600` both — they hold live credentials.

System properties reach the fat binary through `JAVA_OPTS`:

```bash
JAVA_OPTS="-Ddevelocity.sessionCookie=$(cat ~/.dv-cookie)" scb list --server ... --tag CI
```

Which source suits which credential: the access key is long-lived, so the file is the place for it
and you never think about it again. The cookie expires every few hours, so the flag or the
environment variable usually beats editing a file — though the file is convenient when you are
running many commands against one instance inside a session.

