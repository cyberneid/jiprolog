# JIProlog — Code Review

Review of JIProlog 4.1.7.1 (`master` @ `a5234d6`), 251 Java files / ~34,900 LOC.

Every finding below was reproduced against a build of this tree
(`javac -encoding ISO-8859-1`, JDK 21) — the transcripts are inline. Findings
are ordered by severity, most severe first.

---

## Summary

The engine is a coherent, self-contained SLD resolution interpreter with a
genuinely good public API boundary (`JIP*` wrappers over package-private
internals), pluggable clause storage, real module support, first-argument
indexing, and an ISO error model that gets the common cases right (`catch/3`,
`findall/3`, `bagof/setof`, cut, if-then-else and the logical update view all
behave correctly under test).

The problems are concentrated in three places:

1. **The parser silently corrupts terms containing a prefix operator followed by
   an infix operator.** `X is -7 + 1` evaluates to `8`. This is a wrong-answer
   bug with no diagnostic, and it is the most important thing in this document.
2. **Shared mutable static state** makes multiple `JIPEngine` instances
   non-isolated and the library not thread-safe — including DCG translation,
   which runs against a statically pinned engine using static scratch terms.
3. **No tests, no working build.** `build.xml` cannot run from a clean clone and
   there is not a single automated test, so none of the above was caught.

Nothing here suggests the design is wrong. The resolution engine, the database
layer and the API boundary are sound. The findings are localized defects and
accumulated infrastructure debt.

**Status.** §14 (build and CI) is done: the project builds with Maven, produces
a working jar, and runs its tests on four JDK/OS combinations. §4, §5 and the
two resolved bullets of §12 are fixed, each with tests. §1, §2, §3 and the rest
are open; `KnownDefectsTest` carries a disabled, already-written test for each
defect that can be expressed at the Prolog level.

---

## 1. Critical — prefix-operator parsing silently corrupts terms

**`src/com/ugos/jiprolog/engine/PrologParser.java:1022-1027`**

```java
if(op.isPrefix())//prefix
{
    if((op.getName().equals("-") || op.getName().equals("+")) && obj1 instanceof Expression)
    {
        return Expression.createNumber(op.getName() + obj1.toString());
    }
```

Negative numeric literals are not recognized by the tokenizer. Instead, `-`/`+`
are parsed as ordinary prefix operators and folded into a number *after the fact*
in `resolveOperator`, by string-concatenating the operator name onto
`obj1.toString()` and re-parsing it. The fold only fires when the operand has
already been reduced to a bare `Expression`, which depends on what follows.

The result is two different, context-dependent corruptions. Both are silent —
no syntax error, no warning.

### 1a. In a body or at top level, the prefix operator is dropped

```prolog
X = -7 + 1,  X =.. L.      % L = [+, 7, 1]        expected [+, -7, 1]
Y is -7 + 1.               % Y = 8                expected -6
Z is -7 - 1.               % Z = 6                expected -8
```

Verified:

```
univ([+,7,1])
v(8)
univ([-,7,1])
v(6)
```

`X is -7 + 1` yielding `8` is arithmetic returning the wrong number for input a
first-year textbook would use.

### 1b. Inside an argument list, the *operand* is deleted

```prolog
show(T) :- T =.. L, write(L), nl.

show(-7 + 1).      % [-, +(1)]     the integer 7 is gone
show(-a + 1).      % [-, +(1)]     the atom a is gone
show(f(-7 + 1)).   % [f, - + 1]    same, nested
```

Verified:

```
univ([-,+ 1])
univ([-,+ 1])
univ([f,- + 1])
```

A subterm the user wrote is discarded outright and parsing continues.

### 1c. Prefix `-` binds too loosely against higher-priority infix operators

```prolog
X is -7 mod 2.     % -1     expected 1   (parses as -(mod(7,2)))
Y is -2 ** 2.      % -4     expected 4.0 (parses as -(**(2,2)))
```

ISO 6.3.1.2 requires `-` immediately followed by a numeric token to denote the
negative constant, so `-7 mod 2` is `mod(-7, 2)` = 1.

