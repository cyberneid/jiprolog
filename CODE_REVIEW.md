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

1. ~~**The parser silently corrupts terms containing a prefix operator followed
   by an infix operator.** `X is -7 + 1` evaluates to `8`.~~ **Fixed.** Two
   causes, neither where this review first looked: prefix `-` was declared
   `500 fx` instead of ISO's `200 fy`, and the negative-literal fold ignored
   layout.
2. **Shared mutable static state** makes multiple `JIPEngine` instances
   non-isolated and the library not thread-safe — including DCG translation,
   which runs against a statically pinned engine using static scratch terms.
3. **No tests, no working build.** `build.xml` cannot run from a clean clone and
   there is not a single automated test, so none of the above was caught.

Nothing here suggests the design is wrong. The resolution engine, the database
layer and the API boundary are sound. The findings are localized defects and
accumulated infrastructure debt.

**Status.** §14 (build and CI) is done: the project builds with Maven, produces
a working jar, and runs its tests on four JDK/OS combinations. §1, §4, §5, §9
and the two resolved bullets of §12 are fixed, each with tests — 59 tests, none
disabled. §2, §3 and the rest are open. Neither §2 nor §3 can be demonstrated
from Prolog alone, which is why they have no test yet: both need a
multi-engine or multi-threaded harness.

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
