# Benchmarks

Standard Prolog programs used to measure the engine, plus a runner.

```bash
mvn package
java -jar target/jiprolog-4.1.7.1.jar -c bench/benchmarks.pl -c bench/run.pl -g run_all
```

Each line of output is `name<TAB>milliseconds<TAB>repetitions`, so two builds
can be diffed directly. Repetition counts are calibrated to about a second per
entry, which keeps a full pass under ten seconds.

Two caveats worth knowing before drawing conclusions from a number here.

**Take the best of several runs.** Single runs vary by a factor of two or more
on a shared machine, and the JIT needs the warm-up that `time_it/3` does before
timing. The minimum across three runs is stable enough to compare builds.

**Order matters, and that is itself a finding.** `moduleStack` in `WAM` is not
fully unwound (see `CODE_REVIEW.md` §10), so a benchmark placed late in a
deterministic conjunction runs slower than the same benchmark placed first —
`atom_churn` measured 118 ms first and 795 ms last. `run_all` drives its
iteration by failure, which unwinds between entries, so the numbers it reports
are the optimistic ones. Compare like with like.

`queens8` and `tak18` are defined in `benchmarks.pl` but left out of the
`bench/3` set: naive-permutation queens and `tak(18,12,6)` each take minutes at
this speed, which makes them useless for iterating and fine for a once-off
check.