### Also wrong in the same area

- `- 7` (with a space) is folded to the integer `-7`; ISO requires the compound
  `-(7)`. `integer(- 7)` succeeds here and must not.
- `+1` is folded to `1`, discarding the `+(1)` structure: `X = 7 * +1` gives
  `*(7,1)`.
- The fold round-trips through `PrettyPrinter` (`obj1.toString()`) and
  `Double.valueOf` — slow, and it loses the float/integer distinction for
  exponent-only literals, since `Expression.createNumber(String)` decides
  `floating` by testing `strNum.contains(".")`.

### Recommendation

Move negative-literal recognition into `PrologTokenizer`, where the standard
puts it: when `-` is immediately followed (no layout) by a digit **and** the
preceding token is not a name/number/close-bracket, emit a single negative
number token. Then delete the fold at `PrologParser.java:1024` entirely and let
the shunting-yard treat `-` as an ordinary `200 fy` prefix operator.

That change also fixes 1a and 1b, which are downstream of the same root cause:
the parser's prefix/infix reduction path assumes an operand it has already
consumed is still on the stack.

**Please add regression tests for these exact cases before touching anything
else** — this area has no coverage at all today, and `write/1` will happily
print a corrupted term in a way that looks plausible. Assert on
`write_canonical/1` or `=../2`.

---

## 2. Critical — DCG translation is pinned to a static engine and uses static scratch terms

**`src/com/ugos/jiprolog/engine/Clause.java:40-42, 176-198`**

```java
private static JIPEngine s_engine = null;
private static Functor   s_translateQuery = null;
private static ConsCell  s_translateParams = null;
...
if(s_engine == null)
    s_engine = JIPEngine.getDefaultEngine();   // == the FIRST engine ever constructed
...
s_translateParams.setHead(func);                            // mutating shared state
((ConsCell)s_translateParams.getTail()).setHead(vTranslated);
WAM wam = new WAM(s_engine);
```

Two independent defects:

- **Wrong engine.** `JIPEngine.getDefaultEngine()` returns whichever engine was
  constructed first in the JVM (`JIPEngine.java:130-134`). Create engine A, then
  engine B, then consult a `-->/2` rule into B: the translation runs against
  **A's** database and operator table. In a servlet container or any app with a
  per-request/per-tenant engine, DCG rules are translated against a stranger's
  state.
- **Not thread-safe.** `s_translateQuery` / `s_translateParams` are static and
  *mutated in place* on every translation. Two threads consulting DCG rules
  concurrently overwrite each other's arguments mid-flight, producing silently
  wrong translations.

**Fix:** make the translation query a local, and take the `JIPEngine` from the
clause's own context rather than a static. `getClause` is already called from
paths that know the engine (`GlobalDB`, `Consult1`, `Load1`) — thread it through
instead of reaching for a global.

---

## 3. High — shared mutable statics break engine isolation and thread safety

`JIPEngine` is documented as an instantiable engine, and new predicates *are*
correctly isolated (verified: `assertz` into engine 1 is invisible to engine 2,
and kernel predicates are protected by `JIPPermissionException`). But a
significant amount of state escapes that boundary:

| Location | State | Consequence |
|---|---|---|
| `Atom.java:34` | `static Hashtable s_atomTable` | JVM-global atom table, never trimmed; `containsKey`+`put` is non-atomic (`Atom.java:52-65`) |
| `BuiltInFactory.java:26,155` | `static m_builtInTable` + `static addExternalPredicate` | `extern/3` in one engine registers the predicate in **every** engine in the JVM |
| `Clause.java:40-42` | static engine + scratch terms | see §2 |
| `GlobalDB.java:49-52` | `static StringBuilderEx sbUSER_MODULE`, `sbSYSTEM_MODULE`, `sbKERNEL_MODULE`, `sbUSER_MODULE_AUX` | see below |
| `Variable.java:39-40` | `static StringBuilderEx sbANONYMOUS`, `sbSHADOW` | see below |
| `Variable.java:34` | `static long counter` | non-atomic `counter++`; duplicate variable timestamps under concurrency, and `timestamp()` drives standard order of terms |
| `JIPxReflect.java:55` | `static Hashtable s_classHandleTbl` | see §5 |
| `JIPEngine.java:70-73,87` | `s_classProvider`, `s_classLoader`, `s_globalDB`, `defaultEngine` | unsynchronized lazy init in the constructor (`JIPEngine.java:130-134`) — two threads constructing engines concurrently race |

