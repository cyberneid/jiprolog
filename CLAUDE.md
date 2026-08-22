# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## What this is

JIProlog — a Prolog interpreter written in 100% pure Java, with a bidirectional
Java↔Prolog bridge (call Prolog from Java, call Java from Prolog, query JDBC
databases from Prolog). No JNI, no native code, no third-party runtime
dependencies. Author: Ugo Chirico. Licensed AGPLv3 or commercial.

Version is defined in `JIPEngine.major/minor/build/revision` (currently 4.1.7.1)
and **not** in `build.xml` (which still says 4.1.6.1 — they drift).

## Build and run

Maven is the build. It compiles the Java, bootstraps the release form of the
Prolog libraries, runs the tests, and packages a runnable jar:

```bash
mvn verify                    # compile + bootstrap the .jip libraries + test
mvn package                   # the above, plus target/jiprolog-4.1.7.1.jar
java -jar target/jiprolog-4.1.7.1.jar -c yourfile.pl -g yourgoal
```

A clean build takes about fifteen seconds. Two things in the POM are
load-bearing and easy to break by "tidying":

- **`project.build.sourceEncoding` is `ISO-8859-1`, not UTF-8.** The sources
  carry Italian comments with accented characters. Compiling them as UTF-8
  fails with ~68 "unmappable character" errors. Converting the tree to UTF-8 is
  a reasonable change, but it has to be one deliberate commit, not a side
  effect.
- **`sourceDirectory` is `src`**, not `src/main/java` — the tree keeps its
  original Eclipse layout. Tests live in `test/java`, test fixtures in
  `test/resources`.

`maven.compiler.release` is 8, so the jar runs on a Java 8 runtime even though
the build itself needs 9+ (`--release` is not available on JDK 8's compiler
plugin). CI verifies that claim by running the packaged jar under JDK 8.

Use `-DskipPrologCompile` to skip the library bootstrap when iterating on Java
code — but see the next section for what that costs you.

The legacy `build.xml` is still in the tree and **does not work from a clean
clone**: it references a sibling `../jipgui` project, a `../deploy` tree, and a
hard-coded Windows ProGuard path. It is kept only for the ProGuard/deploy
bundling steps that Maven does not yet cover. Do not add to it.

### Debug vs release kernel — read this before debugging a startup crash

The kernel and libraries exist in two forms:

| | debug | release |
|---|---|---|
| kernel | `resources/jipkernel.txt` (Prolog source) | `jipkernel.jip` (Java-serialized terms) |
| libraries | `resources/*.pl` | `*.jip` |
| selected by | `JIPDebugger.debug == true` | default |

The `.jip` files are **build output**: gitignored, never committed, and never
written into `src/`. They are produced by bootstrapping the interpreter against
itself — running JIProlog in debug mode over its own Prolog sources — which is
what `tools/compile-libraries.pl` does and what the `exec-maven-plugin` step in
`process-classes` runs. It replaces the ant `CompileLibraries` target.

So: after `mvn verify` the release path works. But if you compile by hand with
`javac`, or build with `-DskipPrologCompile`, there is no `jipkernel.jip` and
`GlobalDB.loadKernel` dies with

```
JIPRuntimeException: Unable to load Kernel: java.lang.NullPointerException
```

That is a missing bootstrap, not a regression. Either run the bootstrap, or set
`-debug` (CLI) / `JIPDebugger.debug = true` (embedded) to use the `.txt`/`.pl`
sources instead.

**When adding a Prolog library, edit three lists:** `tools/compile-libraries.pl`
(what gets compiled), `src/com/ugos/jiprolog/resources/x.pl` (what gets loaded
at startup, in both its debug and release clauses), and the legacy `compile.pl`
if you care to keep it accurate.

### Tests

`test/java/com/ugos/jiprolog/`, JUnit 5, run by `mvn verify`.

- `PrologTestBase` — boots an engine and runs goals. `valueOf(goal, "X")` for a
  binding, `solveAll(goal)` for all solutions, `canonical(term)` to render with
  `write_canonical/1`.
- `EngineBootstrapTest` — the engine boots from the compiled kernel and the
  x.pl library set is present. This is what fails first if the bootstrap broke.
- `BuiltInsTest` — the passing baseline: arithmetic, terms, control constructs,
  the database, lists.
- `ParserTest` — operator precedence and associativity, asserted through
  `write_canonical/1`. This is the regression net for `PrologParser`;
  `OperatorAsOperand` and `PriorityClash` are §19's half of it.
- `ConcurrencyTest` — four threads, one engine each. This is the harness for the
  shared-static work; every one of its tests fails against the pre-fix sources.
- `DcgTest`, `ListenerApiTest`, `ReflectionHandleTest` — the areas fixed in
  `CODE_REVIEW.md` §2, §4/§12 and §5.
