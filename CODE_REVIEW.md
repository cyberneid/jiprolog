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

The problems are concentrated in five places:

1. ~~**The parser silently corrupts terms containing a prefix operator followed
   by an infix operator.** `X is -7 + 1` evaluates to `8`.~~ **Fixed.** Two
   causes, neither where this review first looked: prefix `-` was declared
   `500 fx` instead of ISO's `200 fy`, and the negative-literal fold ignored
   layout.
2. ~~**Shared mutable static state** makes multiple `JIPEngine` instances
   non-isolated and the library not thread-safe — including DCG translation,
   which runs against a statically pinned engine using static scratch terms.~~
   **Mostly fixed**, and `ConcurrencyTest` now demonstrates it: all five of its
   tests fail on the pre-fix code.
3. ~~**No tests, no working build.**~~ **Fixed.** Maven, CI on four JDK/OS
   combinations, 67 tests.
4. ~~**Goal normalisation writes into the caller's clause body**, so a bare
   metacall re-runs its first solution forever and a module-qualified goal
   loses its qualifier after the first solution.~~ **Fixed** (§16). Both give
   wrong answers rather than errors, and both ship in 4.1.7.1.
5. ~~**Neither the printer nor the parser bounds operand priority.**
   `writeq/1` output was not re-readable, and `X = not ; c` was read as
   `=(X, ;(not,c))`.~~ **Fixed** (§17, §19).

Nothing here suggests the design is wrong. The resolution engine, the database
layer and the API boundary are sound. The findings are localized defects and
accumulated infrastructure debt.

**Status.** §14 (build and CI) is done: the project builds with Maven, produces
a working jar, and runs its tests on four JDK/OS combinations. The independent
INRIA suite runs too (§21): 420 cases, six real deviations, and a score the
review work had not moved. One of the six is now fixed (§22) and it is down to
five. §1, §2, §4, §5,
§9, §10, §15, §16, §17, §19, §20, §22 and the two resolved bullets of §12 are
fixed; §3 all but the built-in table. 99 tests and a 465-case conformance suite, none
disabled. What remains is §11 (error handling and resource management), §13
(maintainability) and §18, plus the deeper items §10 lists as still open.

---

## 1. Critical — prefix-operator parsing silently corrupts terms

> **Fixed**, in two steps, and the diagnosis below was wrong about where the
> main bug was. See "Root cause, corrected" and "The negative-literal fold"
> at the end of this section.

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
`obj1.toString()` and re-parsing it.

The observed symptoms were, all silent — no syntax error, no warning:

| source | parsed as | should be |
|---|---|---|
| `X is -7 + 1` | `+(7,1)` → **8** | `+(-7,1)` → -6 |
| `X is -7 - 1` | `-(7,1)` → **6** | `-(-7,1)` → -8 |
| `f(-7 + 1)` | `f(-(+(1)))` — the `7` deleted | `f(+(-7,1))` |
| `f(-a + 1)` | `f(-(+(1)))` — the `a` deleted | `f(+(-(a),1))` |
| `X is -7 mod 2` | `-(mod(7,2))` → -1 | `mod(-7,2)` → 1 |
| `X is - a mod b` | `-(mod(a,b))` | `mod(-(a),b)` |
| `- - 1` | **syntax error** `not_assoc_operator(-)` | `-(-(1))` |

### Root cause, corrected

The original diagnosis above blamed `PrologParser.resolveOperator` and
recommended moving negative-literal recognition into `PrologTokenizer`. That
was wrong. The parser was working from a bad operator table:

**`src/com/ugos/jiprolog/engine/OperatorManager.java:77-78`**

```java
put(500, "fx", "-");
put(500, "fx", "+");
```

ISO 6.3.4.4 puts prefix `-` at **200 fy**. Two consequences, and they account
for every row of the table above:

- At priority 500 the prefix operator outranks everything at 400 (`mod`, `rem`,
  `*`, `/`, `<<`, `>>`), so it swallowed them whole: `- a mod b` became
  `-(mod(a,b))`. The operand-deletion cases were the same mispriced reduction
  reaching the "both operators are prefix" branch of `resolveOperator`, where
  the already-consumed operand is not on the stack.
- `fx` is non-associative, so a prefix operator could not take another prefix
  operator of the same priority as its operand, and `- - 1` was rejected
  outright.

**Fixed** by declaring them `200 fy`. That one change fixes every row above and
changes nothing else: a 62-term parser corpus covering precedence,
associativity, lists, curly terms, control constructs and numeric literal
syntax is byte-identical before and after, and the kernel plus all thirteen
libraries still bootstrap. `ParserTest` now covers the corpus.

The lesson for the next person: the parser is hard to read and was the obvious
suspect, but the defect was in a data table thirty lines long. Check the
operator priorities against the ISO table before reading `PrologParser`.

### The negative-literal fold, and layout

A second, independent defect lived at `PrologParser.java:1024`: the fold applied
whether or not layout separated the sign from the numeral. ISO 6.3.1.2 forms
the negative constant only when the sign is followed *directly* by the numeral.

| source | was | now |
|---|---|---|
| `- 7` | `-7`, and `integer(- 7)` succeeded | `-(7)`, `integer(- 7)` fails |
| `f(- 1)` | `f(-1)` | `f(-(1))` |
| `-2 ** 2` | `-(**(2,2))` → -4 | `**(-2,2)` |
| `- 2 ** 2` | `-(**(2,2))` | `-(**(2,2))` — unchanged, and correct |

The last two rows are the point: an adjacent sign has to beat a priority-200
operator, and a separated one must not.

**Fixed** by recognizing the sign on the token instead of after the operator has
been reduced. The machinery was already in the tree, disabled on both sides —
`PrologParser.sign` and `PrologTokenizer.TOKEN_SIGN`/`STATE_SIGN`. What it
needed was one token of lookahead, plus the observation that since the tokenizer
emits `TOKEN_WHITESPACE` as a real token, "the next token is a number" already
means "no layout intervened". So, in `translateTerm`, before the token switch:
if the token is `-` or `+` **in operand position** — the term stack is empty or
has an operator on top, which is what keeps the `-` in `a-1` infix — peek one
token; if it is a number, set `sign` and let the existing `TOKEN_NUMBER` case
apply it; otherwise push the token back. `PrologTokenizer` gained a `pushBackToken`
with its own slot, kept separate from `m_nextToken` so a push-back cannot
clobber a token the tokenizer had already queued for itself. The fold is gone.

Verified on the corpus plus 25 further edge cases: `-0x1f` → -31, `-0'a` → -97,
`- -1` → `-(-1)`, `1 - -1` → `-(1,-1)`, `2 -1` → `-(2,1)` (infix, because the
minus is not in operand position), `'-'(1)` → `-(1)` (a quoted minus is an atom,
never a sign), and `foo(- 1, -1)` → `foo(-(1),-1)`.