### The shared `StringBuilderEx` buffers are the worst of these

```java
// GlobalDB.java:50
public static final StringBuilderEx sbUSER_MODULE = new StringBuilderEx(USER_MODULE).append(":").setInitial();
// used ~20 times, e.g. GlobalDB.java:96
String def = sbUSER_MODULE.resetToInitialValue().append(strPredName).toString();
```

One mutable `char[]` buffer, shared process-wide, used to build **database
lookup keys**. `StringBuilderEx` has no synchronization at all. Two threads
resolving predicates concurrently interleave `resetToInitialValue()` and
`append()` and produce keys like `$user:apppend/3` — a lookup against a
corrupted key, i.e. a spurious "undefined predicate" or, worse, a hit on the
wrong predicate.

`Variable.java:39-40` has the same pattern for generating variable names, so two
threads can mint two distinct variables with the same name.

This is a micro-optimization (avoiding a `StringBuilder` allocation per lookup)
that costs correctness. The allocation it saves is noise next to the `Hashtable`
allocated per resolution step in `WAM.run`. **Make them local variables.**

### Also

`GlobalDB.newInstance` (`GlobalDB.java:61-78`) `clone()`s the tables, which is a
*shallow* copy: the `JIPClausesDatabase` **values** are shared between engines.
New predicate names are safe (they land in the cloned table), and kernel
predicates are protected by the static-procedure check — but any dynamic
predicate that exists in the shared snapshot is a live cross-engine channel.
Deep-copy the databases, or make the sharing explicit and documented.

**Recommendation:** treat "no new mutable statics" as a rule, then work the
table above down. `Atom.s_atomTable` → `ConcurrentHashMap` with
`computeIfAbsent`; `Variable.counter` → `AtomicLong`; the `StringBuilderEx`
buffers → locals; `defaultEngine`/`s_globalDB` init → synchronized or a static
holder.

---

## 4. High — `removeEventListener` never removes anything

> **Fixed.** The inverted condition is gone; `ListenerApiTest` covers it.

**`src/com/ugos/jiprolog/engine/EventNotifier.java:59-63`**

```java
public final synchronized void removeEventListener(final JIPEventListener listener)
{
    if(!m_EventListenerVect.contains(listener))     // <-- inverted
        m_EventListenerVect.removeElement(listener);
}
```

The condition removes the listener only when it is **not** in the list.
`removeTraceListener` 12 lines below has the correct form
(`if(m_TraceListenerVect.contains(listener))`), which makes this a clear typo
rather than intent.

Verified at runtime:

```
after add   : 1
after remove: 1  <-- expected 0
```

Consequence: listeners can never be detached. Any long-running host that
attaches a listener per query/request leaks them, and every event is delivered
to every stale listener.

Note also that `addEventListener` uses `insertElementAt(listener, 0)` while
`addTraceListener` does the same — listeners fire in reverse registration order.
Harmless, but undocumented.

**Fix:** drop the `!`.

---

## 5. High — reflection object handles collide ~~and leak~~

> **Fixed.** Handles now come from a counter, with an `IdentityHashMap` keeping
> one handle per object. The "nothing is ever removed" bullet below was wrong
> and is corrected in place — see the note at the end of this section.

**`src/com/ugos/jiprolog/extensions/reflect/JIPxReflect.java:55, 62-67`**

```java
private static Hashtable s_classHandleTbl;      // static, JVM-global

public static final JIPAtom putObject(Object object)
{
    String strHandle = "#" + object.hashCode();
    s_classHandleTbl.put(strHandle, object);
    return JIPAtom.create(strHandle);
}
```

