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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Operator parsing, asserted through write_canonical/1 so that the operator
 * table cannot hide a bad parse - write/1 will happily print a corrupted term
 * in a form that looks reasonable.
 *
 * <p>The prefix-minus group covers CODE_REVIEW.md section 1. The cause turned
 * out not to be in PrologParser at all: OperatorManager declared prefix - and +
 * as 500 fx, where ISO 6.3.4.4 has them at 200 fy. At 500 the prefix operator
 * swallowed the priority-400 operators, and fx being non-associative made
 * "- - 1" a syntax error.
 */
public class ParserTest extends PrologTestBase
{
    @Nested
    @DisplayName("precedence and associativity")
    public class Precedence extends PrologTestBase
    {
        @Test
        public void arithmeticPrecedence()
        {
            assertEquals("+(a,*(b,c))", canonical("a+b*c"));
            assertEquals("*(+(a,b),c)", canonical("(a+b)*c"));
            // * and / are both 400 yfx, so this groups to the left
            assertEquals("/(*(a,b),c)", canonical("a*b/c"));
        }

        @Test
        @DisplayName("yfx operators associate to the left")
        public void leftAssociative()
        {
            assertEquals("-(-(a,b),c)", canonical("a-b-c"));
            assertEquals("-(a,-(b,c))", canonical("a-(b-c)"));
        }

        @Test
        @DisplayName("xfy operators associate to the right")
        public void rightAssociative()
        {
            assertEquals("^(a,^(b,c))", canonical("a^b^c"));
            assertEquals("','(a,','(b,c))", canonical("(a,b,c)"));
            assertEquals(";(a,;(b,c))", canonical("(a;b;c)"));
        }

        @Test
        public void controlConstructPrecedence()
        {
            assertEquals(";(a,->(b,c))", canonical("(a;b->c)"));
            assertEquals(";(->(a,b),c)", canonical("(a->b;c)"));
            assertEquals(":-(a,','(b,c))", canonical("(a:-b,c)"));
        }

        @Test
        public void listsAndBraces()
        {
            assertEquals("'.'(a,'.'(b,c))", canonical("[a,b|c]"));
            assertEquals("[]", canonical("[]"));
            assertEquals("{}(','(a,b))", canonical("{a,b}"));
            assertEquals("{}", canonical("{}"));
        }

        @Test
        public void numericLiterals()
        {
            assertEquals("97", canonical("0'a"));
            assertEquals("31", canonical("0x1f"));
            assertEquals("5", canonical("0b101"));
            assertEquals("15", canonical("0o17"));
            assertEquals("1500.0", canonical("1.5e3"));
        }
    }

    @Nested
    @DisplayName("prefix minus")
    public class PrefixMinus extends PrologTestBase
    {
        @Test
        @DisplayName("a negative literal keeps its sign across an infix operator")
        public void negativeLiteralBeforeInfixOperator()
        {
            assertEquals("+(-7,1)", canonical("-7 + 1"));
            assertEquals("-(-7,1)", canonical("-7 - 1"));
            assertEquals("-6", valueOf("X is -7 + 1", "X"));
            assertEquals("-8", valueOf("X is -7 - 1", "X"));
        }

        @Test
        @DisplayName("a prefix operator inside an argument list keeps its operand")
        public void prefixOperatorInsideArgumentList()
        {
            assertEquals("f(+(-7,1))", canonical("f(-7 + 1)"));
            assertEquals("f(+(-(a),1))", canonical("f(-a + 1)"));
            assertEquals("'.'(-7,[])", canonical("[-7]"));
        }

        @Test
        @DisplayName("prefix minus binds tighter than a priority 400 operator")
        public void bindsTighterThanMod()
        {
            assertEquals("mod(-7,2)", canonical("-7 mod 2"));
            assertEquals("mod(-(a),b)", canonical("- a mod b"));
            assertEquals("1", valueOf("X is -7 mod 2", "X"));
        }

        @Test
        @DisplayName("prefix minus is fy, so it can be stacked")
        public void isRightAssociative()
        {
            assertEquals("-(-(1))", canonical("- - 1"));
            assertEquals("-(-(a))", canonical("- - a"));
        }

        @Test
        @DisplayName("after an operand, minus is the infix operator")
        public void minusAfterAnOperandIsInfix()
        {
            assertEquals("-(a,1)", canonical("a-1"));
            assertEquals("-(a,-1)", canonical("a - -1"));
            assertEquals("-(3,-2)", canonical("3 - -2"));
            assertEquals("5", valueOf("X is 3 - -2", "X"));
            assertEquals("*(a,-1)", canonical("a * (-1)"));
        }

        @Test
        @DisplayName("layout decides between the negative literal and the operator")
        public void layoutSeparatesSignFromNumeral()
        {
            // ISO 6.3.1.2 forms the negative constant only when the sign is
            // followed directly by the numeral.
            assertEquals("-7", canonical("-7"));
            assertEquals("-(7)", canonical("- 7"));
            assertFalse(succeeds("integer(- 7)"));
            assertEquals("f(-(1))", canonical("f(- 1)"));
            assertEquals("foo(-(1),-1)", canonical("foo(- 1, -1)"));

            // an adjacent sign beats a priority 200 operator
            assertEquals("**(-2,2)", canonical("-2 ** 2"));
            // with layout, the prefix operator takes the whole power term
            assertEquals("-(**(2,2))", canonical("- 2 ** 2"));
        }

        @Test
        @DisplayName("the sign rule only applies in operand position")
        public void signRuleOnlyInOperandPosition()
        {
            // after an operand the minus is infix, however it is spaced
            assertEquals("-(2,1)", canonical("2-1"));
            assertEquals("-(2,1)", canonical("2 -1"));
            assertEquals("-(2,1)", canonical("2 - 1"));
            // a quoted minus is an atom, never a sign
            assertEquals("-(1)", canonical("'-'(1)"));
        }

        @Test
        @DisplayName("signed literals in other numeric notations")
        public void signedLiteralsInAllNotations()
        {
            assertEquals("-31", canonical("-0x1f"));
            assertEquals("-97", canonical("-0'a"));
            assertEquals("-1500.0", canonical("-1.5e3"));
            assertEquals("-(-1)", canonical("- -1"));
        }

        @Test
        @DisplayName("prefix minus applied to a compound")
        public void appliedToCompound()
        {
            assertEquals("-(a)", canonical("- a"));
            assertEquals("-(a)", canonical("-(a)"));
            assertEquals("-(3,4)", canonical("-(3,4)"));
            assertEquals("*(1,-2)", canonical("1 * -2"));
        }
    }
}