One deliberate non-change: `+` keeps folding into the literal, so `+1` reads as
`1`, not `+(1)`. ISO defines the rule for `-` only, and SWI reads `+1` as
`+(1)`, so this is a JIProlog extension — but it is pre-existing behaviour, it
is not what §1 was about, and changing it would silently alter existing
programs. It now at least respects layout, like `-`.

---

## 2. Critical — DCG translation is pinned to a static engine and uses static scratch terms

> **Fixed.** The engine is now passed down through `Clause.getClause` and the
> translation query is built locally. `ConcurrencyTest` and `DcgTest` cover it.

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

**Fixed** by adding a `JIPEngine` parameter to both `getClause` overloads and
threading it through all ten call sites, every one of which already had an
engine to hand. The translation query is built directly rather than by parsing
`"translate(X, Y)"` and caching the result in a static, so nothing is shared
and nothing is mutated in place.

Three call sites in `JIPClause` — the public `create` entry points — have no
engine, and pass `null`. A `-->/2` term reaching those now raises rather than
being translated against an arbitrary engine; the internal callers all build a
plain head with no body and never take that branch.

`JIPEngine.defaultEngine` and `getDefaultEngine()` are gone with it: this was
their only caller, and the field kept the first engine ever constructed alive
for the life of the JVM.

Under the old code, `ConcurrencyTest.dcgTranslationIsThreadSafe` fails with
`NullPointerException: "translated" is null` — one thread's translation result
read after another thread had already overwritten the shared query term.

---

## 3. High — shared mutable statics break engine isolation and thread safety

> **Mostly fixed.** Every row of the table below is addressed except the
> built-in table, which is discussed at the end. `ConcurrencyTest` is the
> harness this section needed; all five of its tests fail on the pre-fix code.

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

### What was done

- **The `StringBuilderEx` buffers are gone.** `GlobalDB` now composes its keys
  from three `String` constants. This was the worst of them, and the failure it
  produces is vivid: under load a thread reads the buffer while another has
  reallocated its `char[]`, and the engine dies during startup with
  `Range [0, 0 + 48) out of bounds for length 37` while loading `flags.jip`.
  `Variable`'s two name buffers went the same way.
- **`Variable.counter` is an `AtomicLong`.** It drives both variable names and
  the standard order of terms, so a lost increment is not cosmetic.
- **`Atom.s_atomTable` is a `ConcurrentHashMap`,** and `createAtom` uses
  `get` then `putIfAbsent` instead of `containsKey` then `put`. Interning the
  same name twice used to be possible, which would make `==/2` report two
  identical atoms as different terms.
- **`JIPEngine.s_globalDB` is initialized under a lock.** Two threads
  constructing engines at once each loaded a kernel, and the second overwrote
  the first's snapshot — the other engine's `newInstance` then hit
  `s_globalDB is null`.
- **`Clause`'s statics are gone** — see §2.
- **`JIPxReflect`'s handle table** is synchronized — see §5.
- **Stream handles.** Found while doing the above: `InputStreamInfo` and
  `OutputStreamInfo` numbered streams with a plain `static int refCounter`
  incremented by 2, so two streams opened concurrently could share a handle.
  Both are `AtomicInteger` now. A dead `sbMODE` buffer went with them.

### Found while removing the buffers

One of the twenty-odd call sites was

```java
m_clauseTable.get(sbSYSTEM_MODULE.resetToInitialValue().append(funct.getName()));
```

with no `.toString()` — a `StringBuilderEx` passed as a `Hashtable` key. It does
not override `equals`, so that lookup could never match: the fallback search in
`$system` from `search(Functor, String)` was dead code. It now composes a
`String` and works. That is a behaviour change, and the one place in this
section where something starts happening rather than stops.

### Still open

`BuiltInFactory.m_builtInTable` is static, and `addExternalPredicate` is a
static mutating method, so `extern/3` in one engine still registers the
predicate in every engine in the JVM. Reads of the table are safe — it is fully
populated in a static initializer and only `extern/3` writes to it — so this is
an isolation problem rather than a memory-model one, and fixing it means giving
each engine its own overlay of external predicates. Left for its own change.

`GlobalDB.newInstance` still shallow-clones, so `JIPClausesDatabase` values are
shared between engines; the concrete databases synchronize their own methods,
and new predicate names land in the cloned table, so what remains is the
isolation question rather than a race.

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

## 9. Medium — all numbers are `double`, ~~with a 32-bit integer range~~

> **Fixed**, except for `/` on exact integer division (see the end of this
> section). Investigating it turned up four further defects, three of them
> silent wrong answers, recorded below.

**`src/com/ugos/jiprolog/engine/Expression.java`**

```java
private final double m_dValue;
private boolean floating = false;
...
floating = (int)dNum != dNum;     // 32-bit test on a 64-bit value
```

Numbers are all `double`s with a `floating` flag. A `double` holds every
integer up to 2^53 exactly, but the bound checks were written against
`Integer.MAX_VALUE`, so 22 bits of exact integers were thrown away:
`X is 13 * 479001600` (13!) raised `evaluation_error(int_overflow)`, and
integral values beyond ±2^31 were classified as *floats* by the
`(int)dNum != dNum` test.

**Fixed** by introducing `Expression.MAX_INTEGER` / `MIN_INTEGER` at ±(2^53−1)
and using them for all 44 bound checks, and by testing float-ness with `(long)`.
2^53−1 rather than 2^53, because a true product of 2^53+1 rounds to 2^53 and
must still be rejected. `IntegerBounds2` reads the same constants, so the
`max_integer` / `min_integer` flags follow automatically, and
`PrettyPrinter.printExpression` now formats through `long` — it was doing
`Integer.toString((int)dVal)`, which would have printed every large integer
saturated at 2147483647.

### Four further defects found while widening the range

The bound was masking these: the values that trigger them were unreachable.

| goal | was | now |
|---|---|---|
| `X is 1 << 40` | **256** | 1099511627776 |
| `X is truncate(1.0e10)` | **2147483647** | 10000000000 |
| `X is float_integer_part(3.7)` | **0.7000000000000002** | 3.0 |
| `X is -7 div 2` | **-3** | -4 |
| `X is 1 div 0` | **0** | `evaluation_error(zero_divisor)` |