- The handle is `Object.hashCode()`. Hash codes are **not unique** — two live
  Java objects sharing one collide, the second `put` evicts the first, and
  Prolog code holding the first handle silently starts operating on the second
  object. For any object with a value-based `hashCode` (`String`, boxed
  numbers, records, most value classes) this is not a remote possibility.
- Entries are not dropped automatically, so an object stays reachable — and
  its whole object graph pinned — until it is released explicitly.
- The table is static, so handles are shared across all engines.

**Correction to the original review.** This section first claimed that nothing
is ever removed and that the extension needed a release predicate added. That
was wrong: `releaseObject` exists, and `xreflect.pl` exposes it as
`release_object/1`. The leak is therefore the ordinary one of any manual
release scheme — a program that forgets to call it — not a missing capability.
The collision defect was real, and reproduced:

```prolog
create_object('java.util.ArrayList', [], H1),
create_object('java.util.ArrayList', [], H2),
invoke(H1, add('java.lang.Object'), [x], _),
invoke(H1, size, [], S1), invoke(H2, size, [], S2).

% before: H1 = H2 = '#1',  S1 = 1, S2 = 1   - one object behind two handles
% after:  H1 = '#1', H2 = '#2',  S1 = 1, S2 = 0
```

Two empty `ArrayList`s both hash to 1, so both got handle `#1` and the second
`put` evicted the first.

**Fixed** by keying handles off a counter instead of `hashCode()`, with an
`IdentityHashMap` from object to handle so that one object still maps to one
handle — otherwise two `invoke` calls returning the same object would hand back
handles that compare unequal in Prolog. Still open: the table is static, so it
is shared across engines. That belongs with §3.

---

## 6. High — query handles are identity hash codes

**`AsyncWAMManager.java:46-49`, `JIPEngine.java:936`, `BuiltIn.java:59-62`**

```java
public final int getHandle()  { return m_wam.hashCode(); }
...
m_prologTable.put(new Integer(container.getHandle()), container);
```

The public integer query handle is `Object.hashCode()` of the `WAM`. Identity
hash codes are not unique, so two concurrently open queries can collide; the
second `put` silently evicts the first from `m_prologTable`, and the evicted
query becomes unreachable — `closeQuery` on it throws
`JIPInvalidHandleException` and its daemon thread is never cleaned up.

**Fix:** allocate handles from an `AtomicInteger` per engine.

---

## 7. High — unfiltered Java deserialization of `.jip` files

**`Load1.java:97`, `GlobalDB.java:717`**

```java
final ObjectInputStream oins = new ObjectInputStream(ins);
while((obj = (PrologObject)oins.readObject()) != null) { ... }
```

`load/1` and the release-mode kernel loader deserialize arbitrary Java objects
with no `ObjectInputFilter`. A malicious `.jip` file is a gadget-chain RCE, and
the cast to `PrologObject` happens *after* `readObject` has already run
attacker-controlled `readObject`/`readResolve` code. The `.jip` format is a
plausible distribution artifact ("compiled Prolog library"), so this is not a
purely theoretical path.

**Fix (Java 9+):**

```java
ObjectInputStream oins = new ObjectInputStream(ins);
oins.setObjectInputFilter(ObjectInputFilter.Config.createFilter(
        "com.ugos.jiprolog.engine.*;java.lang.*;java.util.*;!*"));
```

and set a depth/count limit. Document that `.jip` files must come from a trusted
source. (The `extensions/reflect` package deliberately exposes `Class.forName` +
`Method.invoke` to Prolog — that is the advertised "Prolog calls Java" feature
and is fine, but it does mean consulting untrusted Prolog is equivalent to
running untrusted Java. Worth saying so in the README.)

---

## 8. Medium — `AsyncWAMManager` has an unsynchronized cross-thread handshake

**`src/com/ugos/jiprolog/engine/AsyncWAMManager.java`**

