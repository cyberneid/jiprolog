/*
 * Copyright (C) 1999-2018 Ugo Chirico
 *
 * This is free software; you can redistribute it and/or
 * modify it under the terms of the Affero GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Affero GNU General Public License for more details.
 *
 * You should have received a copy of the Affero GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.ugos.jiprolog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.ugos.jiprolog.engine.JIPEngine;
import com.ugos.jiprolog.engine.JIPQuery;
import com.ugos.jiprolog.engine.JIPTerm;
import com.ugos.jiprolog.engine.JIPVariable;

/**
 * Several engines, one per thread, working at the same time.
 *
 * <p>This is the harness CODE_REVIEW.md sections 2 and 3 needed. The state that
 * used to be shared between engines is what these exercise:
 *
 * <ul>
 *   <li>the four static {@code StringBuilderEx} buffers in {@code GlobalDB} that
 *       composed every database lookup key, with no synchronization at all;
 *   <li>{@code Variable}'s static name buffers and its {@code counter++};
 *   <li>the static atom table's non-atomic {@code containsKey} then {@code put};
 *   <li>{@code Clause}'s static DCG translation query, mutated in place;
 *   <li>the lazy initialization of {@code JIPEngine.s_globalDB}.
 * </ul>
 *
 * <p>A race is not guaranteed to show on any single run, so these are worth
 * little as proof that the code is correct and a great deal as a tripwire: they
 * do fail, and did fail, when that state is shared. Keep the iteration counts
 * high enough to be worth running and low enough not to dominate the build.
 */