- **The bit operations computed in 32-bit `int`.** `(int)dVal1 << (int)dVal2`
  makes `1 << 40` a shift by `40 & 31` == 8, so it returned 256 — silently, no
  overflow, just a wrong number. Twelve `(int)` casts across `//`, `mod`, `rem`,
  `/\`, `\/`, `<<`, `>>`, `xor`, `\`, `truncate` and the float-part functions
  are now `(long)`. This one mattered more than the bound itself: an overflow
  raises an error, a truncated shift does not.
- **`truncate` saturated.** `(int)1.0e10` is `Integer.MAX_VALUE`, so
  `truncate(1.0e10)` returned 2147483647.
- **`float_integer_part` was a copy of `float_fractional_part`** — literally the
  same expression, `dVal1 - (int)dVal1`, two branches apart.
- **`div/2` truncated instead of flooring, and did not check its divisor.** ISO
  9.1.3 has `div` round toward negative infinity, unlike `//` which rounds
  toward zero, so `-7 div 2` is -4. And with no zero check,
  `(int)(dVal1 - dVal1 % 0) / 0` evaluated to `(int)NaN / 0.0`, which the
  `Expression(int)` constructor accepted as 0 — `1 div 0` quietly returned 0
  while `//`, `mod` and `rem` all raised `zero_divisor` correctly.

`**/2` also now returns a float for integer arguments, per ISO 9.3.1;
`^/2` stays integral. That was the last row of the conformance table below.

### Still open

| goal | JIProlog | ISO |
|---|---|---|
| `X is 10 / 5` | `2.0` | `2` when the division is exact |
| error `context/2` | `context(error(type_error(atom,1)), file(undefined,0))` | `context(Name/Arity, Message)` |

`/` is left alone deliberately: implementations genuinely differ on whether
exact integer division yields an integer, and changing it would silently alter
the value of existing programs for no conformance gain that the standard
actually requires. The error-context shape is a compatible extension in spirit,
but it nests the whole error term inside its own context, which is redundant and
will confuse portable code that matches on `context/2`.

**Not attempted:** splitting `Expression` into `long`-backed integer and
`double`-backed float variants, or adding a `BigInteger` path behind an
unbounded flag. That is the real fix for a Prolog that wants unbounded
integers; what is here makes the bounded implementation honest and exact
within its bounds.

---

## 10. Medium — hot-path performance

> **Largely fixed — 15x on the benchmark set — and this section's original
> diagnosis was wrong on all three counts.** What follows is the measurement.

The original text blamed the two `Hashtable`s per unification attempt,
`Class.newInstance` per built-in call, and the `if/else` chain in
`Expression.compute`. A JFR profile of `bench/` says otherwise:

| self time | |
|---|---|
| `AbstractStringBuilder.ensureCapacityInternal` | **35%** |
| `Hashtable.get` | **26%** |
| `GlobalDB.search` | 17% |
| `String.hashCode` | 10% |
| `ConsCell.copy` | 3% |
| `PrologObject.unify` | **1%** |

Seventy per cent of the time went on building and hashing strings to look
predicates up. Unification — the thing this section pointed at — was one per
cent. `Class.newInstance` and `Expression.compute` do not appear in the profile
at all, at any depth.

### What was actually wrong

**The clause table was keyed by a composed string.** `GlobalDB.search` runs once
per inference and built `"modulo:nome/arieta"` with a `StringBuilder` each time,
then hashed it from scratch. For a predicate that resolves in `$system` it did
this three times, twice with the same key, because the module-stack loop and the
explicit `$user` lookup below it both miss first. `m_moduleIndex` is the same
content as a two-level map, so a lookup uses strings that already exist with
their hash already cached, and allocates nothing.

**The module stack never unwound, which made the interpreter quadratic.**
`getRulesEnumeration` pushes the current module for every goal; the pushes made
by nodes that backtracking abandoned were never undone. Measured on this
benchmark set: **28129 entries at peak, 7674 on average at the moment
`search` was called** — and `search` scans it. The cost of resolving a predicate
grew with the number of inferences already run. Each `Node` now records the
depth at which it generated its clauses and `backtrack` truncates to it.

**A functor name was decomposed on every construction.** `Functor(Atom, ConsCell)`
did `lastIndexOf`, two `substring`s and an `Integer.parseInt` — once per clause
copy, so once per resolution step. Atoms are interned and immutable, so the
split is cached there.

### Results

Best of three runs, whole set:

| | before | after | |
|---|---|---|---|
| `atom_churn` | 10517 ms | 89 ms | **118x** |
| `deriv` | 375 ms | 41 ms | 9.2x |
| `findall` | 201 ms | 26 ms | 7.7x |
| `db_churn` | 135 ms | 59 ms | 2.3x |
| `nrev30` | 712 ms | 508 ms | 1.4x |
| `fib20` | 81 ms | 64 ms | 1.3x |
| **total** | **12021 ms** | **797 ms** | **15.1x** |

The spread is the informative part. `nrev30` barely moves because its
predicates live in `$user`, which is the bottom of the module stack and so the
first entry tried — it was always hitting on the first iteration. `atom_churn`
moves by two orders of magnitude because its predicates live in library
modules, so every call scanned the whole leaked stack first.

On nrev this is roughly 209 KLIPS to 287 KLIPS.

### Tried and measured flat

Recorded so the next person does not repeat them:

- **Skipping repeated modules inside the stack scan.** Exactly
  semantics-preserving, and completely flat: the scan's cost is its length, not
  its body. Reverted.
- **Replacing the temporary `Hashtable` in `PrologObject.unify` with a trail.**
  About 3–6% on nrev, inside the noise on the rest. Kept, because it does
  measure better on the benchmark it targets and removes a per-attempt
  allocation, but it is not the win the original diagnosis expected.

  It also introduced a regression, and where that was caught is the point.
  `BuiltInPredicate._unify` returned false without undoing what the built-in
  had already bound. Most built-ins end in a single `unify` call, which cleans
  up after itself on failure, so nothing showed — but `integer_bounds/2`
  unifies both arguments with `&&`, so `integer_bounds(X, 999)` binds `X`,
  fails on the second argument, and left `X` bound after a failed goal. The 68
  tests in place when the change was made did not notice: they had two cut
  cases, four trivial disjunction cases, ten basic unification cases, and
  nothing at all on the undo path. See §15.

### What is left

The nrev profile is now the structure-copying cost this section should have
pointed at in the first place:

| self time | |
|---|---|
| `ConsCell.copy` + `Variable.copy` | **54%** |
| `Hashtable.addEntry` (the per-node binding table) | 18% |
| `ConsCell._unify` | 11% |

Two levers, in increasing order of ambition:

1. **The per-node binding table.** `WAM.run` allocates a `Hashtable` per node
   and `Node.clearVariables` iterates it. A trail would suit it as well as it
   suited `unify`, but `Node.m_varTbl` is threaded through
   `BuiltIn.unify(Hashtable)`, which about a hundred built-in classes override.
   Mechanical, and the compiler catches every miss, but wide.

