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

import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


/**
 * Executable specifications for the defects recorded in CODE_REVIEW.md.
 *
 * <p>Every test here asserts the <em>correct</em> behaviour and is
 * {@link Disabled}, so CI stays green while the defects are open. Fixing a
 * defect means deleting its {@code @Disabled} - the assertion is already
 * written and says what the answer should be.
 *
 * <p>Do not "fix" these by changing the expected values to match what the
 * engine currently does. That would turn a bug report into a specification.
 *
 * <p>Sections 4 and 12 used to live here and are now fixed; their tests moved
 * to ListenerApiTest.
 *
 * <p>Sections 2 and 3 of the review - the statically pinned DCG engine and the
 * shared mutable statics - are not covered here. Both need a multi-engine or
 * multi-threaded harness to demonstrate reliably, and a weak test for them
 * would be worse than none.
 */
public class KnownDefectsTest extends PrologTestBase
{
    // ---------------------------------------------------------------- section 1

    @Test
    @Disabled("CODE_REVIEW.md section 1a - PrologParser drops the prefix operator")
    @DisplayName("a negative literal followed by an infix operator keeps its sign")
    public void negativeLiteralBeforeInfixOperator()
    {
        // parses as +(7,1), so this currently evaluates to 8
        assertEquals("-6", valueOf("X is -7 + 1", "X"));
        // parses as -(7,1), so this currently evaluates to 6
        assertEquals("-8", valueOf("X is -7 - 1", "X"));
        assertEquals("+(-7,1)", canonical("-7 + 1"));
    }

    @Test
    @Disabled("CODE_REVIEW.md section 1b - PrologParser deletes the operand")
    @DisplayName("a prefix operator inside an argument list keeps its operand")
    public void prefixOperatorInsideArgumentList()
    {
        // currently -(+(1)): the 7 is discarded outright
        assertEquals("f(+(-7,1))", canonical("f(-7 + 1)"));
        // currently -(+(1)): the atom a is discarded outright
        assertEquals("f(+(-(a),1))", canonical("f(-a + 1)"));
    }

    @Test
    @Disabled("CODE_REVIEW.md section 1c - prefix minus binds too loosely")
    @DisplayName("a negative literal binds tighter than a priority 400 operator")
    public void negativeLiteralBindsTighterThanMod()
    {
        // parses as -(mod(7,2)), so this currently evaluates to -1
        assertEquals("1", valueOf("X is -7 mod 2", "X"));
        assertEquals("mod(-7,2)", canonical("-7 mod 2"));
        // parses as -(**(2,2)), so this currently evaluates to -4.
        // Asserted on the parse rather than the value, so that this test turns
        // green on the parser fix alone and does not also wait on **/2 (below).
        assertEquals("**(-2,2)", canonical("-2 ** 2"));
    }

    @Test
    @Disabled("CODE_REVIEW.md section 1 - '- 7' with layout is a compound, not a literal")
    @DisplayName("a minus separated by layout is the compound -(7)")
    public void minusWithLayoutIsCompound()
    {
        assertFalse(succeeds("integer(- 7)"));
        assertEquals("-(7)", canonical("- 7"));
    }

    // ---------------------------------------------------------------- section 9

    @Test
    @Disabled("CODE_REVIEW.md section 9 - ** must return a float")
    @DisplayName("**/2 is float exponentiation")
    public void powerReturnsFloat()
    {
        assertEquals("1024.0", valueOf("X is 2 ** 10", "X"));
        // ^/2, unlike **/2, stays integral for integer arguments
        assertEquals("1024", valueOf("X is 2 ^ 10", "X"));
    }

    @Test
    @Disabled("CODE_REVIEW.md section 9 - integers are doubles bounded at 2^31")
    @DisplayName("integer arithmetic covers at least the 64-bit range")
    public void integerRange()
    {
        assertEquals("6227020800", valueOf("X is 13 * 479001600", "X"));
    }
}
