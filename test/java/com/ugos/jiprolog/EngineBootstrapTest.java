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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ugos.jiprolog.engine.JIPDebugger;
import com.ugos.jiprolog.engine.JIPEngine;

/**
 * Checks that the engine boots from the compiled (.jip) kernel and that the
 * library set listed in x.pl is actually loaded.
 *
 * <p>This is the test that fails first if the Maven bootstrap step
 * (exec-maven-plugin, see pom.xml) did not run or produced a broken kernel:
 * without jipkernel.jip, GlobalDB.loadKernel throws
 * "Unable to load Kernel: java.lang.NullPointerException".
 */
public class EngineBootstrapTest extends PrologTestBase
{
    @Test
    @DisplayName("engine boots from the compiled kernel, without debug mode")
    public void bootsFromCompiledKernel()
    {
        assertTrue(!JIPDebugger.debug,
                "the suite must exercise the release kernel, not the .txt/.pl sources");
        assertNotNull(engine, "engine failed to construct");
        assertTrue(succeeds("true"), "engine constructed but cannot prove true/0");
    }

    @Test
    @DisplayName("the libraries listed in x.pl are loaded")
    public void librariesAreLoaded()
    {
        // one representative predicate from each library x.pl pulls in
        assertTrue(succeeds("append([a], [b], [a,b])"), "list.pl not loaded");
        assertTrue(succeeds("current_prolog_flag(bounded, _)"), "flags.pl not loaded");
        assertTrue(succeeds("forall(member(X, [1,2]), integer(X))"), "sys.pl not loaded");
        assertTrue(succeeds("setof(X, member(X, [b,a]), [a,b])"), "setof.pl not loaded");
        assertTrue(succeeds("atom_concat(a, b, ab)"), "xterm.pl not loaded");
    }

    @Test
    @DisplayName("reported version matches the POM")
    public void versionMatchesPom()
    {
        assertEquals("4.1.7.1", JIPEngine.getVersion(),
                "JIPEngine.major/minor/build/revision drifted from the POM version");
    }

    @Test
    @DisplayName("consulted programs are queryable")
    public void consultAndQuery()
    {
        consult("colour(red). colour(green). colour(blue).");
        assertEquals(3, solveAll("colour(X)").size());
        assertEquals("green", valueOf("colour(X), X \\== red, !", "X"));
    }
}