2. **Not copying clauses at all.** `PrologRule.nextElement` copies the whole
   clause on every resolution step. A real WAM binds into a shared structure
   and undoes through a trail. That is the change that would move nrev by an
   order of magnitude, and it is a rewrite of the resolution core rather than a
   local fix.

3. **The module stack still leaks on deterministic forward execution** — depth
   fell from 28129 to 15958, not to zero. It is visible as position sensitivity:
   `atom_churn` takes 118 ms run first and 795 ms run last in a conjunction.
   Closing it means deciding what the module chain is supposed to mean, which is
   a semantics question and needs module-resolution tests that do not exist.

`bench/` holds the programs and the runner; `bench/README.md` explains why the
numbers are order-dependent and why one run is not enough.

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

- ~~**No test suite.** Not one automated test in 251 files.~~ **Fixed:** 80
  JUnit tests, one of which runs a 441-case ISO conformance suite. See the note
  at the end of this section.
- **`build.xml` does not work from a clean clone.** It references a sibling
  `../jipgui` project, a `../deploy` tree, and
  `C:\Program Files\proguard5.3.3\lib\proguard.jar`.
- **Source encoding is ISO-8859-1 and unspecified.** `javac` on any modern JDK
  (UTF-8 default) fails with 68 "unmappable character" errors. Any build file
  must pass `-encoding ISO-8859-1` — or, better, convert the tree to UTF-8 once
  (`iconv`) and set `-encoding UTF-8`.
- **No CI.** No `.github/workflows`.

### The conformance suite

`test/resources/iso/` holds 441 cases across ISO sections 7.8 (control
constructs, the cut, `catch/3` and `throw/1`), 8.2–8.5 (unification including
`unify_with_occurs_check/2`, type testing, comparison, term construction),
8.6–8.7 and 9 (arithmetic), 8.8–8.10 (clause database and all solutions), 8.15
(negation) and 8.16 (atomic term processing), plus three non-ISO files for
module-qualified goals, the bare metacall, and the `$!`/`$!!` internal cuts that
`->`/`*->` are built on. `IsoConformanceTest` runs them and fails on any
unexpected result. All 441 pass.

The cases are written from the standard rather than taken from an existing
suite. That was a deliberate choice — vendoring a third-party test corpus of
unclear licensing into an AGPL tree is a decision for the project owner, not a
side effect of adding tests — but it is also the caveat on the number. **A suite
and an implementation checked against each other by the same hand is a weaker
signal than an independent one.** Running the real `inriasuite` remains worth
doing, and would very likely come back redder.

It has earned its keep even so. Two defects fell out of writing it:

| goal | was | now |
|---|---|---|
| `X is sign(0)` | **-1** | 0 |
| `[a] =.. L` | **`[[a]]`** | `['.',a,[]]` |

- **`sign/1` had no zero case** — `if(dVal1 > 0) 1 else -1`, so `sign(0)` and
  `sign(0.0)` both returned -1. It also now preserves float-ness, per ISO 9.1.7.
- **`=../2` treated a list as atomic**, while `functor/3` already reported `'.'`
  and arity 2 for the same term. The two built-ins contradicted each other, and
  `X =.. L, Y =.. L` did not round-trip for any list. Both directions of `=..`
  now agree with `functor/3`.

### What is left in this section

Nothing, other than keeping the suite growing. Sections not yet covered:
8.11–8.14 (stream and term I/O), 8.17 (implementation-defined hooks), and the
flags in 7.11.

---

## 15. The engine core had almost no coverage

Recorded because it is the process failure behind the one regression this work
introduced, not a defect in the code.

The performance work in §10 changed `WAM.backtrack` and rewrote the unification
trail. The tests it was made against were, for those three areas:

- **cut** — two cases: `(! ; true)` and `(call((!, fail)) ; true)`
- **backtracking** — four disjunction cases, none checking solution order or
  count, none checking that bindings are restored
- **unification** — ten cases, all of the shape `1 = 1` or `f(X,b) = f(a,Y)`,
  and **none** exercising the undo path

That last gap is the one that mattered. A unification that binds and then fails
must leave its operands untouched, and when it does not, nothing looks wrong:
the goal fails either way, just with stale bindings left behind. It is the kind
of defect that surfaces three refactors later as an inexplicable wrong answer.

**Fixed** by 86 new cases — `cases_unify.pl`, `cases_backtracking.pl`,
`cases_cut.pl` — and `ResolutionTest`. The suite cases check properties one at a
time, with solution order and count asserted through `findall` inside the goal
so a case states which solutions it expects. `ResolutionTest` checks whole
programs against answers that are published facts rather than something this
engine decided: six queens has four solutions, five queens has ten, naive
reverse of 1..20 is 20..1, `tak(14,10,4)` is 5.

Worth noting for calibration: three cases in the first draft were wrong, and the
engine was right. The cut is transparent to `;/2`, so `(!, fail ; true)` fails —
it removes the alternative, then fails — and `call((c(X), !))` yields one
solution, because the cut is opaque to the *caller* but still commits inside the
call. A suite written against an implementation by the same hand gets the
implementation's benefit of the doubt; these three went the other way, which is
at least a sign the cases were derived from the standard rather than from
observed behaviour.

---

## 16. Critical — goal normalisation writes into the caller's clause body

Found by extending the coverage of §15 to the areas it had left out, and
**present in 4.1.7.1 as shipped** — reproduced against an untouched build of
`master`, not introduced by any of the work above.

Before resolving a goal, `getRulesEnumeration` normalises it: an `Atom` becomes
a `Functor`, a bound `Variable` becomes the term it is bound to, and `M:G` has
its qualifier stripped after setting the node's module. Each of those wrote the
normalised form back through `Node.setGoal`, which is
`m_callList.setHead(goal)` — and `m_callList` is *the tail of the parent's list*,
which is to say the caller's own clause body.

The body of a clause is copied once when the clause is selected, not once per
retry of a goal inside it. So the parent rebuilds its continuation from that
same list on every backtrack, and from the second solution onward it found the
previous solution's normalised goal sitting where the original goal used to be.

Two goals were affected, both silently and both with wrong answers rather than
errors.

**A variable used directly as a goal froze after its first solution.**

```prolog
g(t1).  g(t2).  g(t3).
t1.
t3.                       % t2 has no clauses

?- findall(G, (g(G), G), L).
L = [t1, t2, t3].         % 4.1.7.1 - and t2 has no clauses at all
L = [t1, t3].             % correct
```

`t2` "succeeded" because the goal actually executed all three times was `t1`.
The same shape with different functors is starker still:

