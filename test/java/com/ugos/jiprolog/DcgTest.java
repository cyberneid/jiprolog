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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ugos.jiprolog.engine.JIPEngine;

/**
 * Definite clause grammars, which had no coverage at all before.
 *
 * <p>Also covers CODE_REVIEW.md section 2: the translation of a -->/2 rule used
 * to run on a static JIPEngine pinned to the first one constructed in the JVM,
 * using static scratch terms mutated in place. The engine is now passed down
 * through Clause.getClause.
 */
public class DcgTest extends PrologTestBase
{
    private static final String GRAMMAR =
            "greeting --> [hello], subject.\n"
          + "subject --> [world].\n"
          + "subject --> [prolog].\n"
          + "digits([D|T]) --> digit(D), digits(T).\n"
          + "digits([D]) --> digit(D).\n"
          + "digit(D) --> [D], { integer(D) }.\n";

    @Test
    @DisplayName("a grammar rule is translated and can be run with phrase/2")
    public void grammarRulesWork()
    {
        consult(GRAMMAR);

        assertTrue(succeeds("phrase(greeting, [hello, world])"));
        assertTrue(succeeds("phrase(greeting, [hello, prolog])"));
        assertFalse(succeeds("phrase(greeting, [bye, world])"));
        assertEquals("[1,2,3]", valueOf("phrase(digits(L), [1,2,3])", "L"));
        assertEquals("[world,prolog]", valueOf("findall(N, phrase(subject, [N]), L)", "L"));
    }

    @Test
    @DisplayName("-->/2 translates to a clause of the right head arity")
    public void translationShape()
    {
        consult(GRAMMAR);

        // consulted predicates are static, so clause/2 is not allowed on them
        // (ISO permission_error(access, private_procedure, _)); call the
        // translated forms directly instead.

        // greeting//0 becomes greeting/2, threading the difference list
        assertTrue(succeeds("greeting([hello, world], [])"));
        // greeting consumes both tokens, so what is left is the tail after them
        assertEquals("[]", valueOf("greeting([hello, world], R)", "R"));
        assertEquals("[extra]", valueOf("greeting([hello, world, extra], R)", "R"));

        // digit//1 becomes digit/3
        assertTrue(succeeds("digit(7, [7], [])"));
    }

    @Test
    @DisplayName("a grammar is translated against the engine it is consulted into")
    public void translationUsesItsOwnEngine()
    {
        // This engine is not the first one constructed in this JVM - the base
        // class already made one - which is exactly the case that used to be
        // translated against a stranger's database.
        JIPEngine second = new JIPEngine();
        second.consultStream(new StringReader(GRAMMAR), "second");

        Probe probe = new Probe(second);
        assertTrue(probe.succeeds("phrase(greeting, [hello, world])"),
                "the rule must land in the engine it was consulted into");

        // and it must not have leaked into the first engine
        assertFalse(succeeds("catch(phrase(greeting, [hello, world]), _, fail)"),
                "the second engine's grammar must not be visible from the first");
    }

    /** Lets a second engine be driven through the shared helpers. */
    private static final class Probe extends PrologTestBase
    {
        Probe(JIPEngine engine)
        {
            this.engine = engine;
        }
    }
}