- `WriteTermTest` — ISO 7.10.5 term output: that an argument is written at
  priority 999, that an operand respects the operator's associativity, and
  that `writeq/1` output reads back as the same term. `CODE_REVIEW.md` §17.
- `ResolutionTest` — unification, backtracking and cut, including whole
  programs checked against independently known answers (six queens has four
  solutions, `tak(14,10,4)` is 5). The slowest class in the suite at ~13 s.
- `IsoConformanceTest` — runs the 441-case suite in `test/resources/iso/`.

Tests run against the **release** kernel (no `JIPDebugger.debug`), so the suite
also proves the bootstrap produced a loadable kernel. Surefire uses
`reuseForks=false` — one JVM per test class — because engine state lives in
statics (`CODE_REVIEW.md` §3) and results would otherwise depend on class
ordering. Drop that setting once the statics are gone.

### The ISO conformance suite

`test/resources/iso/` is a suite written from ISO/IEC 13211-1 — plus files for
the non-ISO areas it turned out to need: modules, metacall, and the `$!`/`$!!`
internal cuts — in a small data format the runner interprets:

```prolog
iso(Section, Goal, success).            % has at least one solution
iso(Section, Goal, failure).            % has none
iso(Section, Goal, error(Formal)).      % throws error(Formal, _)
iso(Section, Goal, ball(Term)).         % throws Term, not necessarily an error/2
```

Value checks go **inside** the goal with `==/2`, so the harness never has to
match variable names:

```prolog
iso('9.1.3', (X is 7 // 2, X == 3), success).
```

To add cases, drop them in the matching `cases_*.pl` (each needs
`:- multifile(iso/3).`) or add a file and list it in `IsoConformanceTest.SUITE`.

`cases_unify.pl`, `cases_backtracking.pl` and `cases_cut.pl` carry the engine
core. Much of what they check is the **undo** path — that a goal which binds
and then fails leaves nothing bound — because that is what breaks silently:
the goal fails either way, just with stale bindings left behind. If you touch
`WAM.backtrack`, `VariableTrail` or anything in `_unify`, these are the files
that will notice.

The suite is expected to be entirely green. A case that starts failing is
either a regression or a real deviation — the latter belongs in
`CODE_REVIEW.md` with the case annotated, not quietly deleted.

Caveat, now measured rather than suspected: these cases were written against
this implementation by the same hand, so 441/441 is a weak signal. The
independent INRIA suite says so — see the next section.

### The INRIA suite

```bash
mvn package && tools/run-inriasuite.sh
```

Fetches the 1999 INRIA conformance suite into `target/` (gitignored — it is
third-party material with no stated licence, and is deliberately **not**
vendored) and runs it. **420 cases, 12 flagged, six of them real**: `number_chars/2`
and `number_codes/2` do not parse a bound number's text, `call/1` reports the
offending subterm rather than the whole goal as the `type_error` culprit, and
`bagof/setof` disagree on `^/2` nested in a disjunction. `CODE_REVIEW.md` §21
has the full classification and which six are artifacts of the suite.

Not part of `mvn verify`: it needs the network, it is not green, and the build
must not depend on a 27-year-old tarball staying reachable.

Worth knowing before quoting any conformance number: this work took the suite in
`test/resources/iso` from nothing to 441 green cases, and moved the INRIA score
by **zero**. The two suites do not overlap.

## Architecture

```
src/com/ugos/
├── jiprolog/
│   ├── JIProlog.java            standalone CLI entry point (no REPL — the
│   │                            interactive console lives in the separate
│   │                            jipgui project, not in this repo)
│   ├── engine/       (164 files) interpreter core + most built-ins
│   ├── extensions/
│   │   ├── io/       (44)  ISO streams: see/tell, get_char, read_term, ...
│   │   ├── database/ (8)   JDBC-backed clause databases
│   │   ├── reflect/  (10)  Prolog → Java reflection bridge
│   │   ├── terms/    (8)   atom/number/term manipulation
│   │   ├── system/   (5)   time, sleep, exec
│   │   └── xml/      (2)   XML reader
│   └── resources/          Prolog source of the kernel and libraries
├── io/                     pushback/line-numbering readers used by the parser
└── util/                   StringBuilderEx, SHA1, class loaders
```

### Term representation (`engine`)

Everything derives from `PrologObject` (abstract, `Serializable`):

- `Atom` — interned in a **static, JVM-global** `Atom.s_atomTable`
- `Expression` — **all** numbers, integer and float alike, stored as a single
  `double` plus a `boolean floating` flag. No BigInteger, so integers are exact
  only to ±(2^53−1) and overflow past it.
- `Variable` — mutable binding cell; `lastVariable()` walks the binding chain,
  `clear()` undoes a binding on backtracking