```prolog
one(1).  one(2).
three(x).  three(y).  three(z).
gg(one(_)).  gg(three(_)).

?- findall(S, (gg(G), G, arg(1, G, S)), L).
L = [1, 2, _107, _109].   % 4.1.7.1 - ran one/1 twice, answers unbound
L = [1, 2, x, y, z].      % correct
```

Note that `call(G)` was always correct — `Call1` builds its own node — so the
bug only ever showed up in the bare metacall.

**A module-qualified goal lost its qualifier and resolved in the wrong module.**

```prolog
:- assert(m1:p(1)).  :- assert(m1:p(2)).  :- assert(m1:p(3)).
:- assert(m2:q(a)).  :- assert(m2:q(b)).
:- assert(m1:q(decoy1)).  :- assert(m1:q(decoy2)).

?- findall(X-Y, (m1:p(X), m2:q(Y)), L).
L = [1-a, 1-b, 2-decoy1, 2-decoy2, 3-decoy1, 3-decoy2].   % 4.1.7.1
L = [1-a, 1-b, 2-a, 2-b, 3-a, 3-b].                       % correct
```

The first solution goes to `m2`, and every later one to whichever module the
*preceding* goal left the node in. Without a same-named predicate to land on it
degrades quietly to lost solutions instead — `(m1:p(X), m2:q(Y))` returned two
pairs rather than six — which is how it stayed invisible: a conjunction of two
qualified goals in *the same* module works, and so does a qualified goal
followed by an unqualified one, which covers most real code.

**Fixed** by giving the node its own cons cell on the same tail rather than
writing through the shared one. `Node.setGoal` is replaced by
`Node.replaceGoal`:

```java
final void replaceGoal(final PrologObject goal)
{
    m_callList = new ConsCell(goal, m_callList.getTail());
}
```

`getGoal()` still sees the normalised form — `WAM.run` unifies against it — and
the parent's list is left alone. All four normalisation sites (`Atom`,
`Variable`, `Functor`'s `:` branch, `List`) now use it, and `setGoal` is gone so
the pattern cannot come back.

Cost: one small `ConsCell` per goal, on a path that already allocates a
`Hashtable` per node. Two full benchmark runs disagreed on the sign of the
difference (0.95x and 1.08x total), so it is below this machine's noise floor.

Regression cases: `cases_metacall.pl` (14 cases) and the qualifier-survival
block in `cases_modules.pl`. They fail 10 cases against the pre-fix build and
pass on the fixed one; the other 431 cases in the suite are unchanged across
both, as are 45 corpus goals and 21 parser corpus terms checked side by side.

### What this says about the rest

The defect is not really about modules or metacalls. It is that node state and
caller state share a mutable structure, and nothing in the type system says
which of the two a given write belongs to. §10 describes the same engine copying
clauses eagerly on every resolution step, which is the expensive half of the
same design decision; this is the cheap half, and it is the one that was wrong.
Any future change that touches `m_callList` should be read with this in mind.

---

## 17. ~~High — `write/1` and `writeq/1` never bracket by operator priority~~

**Fixed.** Found while reading a failure report from the conformance runner,
which uses `writeq/1` to echo the goal: the echo said `findall/4` for a term
that was `findall/3`. The parse was fine; the printer was lying.

ISO 7.10.5 writes every operand at a bounded priority — an argument of a
compound term and an element of a list at 999, an operand of an operator at
that operator's priority adjusted for associativity — and brackets any subterm
whose principal functor is an operator above that bound. `PrettyPrinter` does
none of this. It has no notion of a priority bound at all: `printParams` calls
`print` on each argument, and `printOperator` calls `print` on each operand.

The result is that `writeq/1` is not re-readable, which is the one property it
exists to have, and that `write/1` maps distinct terms onto identical text:

```prolog
?- X = f(a, (b,c), d), writeq(X).      % X is f/3
f(a,b,c,d)                             % reads back as f/4

?- writeq(f(a, (b;c), d)).             % f/3
f(a,b ; c,d)                           % reads back as f/2

?- write(*(a, +(b,c))).
a * b + c
?- write(+(*(a,b), c)).
a * b + c                              % two different terms, same text
```

`write_canonical/1` is correct — it emits functional notation and sidesteps the
question — which is why the conformance suite is unaffected and why CLAUDE.md
already tells you to debug the parser with `write_canonical/1` rather than
`write/1`. That note is a workaround for this defect; this is the defect.

What it costs in practice: `listing/1` output for any clause whose body contains
a nested conjunction (`findall(X, (a,b), L)` — very common) cannot be consulted
back. Error and trace messages misrepresent the terms they quote. Any embedder
round-tripping terms through text loses structure silently.

### The fix

The standard algorithm: thread a maximum priority through `print`, bracket when
the subterm's principal operator exceeds it, and adjust the bound per operand
position — `yfx` gives its left operand P and its right P-1, `xfy` the reverse,
`xfx` gives both P-1, `fy` gives P and `fx` P-1. Arguments and list elements are
written at 999.

Three cases needed handling beyond the plain algorithm:

- **Conjunctions are bare `ConsCell`s**, not `','/2` `Functor`s, so the bracket
  decision for them lives in `print`'s dispatch rather than in `printOperator`.
  This is why `[(a,b),c]` already printed correctly — `printCons` special-cased
  it for list elements — while `f((a,b),c)` did not.
- **An atom that is an operator** has the operator's priority when it appears as
  an operand (7.10.5.2), so `EOS = not` has to be written `EOS = (not)`. Without
  it the text reads back with `not` taken as a prefix operator. `xio.pl` already
  writes that clause with the brackets by hand, which is the author having hit
  this from the other side years ago.
- **`'{}'(T)` is written `{T}`.** The existing special case tested
  `getFriendlyName().equals("{")` and so never fired, and `{a,b}` came out as
  `{}(a,b)` — reading back as `{}/2`.

### What changed, measured

Over every term in the Prolog library sources and the conformance suite — 1203
terms — each was written with `writeq/1`, read back, and compared through
`write_canonical/1`:

| | terms written | read back | not faithful |
|---|---|---|---|
| before | 1203 | **360** | 877 lines differ |
| after | 1203 | 1203 | **0** |

"Read back 360" is not a rounding error: the old output stopped being parseable
a third of the way through the file. Of 3637 printed lines, 1944 changed. Every
one inspected was a correction, and some were of clauses whose printed form had
been actively misleading:

```prolog
% before                            % after
forall(A,B) :- \+ A,\+ B.            forall(A,B) :- \+ (A,\+ B).
open(A,B,C,D) :-                    open(A,B,C,D) :-
  var(A) ; var(B) ;                   (var(A) ; var(B) ;
  \+ ground(D),error(...).             \+ ground(D)),error(...).
```