`m_workerThread`, `m_result` and `m_bNext` are written by the worker thread and
read by the caller thread with no `volatile` and no synchronization:

```java
final boolean isRunning() { return m_workerThread != null; }   // may never observe null
...
m_workerThread = null;      // worker thread, unsynchronized
m_engine.update(this);
```

Under the Java memory model the caller may never observe the write, so
`isRunning()` can report `true` forever and `nextSolution`/`hasMoreChoicePoints`
throw `JIPIsRunningException` on a query that finished long ago. `m_result` has
the same problem, and is additionally **not reset on the `m_bNext` success
path** — after a successful `nextSolution` it still holds the previous value.

**Fix:** make the three fields `volatile` (or move the handshake into the
`synchronized(container)` block that `JIPEngine.nextSolution` already uses).

---

## 9. Medium — all numbers are `double`, with a 32-bit integer range

**`src/com/ugos/jiprolog/engine/Expression.java`**

```java
private final double m_dValue;
private boolean floating = false;
...
floating = (int)dNum != dNum;     // 32-bit test on a 64-bit value
```

Consequences:

- `X is 13 * 479001600` (i.e. `13!`) → `evaluation_error(int_overflow)`. `20!`
  is unreachable. Roughly 20 sites guard on `Integer.MAX_VALUE`.
- Integral values outside ±2^31 are classified as **floats** by the
  `(int)dNum != dNum` test, even though a `double` holds every integer up to
  2^53 exactly. So the representable-but-rejected band 2^31…2^53 is lost twice
  over.
- `Expression.createNumber(String)` decides float-ness with
  `strNum.contains(".")`, so `1e10` and `1.0e10` classify differently.

ISO permits a bounded implementation (`flag(bounded, true)`), so this is
conforming — but `max_integer` at 2^31 while the underlying storage is a
`double` is an arbitrary limitation, not a representation limit.

**Suggested direction:** split `Expression` into integer and float variants
backed by `long` and `double` (and optionally `BigInteger` behind an unbounded
flag). That is a real change, so at minimum: raise the bound to 2^53, fix
`floating` to test `(long)dNum != dNum`, and make `createNumber(String)` decide
float-ness from the token type rather than by searching for a `.`.

### Related conformance deviations found under test

| Goal | JIProlog | ISO |
|---|---|---|
| `X is 2 ** 10` | `1024` (integer) | `1024.0` — `**` is float exponentiation |
| `X is 10 / 5` | `2.0` | `2` when both args are integers and the division is exact |
| `integer(- 7)` | `true` | `false` — `- 7` with layout is `-(7)` |
| error `context/2` | `context(error(type_error(atom,1)), file(undefined,0))` | `context(Name/Arity, Message)` |

The error-context shape is a compatible extension in spirit, but it nests the
whole error term inside its own context, which is redundant and will confuse
portable code that pattern-matches on `context/2`.

---

## 10. Medium — hot-path performance

Measured: **~89 KLIPS** on nrev30 × 2000 (JDK 21, this tree). That is roughly
two orders of magnitude below mainstream Prolog systems. Three specific causes,
in rough order of cost:

1. **`WAM.run` allocates a `Hashtable(13)` per resolution step**
   (`WAM.java:403`), plus `PrologObject.unify` allocates a *second*
   `Hashtable(10)` per unification attempt and then copies the survivors into
   the first (`PrologObject.java:77-105`). Two hash tables per attempted clause
   match, including for clauses that fail to unify.
2. **Every built-in call allocates a new instance via reflection.**
   `BuiltInFactory.getInstance` (`BuiltInFactory.java:199-227`) calls
   `Class.newInstance()` per goal — deprecated since Java 9, and it launders
   checked exceptions from the constructor. Stateless built-ins (the majority)
   could be singletons; stateful ones could implement a cheap `newInstance()`
   override that just calls `new`.