public class ConcurrencyTest
{
    private static final int THREADS = 4;
    private static final int ITERATIONS = 150;

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    @DisplayName("engines built concurrently each get a working database")
    public void enginesCanBeConstructedConcurrently() throws Exception
    {
        // JIPEngine.s_globalDB is loaded once and cloned per engine; the check
        // and the assignment were not synchronized, so two threads could each
        // load a kernel and the second overwrite the first's snapshot.
        List<String> failures = runInParallel(new Work()
        {
            public void run(JIPEngine engine, int thread, int iteration, List<String> failures)
            {
                runGoal(engine, "append([a], [b], [a,b])", failures);
            }
        });

        assertTrue(failures.isEmpty(), () -> "concurrent engine construction failed: " + failures);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    @DisplayName("predicate lookup is not corrupted by a concurrent lookup")
    public void databaseLookupIsThreadSafe() throws Exception
    {
        List<String> failures = runInParallel(new Work()
        {
            public void run(JIPEngine engine, int thread, int iteration, List<String> failures)
            {
                // each thread owns a differently named predicate, so a lookup
                // that returns anything at all for the wrong key is visible
                String name = "pred_" + thread + "_" + iteration;
                runGoal(engine, "assertz(" + name + "(" + thread + "))", failures);
                expectBinding(engine, name + "(X)", "X", String.valueOf(thread), failures);
                runGoal(engine, "retract(" + name + "(" + thread + "))", failures);
            }
        });

        assertTrue(failures.isEmpty(), () -> "concurrent database access failed: " + failures);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    @DisplayName("grammar rules translate correctly when consulted concurrently")
    public void dcgTranslationIsThreadSafe() throws Exception
    {
        List<String> failures = runInParallel(new Work()
        {
            public void run(JIPEngine engine, int thread, int iteration, List<String> failures)
            {
                // the translation query used to be a static term mutated in
                // place, so two threads translating at once traded arguments
                String nt = "nt_" + thread + "_" + iteration;
                engine.consultStream(new StringReader(
                        nt + " --> [tok_" + thread + "].\n"), "t" + thread);
                runGoal(engine, "phrase(" + nt + ", [tok_" + thread + "])", failures);
            }
        });

        assertTrue(failures.isEmpty(), () -> "concurrent DCG translation failed: " + failures);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    @DisplayName("atoms interned concurrently stay identical")
    public void atomInterningIsThreadSafe() throws Exception
    {
        // Atom.createAtom did containsKey then put, so the same name could be
        // interned twice and produce two Atom instances - which ==/2 would then
        // report as different terms.
        List<String> failures = runInParallel(new Work()
        {
            public void run(JIPEngine engine, int thread, int iteration, List<String> failures)
            {
                String atom = "shared_atom_" + iteration;
                runGoal(engine, "atom_codes(A, \"" + atom + "\"), A == " + atom, failures);
            }
        });

        assertTrue(failures.isEmpty(), () -> "concurrent atom interning failed: " + failures);
    }

    // ------------------------------------------------------------------ plumbing

    private interface Work
    {
        void run(JIPEngine engine, int thread, int iteration, List<String> failures);
    }

    private static List<String> runInParallel(final Work work) throws Exception
    {
        final List<String> failures = Collections.synchronizedList(new ArrayList<String>());
        final CountDownLatch start = new CountDownLatch(1);
        final List<Thread> threads = new ArrayList<Thread>();

        for (int t = 0; t < THREADS; t++)
        {
            final int thread = t;
            Thread worker = new Thread(new Runnable()
            {
                public void run()
                {
                    try
                    {
                        start.await();
                        // one engine per thread: these tests are about state
                        // shared *between* engines, not about one engine being
                        // driven from several threads
                        JIPEngine engine = new JIPEngine();
                        for (int i = 0; i < ITERATIONS; i++)
                        {
                            work.run(engine, thread, i, failures);
                        }
                    }
                    catch (Throwable th)
                    {
                        failures.add("thread " + thread + ": " + th);
                    }
                }
            }, "jiprolog-test-" + t);
            worker.setDaemon(true);
            threads.add(worker);
            worker.start();
        }

        start.countDown();
        for (Thread worker : threads)
        {
            worker.join(TimeUnit.MINUTES.toMillis(2));
        }
        return failures;
    }

    private static void runGoal(JIPEngine engine, String goal, List<String> failures)
    {
        try
        {
            JIPQuery query = engine.openSynchronousQuery(engine.getTermParser().parseTerm(goal));
            try
            {
                if (query.nextSolution() == null)
                {
                    failures.add("goal failed: " + goal);
                }
            }
            finally
            {
                query.close();
            }
        }
        catch (Throwable th)
        {
            failures.add(goal + " raised " + th);
        }
    }

    private static void expectBinding(JIPEngine engine, String goal, String var,
                                      String expected, List<String> failures)
    {
        try
        {
            JIPQuery query = engine.openSynchronousQuery(engine.getTermParser().parseTerm(goal));
            try
            {
                JIPTerm solution = query.nextSolution();
                if (solution == null)
                {
                    failures.add("goal failed: " + goal);
                    return;
                }
                for (JIPVariable v : solution.getVariables())
                {
                    if (v.getName().equals(var))
                    {
                        String actual = v.getValue() == null ? "<unbound>" : v.getValue().toString(engine);
                        if (!expected.equals(actual))
                        {
                            failures.add(goal + ": " + var + " = " + actual + ", expected " + expected);
                        }
                        return;
                    }
                }
                failures.add(goal + ": no binding for " + var);
            }
            finally
            {
                query.close();
            }
        }
        catch (Throwable th)
        {
            failures.add(goal + " raised " + th);
        }
    }

    /** Guards against the plumbing silently doing nothing. */
    @Test
    @DisplayName("the harness really runs work on every thread")
    public void harnessSanityCheck() throws Exception
    {
        final int[] counter = new int[1];
        List<String> failures = runInParallel(new Work()
        {
            public void run(JIPEngine engine, int thread, int iteration, List<String> failures)
            {
                synchronized (counter) { counter[0]++; }
            }
        });
        assertTrue(failures.isEmpty(), () -> failures.toString());
        assertEquals(THREADS * ITERATIONS, counter[0]);
    }
}
