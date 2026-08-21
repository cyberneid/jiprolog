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
- `KnownDefectsTest` — one test per open defect in `CODE_REVIEW.md`, each
  asserting the **correct** behaviour and marked `@Disabled`. Fixing a defect
  means deleting its `@Disabled`. Never make one of these pass by editing the
  expected value to match what the engine currently does.

Tests run against the **release** kernel (no `JIPDebugger.debug`), so the suite
also proves the bootstrap produced a loadable kernel. Surefire uses
`reuseForks=false` — one JVM per test class — because engine state lives in
statics (`CODE_REVIEW.md` §3) and results would otherwise depend on class
ordering. Drop that setting once the statics are gone.

Coverage is thin and deliberately so: it pins current behaviour so the parser
and engine work in `CODE_REVIEW.md` has something to fall back on. The next
step is wiring up a real ISO conformance suite.

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
  `double` plus a `boolean floating` flag. There is no `long` and no BigInteger.
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

`Clause`/`PrologRule` unification allocates a fresh `Hashtable` per resolution
step. This is the main reason throughput is ~90 KLIPS on nrev (roughly two
orders of magnitude below SWI-Prolog). Treat that as a known characteristic, not
something a local change will fix.

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
- `PrettyPrinter` output is not a reliable view of term structure; use
  `write_canonical/1` or `=../2` when debugging the parser.
- Integers overflow at ±2^31 with `evaluation_error(int_overflow)` — `20!`
  fails. This is by design of the `double`-backed `Expression`, not a bug you
  should "fix" locally.
- `WAM.run` catches `Throwable` and calls `printStackTrace()`; a
  `StackOverflowError` from deep term recursion floods stderr with tens of
  thousands of frames before the real error surfaces.
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
