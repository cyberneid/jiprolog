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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ISO/IEC 13211-1 7.10.5 - writing a term with operators.
 *
 * <p>The property that matters is that {@code writeq/1} output reads back as
 * the term it was given. It did not: every operand was written at no priority
 * bound at all, so {@code f(a,(b,c),d)} came out as {@code f(a,b,c,d)} and read
 * back as {@code f/4}, and {@code *(a,+(b,c))} and {@code +(*(a,b),c)} came out
 * as the same eight characters. See CODE_REVIEW.md section 17.
 *
 * <p>{@link #roundTrips} is the test that would have caught it; the explicit
 * cases below are there so a failure says which rule broke rather than just
 * that something did.
 */
public class WriteTermTest extends PrologTestBase
{
    /**
     * What writeq/1 produces for a term given in Prolog source syntax.
     *
     * <p>The term is bracketed before being spliced into the goal. ((T)) is T,
     * and it keeps a term whose principal functor is ','/2 from arriving as a
     * second argument of writeq - which would call writeq/2 and ask for a
     * stream.
     */
    private String written(String term)
    {
        return captureOutput("writeq((" + term + "))");
    }

    /**
     * Asserts that writeq output, read back, is the same term. Both sides are
     * compared through write_canonical/1, which uses no operators and so is
     * not the thing under test.
     */
    private void roundTrip(String term)
    {
        String printed = written(term);

        // Both sides are re-parsed inside brackets. (T) is T, and it keeps a
        // term whose principal functor is ','/2 - printed unbracketed at top
        // level, correctly - from arriving as extra arguments of
        // write_canonical/1.
        assertEquals(anonymise(canonical("(" + term + ")")), anonymise(canonical("(" + printed + ")")),
                "writeq wrote " + term + " as \"" + printed + "\", which reads back as something else");
    }

    /**
     * Variables print as their address, which is fresh on every parse, so two
     * readings of the same text disagree on the digits. Structure is what is
     * being compared here, so the digits go.
     */
    private static String anonymise(String canonical)
    {
        return canonical.replaceAll("_[0-9]+", "_");
    }

    @Nested
    @DisplayName("an argument is written at priority 999")
    class Arguments
    {
        @Test
        @DisplayName("a conjunction argument keeps its brackets, so the arity survives")
        public void conjunctionArgument()
        {
            assertEquals("f(a,(b,c),d)", written("f(a,(b,c),d)"));
            assertEquals("f((a,b))", written("f((a,b))"));
            assertEquals("f((a,b,c))", written("f((a,b,c))"));
            assertEquals("g((a,b),(c,d))", written("g((a,b),(c,d))"));
        }

        @Test
        @DisplayName("other operators above 999 are bracketed too")
        public void operatorsAbove999()
        {
            assertEquals("f(a,(b ; c),d)", written("f(a,(b;c),d)"));
            assertEquals("f(a,(b -> c),d)", written("f(a,(b->c),d)"));
            assertEquals("f(a,(b :- c),d)", written("f(a,(b:-c),d)"));
        }

        @Test
        @DisplayName("an operator at or below 999 is not bracketed")
        public void operatorsBelow999()
        {
            assertEquals("f(a + b,c)", written("f(a+b,c)"));
            assertEquals("f(a = b)", written("f(a=b)"));
            // \+ is 900, below the 999 an argument allows, so no brackets. The
            // quotes are a separate defect in the quoting rules - a token made
            // only of graphic characters needs none - and are not this test's
            // subject; it round-trips either way.
            assertEquals("f('\\\\+' a)", written("f(\\+ a)"));
        }

        @Test
        @DisplayName("list elements follow the same rule as arguments")
        public void listElements()
        {
            assertEquals("[(a,b),c]", written("[(a,b),c]"));
            assertEquals("[a + b,c]", written("[a+b,c]"));
        }
    }

    @Nested
    @DisplayName("an operand is written at the operator's priority, adjusted for associativity")
    class Operands
    {
        @Test
        @DisplayName("yfx: the left operand may match, the right may not")
        public void leftAssociative()
        {
            // a-b-c is -(-(a,b),c) and needs no brackets; a-(b-c) does
            assertEquals("a - b - c", written("(a-b)-c"));
            assertEquals("a - (b - c)", written("a-(b-c)"));
            assertEquals("a * b + c", written("(a*b)+c"));
            assertEquals("(a + b) * c", written("(a+b)*c"));
        }

        @Test
        @DisplayName("xfy: the right operand may match, the left may not")
        public void rightAssociative()
        {
            assertEquals("2 ^ 3 ^ 4", written("2^(3^4)"));
            assertEquals("(2 ^ 3) ^ 4", written("(2^3)^4"));
        }

        @Test
        @DisplayName("two different terms no longer print the same")
        public void distinctTermsPrintDistinctly()
        {
            assertNotEquals(written("a*(b+c)"), written("(a*b)+c"));
            assertNotEquals(written("a-(b-c)"), written("(a-b)-c"));
        }

        @Test
        @DisplayName("a prefix operator brackets an operand that binds more loosely")
        public void prefixOperand()
        {
            assertEquals("- (1 + 2)", written("-(1+2)"));
            assertEquals("- 1", written("-(1)"));
            assertEquals("- - a", written("- - a"));
        }
    }

    @Nested
    @DisplayName("an atom that is an operator")
    class AtomOperators
    {
        @Test
        @DisplayName("is bracketed when the position does not allow its priority")
        public void bracketedAsOperand()
        {
            // not is 900 fy; the right operand of =/2 (700 xfx) allows 699.
            // Without the brackets "EOS = not ; b" reads back with not taken
            // as a prefix operator - which is why xio.pl writes EOS = (not).
            assertEquals("a = (not)", written("a = (not)"));
            roundTrip("(a = (not) ; b)");
        }

        @Test
        @DisplayName("is left alone when the position does allow it")
        public void notBracketedWhenItFits()
        {
            // - is at most 500 (yfx), and an argument allows 999
            assertEquals("f(-,a)", written("f(-,a)"));
            assertEquals("[+,-,*]", written("[+,-,*]"));
            assertEquals("f(not)", written("f(not)"));
        }
    }

    @Nested
    @DisplayName("curly terms")
    class CurlyTerms
    {
        @Test
        @DisplayName("'{}'(T) is written {T}")
        public void braces()
        {
            assertEquals("{a}", written("{a}"));
            assertEquals("{a,b}", written("{a,b}"));
            assertEquals("{}", written("{}"));
        }
    }

    @Nested
    @DisplayName("writeq output reads back as the same term")
    class RoundTrip
    {
        @Test
        @DisplayName("for terms that mix operators, arguments and lists")
        public void roundTrips()
        {
            String[] terms = {
                "f(a,(b,c),d)",  "f(a,(b;c),d)",   "f(a,(b:-c),d)",  "f(a,(b->c),d)",
                "g((a,b),(c,d))", "a*(b+c)",       "(a*b)+c",        "a-(b-c)",
                "(a-b)-c",       "(a+b)*c",        "-(1+2)",         "a = (b;c)",
                "(1+2)**3",      "{a,b}",          "{a}",            "[(a,b),c]",
                "[a|(b,c)]",     "f(-,a)",         "- - a",          "a - -1",
                "f(a+b,c)",      "(a:-b;c)",       "(a,b,c)",        "f((a,b,c))",
                "-(1)",          "\\+ (a,b)",      "(\\+ a, b)",     "[1,2|X]",
                "f(\\+ a)",      "(a = (not) ; b)","2^(3^4)",        "(2^3)^4",
                "a mod (b mod c)", "(a;b),c",      "a;(b,c)",        "[(:-)]",
                "f(a,-,b)",      "x = (a->b;c)",   "2 * (-3)",       "(a->b;c->d;e)",
            };

            for (String term : terms)
                roundTrip(term);
        }
    }
}
