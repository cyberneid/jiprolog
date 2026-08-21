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

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import com.ugos.jiprolog.engine.JIPEngine;
import com.ugos.jiprolog.engine.JIPQuery;
import com.ugos.jiprolog.engine.JIPTerm;
import com.ugos.jiprolog.engine.JIPVariable;

/**
 * Support for the Prolog-level tests: boots a {@link JIPEngine} and runs goals
 * against it.
 *
 * <p>The engine is created per test, not per class, because a test that asserts
 * into the database would otherwise be visible to every test after it. Engine
 * construction consults the library set in x.pl and costs a few tens of
 * milliseconds, which is affordable at this suite size.
 *
 * <p>Tests run against the <em>release</em> kernel - the .jip files generated
 * into target/classes by the Maven bootstrap step. That is deliberate: it is
 * what ships, and it means the suite also proves the bootstrap produced a
 * loadable kernel. Nothing here sets JIPDebugger.debug.
 */
public abstract class PrologTestBase
{
    protected JIPEngine engine;

    @BeforeEach
    public void createEngine()
    {
        engine = new JIPEngine();
    }

    @AfterEach
    public void releaseEngine()
    {
        if (engine != null)
        {
            engine.closeAllQueries();
            engine = null;
        }
    }

    /** Loads Prolog text into the engine, as if it had been consulted from a file. */
    protected void consult(String prologSource)
    {
        engine.consultStream(new StringReader(prologSource), "test");
    }

    /** True if the goal has at least one solution. Errors propagate. */
    protected boolean succeeds(String goal)
    {
        return solveFirst(goal) != null;
    }

    /**
     * Runs the goal and returns the bindings of its first solution as a map of
     * variable name to its value rendered with quoting, or {@code null} if the
     * goal has no solution.
     */
    protected Map<String, String> solveFirst(String goal)
    {
        List<Map<String, String>> all = solve(goal, 1);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Bindings of every solution of the goal, in order. */
    protected List<Map<String, String>> solveAll(String goal)
    {
        return solve(goal, Integer.MAX_VALUE);
    }

    /**
     * Convenience for the common single-variable case: runs {@code goal} and
     * returns the value bound to {@code variable}, or {@code null} if the goal
     * failed.
     */
    protected String valueOf(String goal, String variable)
    {
        Map<String, String> bindings = solveFirst(goal);
        return bindings == null ? null : bindings.get(variable);
    }

    /**
     * Renders {@code term} with write_canonical/1, which ignores the operator
     * table and so shows the real structure. Use this and not write/1 when
     * asserting on how something parsed - the pretty printer can make a
     * malformed term look reasonable.
     */
    protected String canonical(String term)
    {
        return captureOutput("write_canonical(" + term + ")");
    }

    /** Runs the goal with user_output redirected, and returns what it wrote. */
    protected String captureOutput(String goal)
    {
        OutputStream previous = engine.getUserOutputStream();
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try
        {
            engine.setUserOutputStream(captured);
            solveFirst(goal);
        }
        finally
        {
            engine.setUserOutputStream(previous);
        }

        try
        {
            return captured.toString("ISO-8859-1").trim();
        }
        catch (UnsupportedEncodingException cannotHappen)
        {
            throw new IllegalStateException(cannotHappen);
        }
    }

    private List<Map<String, String>> solve(String goal, int limit)
    {
        List<Map<String, String>> solutions = new ArrayList<Map<String, String>>();
        JIPQuery query = engine.openSynchronousQuery(engine.getTermParser().parseTerm(goal));
        try
        {
            JIPTerm solution;
            while (solutions.size() < limit && (solution = query.nextSolution()) != null)
            {
                solutions.add(bindingsOf(solution));
                if (!query.hasMoreChoicePoints())
                {
                    break;
                }
            }
        }
        finally
        {
            query.close();
        }
        return solutions;
    }

    private Map<String, String> bindingsOf(JIPTerm solution)
    {
        Map<String, String> bindings = new LinkedHashMap<String, String>();
        for (JIPVariable variable : solution.getVariables())
        {
            JIPTerm value = variable.getValue();
            bindings.put(variable.getName(), value == null ? null : value.toStringq(engine));
        }
        return bindings;
    }
}