3. **`Expression.compute` is a ~120-branch `if/else` chain of `String.equals`**
   (`Expression.java:113-760`), walked on every arithmetic evaluation, with the
   common operators not at the front. A `switch` on the functor name (Java 7+)
   or a `Map<String, Evaluable>` would be both faster and far more readable —
   the method is currently ~650 lines.

Note `Variable.timestamp()` and `rootVariable()` walk the parent chain on every
call, and `timestamp()` is used by `lessThen`/`termEquals` — i.e. inside
sorting. Worth caching.

---

## 11. Medium — error handling and resource management

- **`WAM.java:325`** — in `backtrack`, an unexpected null `m_ruleEnum` is
  handled by `System.out.println(curNode.getGoal())` and falling through. That
  is a debug print left in a released engine, on a path that indicates a broken
  invariant. It should throw, or at minimum go to the error stream via the
  notifier.
- **`WAM.java:593`** — `catch(Throwable th) { th.printStackTrace(); ... }`. A
  `StackOverflowError` from deep term recursion dumps tens of thousands of
  frames to stderr before the wrapped `JIPJVMException` surfaces. Reproduced
  with a 50,000-element list. Drop the print (the exception is wrapped and
  rethrown with full context anyway) and handle `StackOverflowError` explicitly
  — there is already a commented-out block that did exactly that
  (`WAM.java:543-568`).
- **Stack-depth limits.** Prolog recursion is heap-allocated and safe, but term
  *traversal* is recursive: `AcyclicTerm1.acyclic`, `ConsCell.copy`,
  `_unify`, `PrettyPrinter.printTerm`. Long lists overflow the Java stack.
  Worth documenting, and worth converting at least `acyclic` and `copy` to
  iterative form for list spines.
- **Streams not closed on the exception path.** `Load1.java:97-114`,
  `GlobalDB.java:717-733`, `Compile2.java:138,155,169,185` — `close()` is called on the
  success path only. Use try-with-resources.
- **Empty catch blocks** at `ClassProvider.java:97`,
  `TextClausesEnumeration.java:145`, `TextAtomClausesEnumeration.java:164`,
  `PrologClausesEnumeration.java:141`, `JDBCClausesDatabase.java:483`,
  `Compile2.java:138,155,169,185`. Each swallows an `IOException`/`SQLException`
  entirely. At least log.
- **54 `printStackTrace()` calls and 32 `System.out.println` calls** across the
  engine. A library should route diagnostics through its own notifier, not
  stdout.

---

## 12. Medium — encapsulation and Java contracts

- ~~**`JIPEngine.getEventListeners()` / `getTraceListeners()`** return the live
  internal `Vector`.~~ **Fixed:** both now return a copy. A copy rather than an
  unmodifiable view because the methods are public API declared to return
  `Vector`, and a view would have meant changing the return type.
- **`PString` overrides `hashCode()` without `equals()`**
  (`PString.java:239-241`). Two equal strings hash alike but are not `equals`,
  so they occupy separate hash-table entries in the same bucket — the worst of
  both worlds. `ConsCell`'s `hashCode` is commented out (`ConsCell.java:552`)
  while `Expression` and `Atom` implement both. Make the policy uniform.
- **`Expression.hashCode()`** is `(int)m_dValue` for integers, which saturates
  for large magnitudes; `IndexedDefaultClausesDatabase` keys a `Hashtable` on
  `Expression`, so numeric first-argument indexing degrades.
- ~~**`EventNotifier.finalize()`**~~ **Fixed:** removed. It nulled
  `m_workerThread` at collection time, but the worker thread holds a reference
  to the notifier, so it could never have run.
- **`getClass().forName(...)`** across `extensions/reflect` — a static method
  invoked on an instance. It reads as if it uses that object's class loader; it
  does not. Use `Class.forName(...)` explicitly, and pass the intended loader.
- **`new Integer(...)`** as `Hashtable` keys throughout `JIPEngine` — deprecated
  boxing constructor; `Integer.valueOf` caches.

---

## 13. Low — maintainability