Regression net: `WriteTermTest`, 12 tests — the explicit rules plus a
round-trip over 40 mixed terms. Nine of the twelve fail against the pre-fix
build.

Cost: none measurable. 15000 `writeq/1` calls over five mixed terms took
1305-1363 ms before and 1262-1290 ms after, three runs each; both figures are
mostly the unbuffered stream underneath, and the printer is not on the
resolution path at all — nothing in the engine calls it except `write`-family
built-ins, `listing/1` and error formatting.

### Still not right, and separate from this

`writeq/1` quotes `\+` as `'\\+'`. Per 6.4.2 a token made only of graphic
characters needs no quotes. It round-trips — `'\\+'` *is* the atom `\+` — so it
is cosmetic, but it is wrong, and it belongs to the quoting rules in
`printAtomString` rather than to the priority algorithm.

---

## 18. Low — a syntax error in the parser can surface as `ClassCastException`

**Open.** Seven places in `PrologParser.translateTerm` cast the top of the term
stack to `Operator` without checking:

```java
Operator lastOp = (Operator)termStack.peek();     // lines 717 and 760
Operator lastOp = (Operator) termStack.pop();     // 506, 560, 596, 642, 1179
```

On malformed input the stack top can be a term instead. A missing closing
parenthesis followed by a line beginning with a prefix operator is enough:

```prolog
:- r((catch(a, b, c)).
:- write(x).
```

```
java.lang.ClassCastException: class com.ugos.jiprolog.engine.BuiltInPredicate
  cannot be cast to class com.ugos.jiprolog.engine.Operator
	at com.ugos.jiprolog.engine.PrologParser.translateTerm(PrologParser.java:760)
```

Most malformed input does produce a proper `JIPSyntaxErrorException`
(`unexpected_eof`, `operator_expected`, `not_assoc_operator`), so this is a gap
rather than the rule. But an embedder catching `JIPSyntaxErrorException` around
`consultFile` will not catch this one, and the message names a Java class rather
than a line number.

Left open because the honest fix is a guard at each of the seven sites, and
`PrologParser` is the file §13 flags as least maintainable — worth doing as part
of a deliberate pass over it rather than by wrapping the boundary in a
`catch(ClassCastException)`, which would also swallow genuine internal bugs.

---

## 19. ~~High — the parser does not enforce operand priority either~~

**Fixed**, and the mirror image of §17: the printer was writing without a
priority bound, and the reader read without one too. Found by the round-trip
harness built for §17 — the one term out of 1203 that would not come back was
not a printer failure.

```prolog
?- X = (a -> b = not ; c), write_canonical(X).
->(a,=(b,;(not,c)))          % 4.1.7.1
;(->(a,=(b,not)),c)          % correct
```

`=` is 700 `xfx`, so its right operand may be at most 699. `;` is 1100. The
parser attached it anyway. The same happens to a prefix operator's operand:

```prolog
?- X = [\+ not, y, z], write_canonical(X).
'.'('\\+'(','(not,','(y,z))),[])   % \+ swallowed the rest of the list
```

Both need the operand to be an atom that is itself an operator (`not` here) —
`b = n ; c` parses correctly — which is what has kept it rare enough to live
with. It ships in 4.1.7.1: reproduced against an untouched build of `master`,
and unchanged by anything on this branch.

The author has met it before. `xio.pl` writes

```prolog
	(	'$stream_property'(get, Handle, eof_action(reset)) ->
		EOS = (not)
```

with brackets that ISO does not require around an operand of `=`, and the same
workaround appears in every clause of that predicate.

### Where it actually goes wrong

Not where it looks. The shunting-yard does compare precedences — but only on the
path where the previous token left a *term* behind. An atom that is also a prefix
operator does not: it is pushed on the stack as an `Operator`, betting that an
operand will follow. When an infix operator arrives instead, one branch takes it:

```java
else // if(curOp.getInfix() != null)
{
    termStack.push(curOp.getInfix());     // on top of a dangling prefix operator
    lastObj = null;                       // ...and no precedence compared, ever
}
```

`resolveStack` then unwinds right to left, which groups by position rather than
by priority. That is the whole defect: `x = not ; c` builds `;` before `=`
because nothing ever asked which binds tighter.

The bet is simply never revisited. `curOp` cannot start a term, so the operand
the prefix operator was waiting for does not exist and never will — the atom was
an atom. Converting it and putting it back in `lastObj` sends the next turn of
the loop down the ordinary term path, the one that does compare precedences.
That is the fix, and it is four lines.

### Enforcement, and the three things it needed first

Correct grouping is not the whole rule. `a ; dynamic + b` still builds
`dynamic(+(b))` at 1150 under `;/2`, whose right operand tops out at 1100 — a
term with no correct reading, which every other Prolog rejects. So
`resolveOperator` checks each operand against the bound its position allows and
raises `syntax_error(operator_priority_clash(Op))`.

Getting there took three rounds, each of which was a measurement rather than an
argument. The method is the point: **the check was run as a warning over the
whole tree before it was allowed to throw**, and each round of false positives
said something true about the parser.

**Round one — 418 flags, every one a false positive.** ISO 6.3.4.1 gives a
*bracketed* term priority 0 wherever it stands, so `X = (a ; b)` is legal where
`X = a ; b` is not, and the parser did not remember the difference. `xio.pl`'s
own `EOS = (not)` was among the flags: the check would have rejected the code it
was meant to protect. Fixed by recording bracketed subterms.

**Round two — the whole kernel.** ISO 6.3.3 gives a compound in *functional
notation* priority 0 too, and the priority recomputed from a functor cannot tell
`;(a, b)` from `a ; b`. The kernel is written that way almost throughout —
`'$system': @>(X, Y)`, `'$system': ->(X,Y)` — and `@>` at 700 under `:` at 600
flagged every one. Fixed the same way: functional-notation compounds are
recorded as priority 0 alongside bracketed ones. Both are the same ISO idea, so
one `IdentityHashMap` holds both. Identity and not equality, because atoms are
interned and would alias, while compounds are built fresh.

**Round three — four real violations, all in the kernel.** What was left:

```prolog
'$system': \+ G :- call(G), !, fail.
'$system': \+ G.
'$system': not G :- call(G), !, fail.
'$system': not G.
```

`:` is `600 xfy`, so its right operand may reach 600; `\+ G` written with the
operator is 900. These four lines are the only place in the tree that does it —
everywhere else the kernel already uses the functional notation that makes the
question moot. They now read `'$system': \+(G) :- ...`, which is the same term
(verified: identical canonical form) written the way their neighbours are.