- `ConsCell` → `List` (Prolog lists), `Functor` (compound terms), `Clause`
- `PString` — Prolog strings
- `BuiltInPredicate` — a goal that dispatches to a Java `BuiltIn`

The `JIP*` classes (`JIPTerm`, `JIPFunctor`, `JIPList`, `JIPAtom`, `JIPNumber`,
`JIPVariable`, …) are the **public wrapper API** over these package-private
internals. Embedders only ever see `JIP*` types. Keep that boundary: don't leak
`PrologObject` subclasses through `public` signatures.

### Resolution engine (`WAM.java`)

Despite the name it is **not** a Warren Abstract Machine — there is no
compilation to WAM instructions, no register allocation, no trail/heap. It is a
structure-sharing SLD resolution interpreter over an explicit, heap-allocated
`WAM.Node` chain, so Prolog recursion depth does not consume Java stack.

Each `Node` holds a goal list, parent/previous links, the module, a lazily built
`Enumeration<PrologRule>` of candidate clauses, and a `Hashtable` of variables
bound at that node (used to undo bindings on backtracking).

- `run(Node)` — the main loop: get candidate rules, unify head, push a new node
  for the body, or walk back up to the parent on success
- `backtrack(Node)` — walks the `m_previous` chain, clearing variables, until it
  finds a node with remaining choice points
- `cut()` / `softCut()` / `strongCut()` — implement `!`, `$!` and `$!!` by
  rewriting `m_backtrack` links
- `WAMTrace` extends `WAM` to emit `JIPTraceEvent`s for the debugger

`PrologRule.nextElement` copies the whole clause on every resolution step, and
`WAM.run` allocates a `Hashtable` per node for the bindings. Together they are
about 70% of the nrev profile and the reason throughput is ~287 KLIPS, still
well below SWI-Prolog. Removing them means binding into shared structure with a
trail instead of copying — a rewrite of the resolution core, not a local change.

`bench/` holds the benchmark set. Measure before optimizing here: the profile
has already contradicted the obvious guess twice, and `CODE_REVIEW.md` §10
records what was tried and measured flat.

### Database (`GlobalDB.java`)

Predicates are keyed by `"module:name/arity"` strings in a `Hashtable`.
Modules are `$user`, `$system`, `$kernel` plus user modules. Storage is
pluggable via the `JIPClausesDatabase` interface:

- `IndexedDefaultClausesDatabase` — first-argument indexing (separate tables for
  atom, number, functor keys; vectors for list/cons/variable keys)
- `NotIndexedDefaultClausesDatabase`
- `JDBCClausesDatabase`, `TextClausesDatabase`, … in `extensions/database`

`GlobalDB.newInstance(engine)` clones the tables (shallow) so each `JIPEngine`
gets its own predicate namespace on top of one shared kernel snapshot.

### Built-ins

Two mechanisms, and it matters which one you extend:

1. **Java built-ins** — registered in the static
   `BuiltInFactory.m_builtInTable`, keyed `"name/arity"`. A fresh instance is
   created **per goal call** via `Class.newInstance()`. To add one: write a
   `BuiltIn` subclass implementing `unify(Hashtable)` (and
   `hasMoreChoicePoints()` if nondeterministic), then add a `put(...)` line to
   the static initializer.
2. **Prolog library predicates** — written in `resources/*.pl` and loaded at
   startup through `x.pl`. Prefer this when the predicate can be expressed in
   Prolog.

Arithmetic is **not** in the built-in table: `Expression.compute()` is one large
`if/else` chain over the functor name, and every new evaluable functor goes
there.

### Parser

`PrologTokenizer` → `PrologParser` (`parseNext()`), a hand-written
shunting-yard over an explicit `Stack`, with `OperatorManager` holding the
operator table. `PrettyPrinter` is the inverse (`write`, `writeq`,
`write_canonical`).

`PrologParser` is ~1250 lines with nesting up to ~15 levels and is the least
maintainable file in the tree. **It has known correctness bugs around prefix
operators** — see `CODE_REVIEW.md` §1. If you touch it, verify with
`write_canonical/1` and `=../2`, not with `write/1` (the printer can mask or
mimic a bad parse).

### Threading model

- `openSynchronousQuery` → `JIPQuery` runs on the **caller's** thread.
- `openQuery` → `AsyncWAMManager` spawns a daemon thread per query and reports
  back through `JIPEventListener` callbacks.
- `EventNotifier` runs its own daemon thread with a `Vector` event queue.

There is a **lot** of shared mutable static state (`Atom.s_atomTable`,
`BuiltInFactory.m_builtInTable`, `Clause.s_engine`/`s_translateQuery`,
`GlobalDB.sbUSER_MODULE` and friends, `JIPxReflect.s_classHandleTbl`,
`JIPEngine.defaultEngine`/`s_globalDB`). **Multiple `JIPEngine` instances in one
JVM are not fully isolated, and concurrent use across threads is not safe.** See
`CODE_REVIEW.md` §2. Do not add new mutable statics.