- **`PrologParser.java`** — 1,254 lines, one method with nesting to ~15 levels
  and branches distinguished only by Italian comments. This file is where §1
  lives, and its structure is why the bug survived. It is the strongest
  candidate in the tree for a rewrite (a small precedence-climbing parser over
  the existing `OperatorManager` would be a few hundred readable lines).
- **`EventNotifier.run()`** — 11 near-identical `case` blocks differing only in
  which listener method is called. Collapses to a small dispatch table.
- **`Expression.compute()`** — ~650 lines, one method. See §10.
- **~1,865 lines of commented-out code** across the tree, plus `removed/` (6
  files) and `todo/` (5 files) directories of dead sources kept in the repo.
  `.classpath` excludes 11 files by name, several of which no longer exist.
  Git history is the place for this; deleting it would make the live code much
  easier to read.
- **`.settings` pins source/target 1.5.** The code compiles clean on 21. Even
  staying conservative, 8 would enable try-with-resources, `switch` on strings,
  diamond, and `AtomicLong` idioms that several findings above ask for.
- **Version drift:** `build.xml` says `4.1.6.1`, `JIPEngine` says `4.1.7.1`,
  `JIProlog.VERSION` says `3.2`. Derive them from one source.

---

## 14. Low — build and project infrastructure

This is the finding with the highest leverage, because it is what would have
caught §1, §4 and §5.

- **No test suite.** Not one automated test in 251 files. For a language
  implementation, where the contract is a published standard with a public
  conformance suite, this is the gap that matters most.
- **`build.xml` does not work from a clean clone.** It references a sibling
  `../jipgui` project, a `../deploy` tree, and
  `C:\Program Files\proguard5.3.3\lib\proguard.jar`.
- **Source encoding is ISO-8859-1 and unspecified.** `javac` on any modern JDK
  (UTF-8 default) fails with 68 "unmappable character" errors. Any build file
  must pass `-encoding ISO-8859-1` — or, better, convert the tree to UTF-8 once
  (`iconv`) and set `-encoding UTF-8`.
- **No CI.** No `.github/workflows`.

### Suggested order of work

1. Add a Maven or Gradle build with `encoding` set, and a GitHub Actions
   workflow that compiles on JDK 8/17/21.
2. Add JUnit and wire up the **Prolog ISO conformance suite** (Ulrich
   Neumerkel's `vanilla` / the `inriasuite` tests are the standard choice, and
   they run as plain Prolog). Record the current pass rate as a baseline —
   that alone is a valuable artifact for a project claiming "a high degree of
   compliance".
3. Add regression tests for §1's exact goals, then fix §1.
4. Fix §4 and §5 (both are small and self-contained).
5. Work through §2 and §3 with the test suite as a safety net.

---

## What is good, and worth preserving

- **The `JIP*` API boundary is genuinely well done.** Package-private internals,
  a clean public wrapper layer, no leakage of `PrologObject` into public
  signatures. That is why the engine is embeddable at all, and it should be
  defended in review.
- **`JIPClausesDatabase` as a pluggable storage interface** — with JDBC, text
  and indexed in-memory implementations — is a nice piece of design and rare in
  a Prolog of this size.
- **Correct behaviour under test** for cut (including the `!>`/`$!`/`$!!`
  soft- and strong-cut extensions), if-then-else, `catch/3` + `throw/1`,
  `findall/bagof/setof`, `msort/keysort/sort`, `copy_term/2`, `numbervars/3`,
  module-qualified goals, and the logical update view. The ISO error terms for
  the common cases (`type_error`, `instantiation_error`, `evaluable`,
  `callable`, `permission_error(modify, static_procedure, _)`) are all correct.
- **First-argument indexing** with separate tables per key type.
- **Heap-allocated resolution nodes** — Prolog recursion depth is bounded by
  heap, not by the Java stack. Many small Java Prologs get this wrong.
- **Zero runtime dependencies**, which is what makes "pure Java 100%" a real
  claim rather than a slogan.

---

*Review conducted on branch `dev`. Findings §1, §4, §9, §10 and §12 were
reproduced against a local build; the transcripts are inline above.*