Raising `:` instead was considered and rejected. It would have to stay under
1200 so that `M:H :- B` groups as `(M:H) :- B`, reach 900 to accept `\+ G`, and
stay at or under 999 to be usable unbracketed in an argument list — a 900..999
window. At 900 it breaks `Goal = Module:Head`, since `=/2` allows 699 on the
right. That idiom is common and works today precisely because `:` is 600.

One deliberate leniency remains: an atom that is an operator counts as priority
0 rather than the operator's own, so `X = not` is accepted. Strict ISO rejects
it; no Prolog in circulation does.

### Measured

- The 1203 terms of the library sources and the conformance suite parse to
  **byte-identical** canonical forms before and after. The fix changes nothing
  about code that was already being read correctly.
- A sweep of ~2900 `A op1 <operator-atom> op2 B` combinations produces no
  priority violation that survives the fix, and no new syntax error on anything
  that parsed before.
- Parse cost is unchanged: 20 reparses of every source in the tree, about 24000
  terms, take 1882-1944 ms before and 1834-1942 ms after.
- `ParserTest` gains `OperatorAsOperand` and `PriorityClash`, seven tests. Five
  fail against the pre-fix build; the two that pass on both are the guards
  against over-correcting — that `- - a`, `- a * b` and `x is - 1 + 2` still
  read as operators, and that `a ; (dynamic + b)` stays legal with brackets.
- The kernel change touches `\+/1` and `not/1`, so negation was checked
  separately: ten goals over `\+`, `not`, double negation and negation inside
  `findall` behave as before, and the conformance suite's negation cases are
  unchanged.

### What is still not enforced

The bound is checked when an operator is reduced, which covers operands of
operators. It is not threaded through `translateTerm`, so the 999 limit on an
*argument* of a compound term and on a *list element* is not enforced:
`f(a :- b, c)` is still accepted where ISO wants brackets. Smaller than what was
closed here — it mis-reads nothing, it only accepts too much — and closing it
means changing `translateTerm`'s signature through every recursive call.

---

## 20. Medium — a failed library bootstrap reported `BUILD SUCCESS`

**Fixed.** Found the worst way: by pushing a commit that passed `mvn verify`
locally and failed all four CI jobs.

`JIProlog.main` caught `IOException`, `JIPSyntaxErrorException` and
`JIPRuntimeException`, printed the message, and returned — exit code 0. The
`.jip` bootstrap runs through that entry point, so when it died loading the
kernel, `exec-maven-plugin` saw success and the build carried on.

Locally that is invisible, because `target/classes` still holds the `.jip` files
from the previous build and the tests load those. The suite goes green against
artifacts that the current sources can no longer produce. CI clones fresh, has no
stale artifacts, and every test fails at once with `Unable to load Kernel`.

```java
catch(JIPRuntimeException ex)
{
    showMessage(ex.getMessage());
    System.exit(1);          // was: fall through, exit 0
}
```

Verified by breaking `jipkernel.txt` on purpose: the build now stops at
`process-classes` with a `MojoExecutionException` instead of reporting success
and running the tests against a stale kernel.

Worth keeping in mind beyond the fix: **`mvn verify` on a dirty `target/` is not
a check that the build works.** Anything that touches the parser, the kernel or
the library sources wants `mvn clean verify` before it is believed.

---

## 21. The independent suite: 420 cases, 6 real deviations, and a flat score