## Conventions

- **Naming**: `m_` instance fields, `s_` statics, Hungarian-ish prefixes
  (`str`, `n`, `b`, `db`). Built-in classes are named `PredicateNameArity`
  (`Assert1`, `Findall3`, `Sort4`, `CallN`).
- **Comments** are mixed Italian and English. Italian ones are original author
  notes — keep them, don't translate wholesale.
- **Collections**: legacy `Hashtable`/`Vector`/`Enumeration` throughout, on
  purpose (the codebase once targeted MIDP/J2ME — see the `//#ifndef _MIDP`
  markers). Match the surrounding style in existing files; prefer modern
  collections only in genuinely new, self-contained code.
- Every source file carries the AGPLv3 header. Keep it on new files.
- Large blocks of commented-out code are common (~1,800 lines). Leave existing
  ones alone unless you are deliberately cleaning up; don't add new ones.

## Gotchas

- `-debug` / `JIPDebugger.debug = true` is required whenever the `.jip`
  bootstrap has not run (hand `javac` builds, `-DskipPrologCompile`).
- Adding a Prolog library file means editing `tools/compile-libraries.pl`
  **and** `resources/x.pl` (both clauses).
- `.jip`, `lib/` and `target/` are gitignored — never commit build output.
- `write/1` and `writeq/1` bracket by operator priority (ISO 7.10.5), so
  `writeq/1` output reads back as the term it was given — `CODE_REVIEW.md` §17
  is where that was fixed and what it used to do. `WriteTermTest` is the
  regression net; if you touch `PrettyPrinter`, its round-trip test is the one
  that matters. Use `write_canonical/1` or `=../2` anyway when debugging the
  parser: it shows the structure without depending on the operator table.
- The parser enforces operand priority (ISO 6.3.4) and raises
  `syntax_error(operator_priority_clash(Op))` when a subterm binds more loosely
  than its position allows — `a ; dynamic + b`, with `dynamic` at 1150 under `;`
  at 1100. `CODE_REVIEW.md` §19 has the measurements. Two things are load
  bearing:
  - **`m_priorityZero`** records terms that are priority 0 whatever their
    functor says — bracketed (6.3.4.1) and functional-notation (6.3.3). Without
    it the check rejects `EOS = (not)` in `xio.pl` and every
    `'$system': @>(X, Y)` in the kernel.
  - **The kernel must not write `Module: \+ Goal`** with the operator. `:` is
    `600 xfy` and `\+ G` is 900. Use the functional notation the rest of
    `jipkernel.txt` uses — `'$system': \+(G)` — which is the same term.
- **Run `mvn clean verify`, not `mvn verify`,** after touching the parser, the
  kernel or the library sources. A dirty `target/` keeps the previous build's
  `.jip` files, and the tests will happily pass against a kernel the current
  sources can no longer produce. `CODE_REVIEW.md` §20.
- Integers are exact to ±(2^53−1) — `Expression.MAX_INTEGER` — and overflow
  past it with `evaluation_error(int_overflow)`. That is the limit of the
  `double` the value is held in, so it is a real boundary, not an arbitrary
  one: `18!` works, `20!` does not. If you add an arithmetic branch, bound-check
  against those constants and do integer work in `long`, never `int`.
- `WAM.run` catches `Throwable` and calls `printStackTrace()`; a
  `StackOverflowError` from deep term recursion floods stderr with tens of
  thousands of frames before the real error surfaces.
- **Paths inside a `-g` goal must use forward slashes**, on every platform. The
  goal text is parsed as Prolog, so a Windows path in a quoted atom
  (`'D:\a\proj'`) has its backslashes read as escape sequences — `\a` becomes
  BEL, `\t` becomes TAB — and the file lookup fails with "The filename,
  directory name, or volume label syntax is incorrect". This is why the POM
  passes `prolog.source.dir`/`prolog.output.dir` as relative forward-slash
  paths rather than `${project.basedir}`.
- `build.xml`'s `project.version` is stale relative to `JIPEngine`.

## Embedding API sketch

```java
// JIPDebugger.debug = true;                  // only if .jip files are absent
JIPEngine engine = new JIPEngine();
engine.consultFile("program.pl");

JIPQuery q = engine.openSynchronousQuery(
        engine.getTermParser().parseTerm("member(X, [a,b,c])"));
JIPTerm sol;
while ((sol = q.nextSolution()) != null) {
    System.out.println(sol.toString(engine));
}
q.close();
```

`releasenotes.txt` is the changelog and is maintained by hand — add an entry
there for user-visible behaviour changes.