The caveat attached to §14's conformance suite — that cases written from the
standard by the same hand that fixed the engine are a weak signal — has now been
tested. The INRIA suite (Deransart, Ed-Dbali and Cervoni's specification;
J.P.E. Hodgson's driver; version 0.9, 1999) runs against this engine.

Not vendored: it is third-party material with no stated licence and this tree is
AGPL. `tools/run-inriasuite.sh` fetches it into `target/` on demand. It is not
part of `mvn verify` — it needs the network, it is not green, and a build should
not depend on a 1999 tarball staying reachable.

**420 cases, 12 flagged.** Of those, six are real and six are the suite or the
environment.

### The result that matters

Run like for like — both in `-debug`, since `master` has no compiled `.jip`
libraries and cannot run any other way — 4.1.7.1 as shipped and this branch
flag **exactly the same 14 goals, file for file**. Everything in §1 through §20
moved this number by zero.

That is the point of running it. The 441-case suite in `test/resources/iso` is
green; it had nothing to say about any of the six deviations below, because they
are in areas none of this work touched. A suite and an implementation checked
against each other by the same hand agree with each other, and that is all they
demonstrate.

(Comparing the two runs by the printed goal text is misleading, incidentally:
`call(1 ; true)` on `master` and `call((1 ; true))` here are the same case. The
difference is §17 — the suite's own output is now correctly bracketed.)

### The six real ones

**~~`number_chars/2` and `number_codes/2` do not parse when the number is
bound.~~ Fixed — see §22.** The clearest of the six, and the best characterised.
ISO 8.16.4.1 says the list is parsed and the resulting number unified. This
implementation instead rendered the number to its canonical text and compared:

```prolog
?- number_chars(X, ['3','.','3','E','+','0']).    X = 3.3.        % parsing works
?- number_chars(3.3, ['3','.','3','E','+','0']).  false.          % should succeed
?- number_chars(3.3, ['3','.','3']).              true.           % canonical only
?- number_chars(33, [' ','3','3']).               false.          % leading layout
?- number_chars(33, ['+','3','3']).               false.          % signed
?- number_chars(31, ['0','x','1','f']).           false.          % other notations
```

Every non-canonical but valid representation fails. Both directions work in
isolation; it is the bound case that takes the wrong one.

**`call/1` names the wrong culprit.** `call((1 ; true))` raises
`type_error(callable, 1)`; ISO 7.8.3.3 wants the whole goal,
`type_error(callable, (1 ; true))`. Same in `setof(A, A^(true ; 4), B)`, which
gives `type_error(callable, 4)`. Two of the twelve. The error is raised, and it
is the right class — only the culprit term is the offending subterm rather than
the goal it came from.

**`bagof/3` and `setof/3` with `^/2` nested inside a disjunction.** The suite
expects `bagof(A, (B^(A=1 ; B=1) ; A=3), C)` to give `C = [3]`; this engine
gives `C = [1,3]` and then a second solution. Flagged here as *unresolved rather
than confirmed*: `^` at the outer level behaves correctly
(`bagof(A, B^(A=1 ; B=1), C)` gives `C = [1,_]`, which is right), and the
nested-`^` reading is one of the cases the 1999 suite is known to get wrong.
It needs checking against the corrigenda before anyone changes code for it.

### The six that are not

- **`abolish` and `functor`** assume `max_arity` is an integer, then compute
  `MaxArity + 1`. This engine reports `unbounded`, which ISO 7.11.2.3 permits,
  so `is/2` raises `type_error(evaluable, unbounded/0)`. The engine is right and
  the case is not portable.
- **`sub_atom(ab, A, B, C, D)`** — this engine returns exactly the six correct
  solutions, verified independently: `(0,0,2,'')`, `(0,1,1,a)`, `(0,2,0,ab)`,
  `(1,0,1,'')`, `(1,1,0,b)`, `(2,0,0,'')`. The suite's expected list omits the
  `C` bindings and starts at `A <-- 1`. Its companion case,
  `sub_atom(charity, A, 3, B, C)`, reports `Solutions Missing: []` — nothing is
  missing, the expectation simply lists fewer bindings than the engine reports.
  The driver's own header admits "matching of solutions is not yet perfected".
- **`char_code(A, 163)`** is a text-encoding artifact of a Latin-1 file from
  1999; the answer is correct.
- **`catch-and-throw`** expects an uncaught ball to surface as `system_error`.
  This engine propagates the ball. Driver-dependent rather than clearly wrong.
- **`current_prolog_flag(debug, off)`**, twice, appears only in `-debug` runs —
  the CLI flag sets the Prolog flag. Not a defect, and the reason the two runs
  have to be compared in the same mode.

### What to do with this

Nothing here was urgent — 414 of 420 with four built-ins deviating is a good
showing for an engine of this size, and the six are narrow.
`number_chars/number_codes` is now fixed (§22), taking the suite to **10
flagged**; the rest stand.

The more useful conclusion is about method. Run this before believing any
conformance claim made from `test/resources/iso` alone.

---

## 22. ~~Medium — `number_chars/2` and `number_codes/2` never parse a given list~~

**Fixed**, and found by §21's independent suite rather than by this project's own.

ISO 8.16.4.1 is directional: when the list is there, it is *parsed* and the
number unified with the result. Rendering the number to text is the other
direction, and it only applies when there is no list yet. `NumberChars2` and
`NumberCodes2` had it backwards — with the number bound they always rendered the
canonical text and compared strings — so the same number succeeded or failed
depending on how it had been written:

```prolog
?- number_chars(X, ['3','.','3','E','+','0']).    X = 3.3.     % parsing worked
?- number_chars(3.3, ['3','.','3','E','+','0']).  false.       % same number
?- number_chars(3.3, ['3','.','3']).              true.        % canonical only
?- number_chars(33,  [' ','3','3']).              false.
?- number_chars(15,  ['0','x','f']).              false.
```

### Flipping it exposed a second defect

The parse branch used `Double.parseDouble`, which implements *Java literal*
syntax. That had been unreachable with the number bound, and nobody writing the
list by hand writes `3d`, so it went unnoticed:

| list | was | ISO 6.4.4 |
|---|---|---|
| `3d` | 3 | not a number (`d` is a Java suffix) |
| `3.3f` | 3.3 | not a number |
| `Infinity` | 2147483647 | not a number |
| `NaN` | 0 | not a number |
| `.3`, `3.` | 0.3, 3.0 | a float needs digits on both sides |

Fixing only the direction would have turned these from *failing* into
*succeeding*, which is worse. So the decimal branch is now matched against the
ISO number-token grammar before being parsed. The radix forms — `0x`, `0o`,
`0b`, `0'c` — were already correct and are unchanged, except that they now
reject bad digits (`0xg`) and use `long` rather than `int`.

Delegating the whole job to `JIPTermParser.parseTerm` was tried first and
rejected: it does not recognise the radix notations, reading `"0xf"` as `0` and
`"0o17"` as `17`. Caught by re-running the same probes rather than by reading.

### One deliberate behaviour change

`number_chars(N, ['1','e','5'])` now raises `syntax_error(not_a_number)` where
it gave `N = 100000`, and `atom_number('1e5', N)` fails where it succeeded. An
exponent without a fractional part is not an ISO number token, and yielding an
*integer* from one is doubly wrong.

This does leave a divergence: **the reader still accepts `1e5`, as integer
100000**. `.3` and `3.` it already rejects, so the tightening agrees with it
everywhere else. Baking the reader's leniency into a second place would have
made it harder to fix; the reader is where it belongs, and it is not fixed here.

### Measured

- The INRIA suite goes from 12 flagged goals to **10**: `number_chars` and
  `number_codes` leave the list entirely.
- `test/resources/iso` grows 441 → 465 cases. **Fourteen of the new ones fail
  against the previous commit**, in both directions — seven that should now
  succeed and seven that should now be syntax errors. The canonical-form cases
  pass on both builds and are the guard against over-correcting.
- 25 runtime goals and the 1203-term parse corpus are identical before and
  after. `atom_number/2`, which goes through `number_codes/2`, is unchanged on
  every input except `1e5`.
- A latent truncation went with it: the canonical text came from
  `Integer.toString((int) value)`, so `number_chars(3000000000, L)` gave a
  negative number's digits. It is `long` now, per §9's rule.

---

## What is good, and worth preserving

- **The `JIP*` API boundary is genuinely well done.** Package-private internals,
  a clean public wrapper layer, no leakage of `PrologObject` into public
  signatures. That is why the engine is embeddable at all, and it should be
  defended in review.
- **`JIPClausesDatabase` as a pluggable storage interface** — with JDBC, text
  and indexed in-memory implementations — is a nice piece of design and rare in
  a Prolog of this size.
- **Correct behaviour under test** for cut (including the `$!`/`$!!` soft- and
  strong-cut extensions), if-then-else, `catch/3` + `throw/1` and their
  interaction with the cut, `findall/bagof/setof`, `msort/keysort/sort`,
  `copy_term/2`, `numbervars/3`, `unify_with_occurs_check/2`, and the logical
  update view. The ISO error terms for the common cases (`type_error`,
  `instantiation_error`, `evaluable`, `callable`,
  `permission_error(modify, static_procedure, _)`) are all correct.
  Module-qualified goals belonged on this list until §16; they are correct now,
  but they were not before, and the difference was only ever visible on the
  second solution.
- **First-argument indexing** with separate tables per key type.
- **Heap-allocated resolution nodes** — Prolog recursion depth is bounded by
  heap, not by the Java stack. Many small Java Prologs get this wrong.
- **Zero runtime dependencies**, which is what makes "pure Java 100%" a real
  claim rather than a slogan.

---

*Review conducted on branch `dev`. Findings §1, §4, §9, §10, §12, §16, §17, §18
and §19 were reproduced against a local build; the transcripts are inline above.
§16 and §19 were also reproduced against an untouched build of `master`, to
establish that they ship in 4.1.7.1 rather than having been introduced here.*
