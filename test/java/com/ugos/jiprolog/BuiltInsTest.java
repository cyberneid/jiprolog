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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Baseline for behaviour that is correct today. The point of this class is not
 * to find bugs but to pin down what works, so that the parser and engine
 * changes proposed in CODE_REVIEW.md can be made with something to fall back on.
 *
 * <p>Goals that would trip the prefix-operator parsing defect (CODE_REVIEW.md
 * section 1) are written with explicit parentheses - "(-7) mod 2" rather than
 * "-7 mod 2". Those cases have their own tests in {@link KnownDefectsTest};
 * here they are avoided so a failure means a real regression.
 */
public class BuiltInsTest extends PrologTestBase
{
    @Nested
    @DisplayName("arithmetic")
    public class Arithmetic extends PrologTestBase
    {
        @Test
        public void basicEvaluation()
        {
            assertEquals("8", valueOf("X is 7 + 1", "X"));
            assertEquals("6", valueOf("X is 7 - 1", "X"));
            assertEquals("42", valueOf("X is 6 * 7", "X"));
            assertEquals("2.0", valueOf("X is 10 / 5", "X"));
            assertEquals("2.0", valueOf("X is 1.0 + 1", "X"));
        }

        @Test
        @DisplayName("integer division truncates toward zero")
        public void integerDivision()
        {
            assertEquals("3", valueOf("X is 7 // 2", "X"));
            assertEquals("-3", valueOf("X is 7 // (-2)", "X"));
            assertEquals("-3", valueOf("X is (-7) // 2", "X"));
        }

        @Test
        @DisplayName("mod takes the sign of the divisor, rem the sign of the dividend")
        public void modAndRem()
        {
            assertEquals("1", valueOf("X is 7 mod 2", "X"));
            assertEquals("-1", valueOf("X is 7 mod (-2)", "X"));
            assertEquals("1", valueOf("X is (-7) mod 2", "X"));

            assertEquals("1", valueOf("X is 7 rem 2", "X"));
            assertEquals("1", valueOf("X is 7 rem (-2)", "X"));
            assertEquals("-1", valueOf("X is (-7) rem 2", "X"));
        }

        @Test
        public void comparison()
        {
            assertTrue(succeeds("1 < 2, 2 =< 2, 3 > 2, 3 >= 3, 2 =:= 2.0, 1 =\\= 2"));
        }

        @Test
        @DisplayName("evaluating an unbound or non-evaluable term raises the ISO error")
        public void evaluationErrors()
        {
            assertEquals("error(instantiation_error,context(undefined,file(undefined,0)))",
                    valueOf("catch(_ is _ + 1, E, true)", "E"));
            assertTrue(String.valueOf(valueOf("catch(_ is foo + 1, E, true)", "E"))
                    .startsWith("error(type_error(evaluable,foo / 0)"));
            assertTrue(String.valueOf(valueOf("catch(_ is 1 // 0, E, true)", "E"))
                    .startsWith("error(evaluation_error(zero_divisor)"));
        }

        @Test
        @DisplayName("integers are bounded at 2^31 - see CODE_REVIEW.md section 9")
        public void integersAreBounded()
        {
            assertTrue(succeeds("current_prolog_flag(bounded, true)"),
                    "the arithmetic is bounded, the flag must say so");
            assertTrue(String.valueOf(valueOf("catch(_ is 2147483647 + 1, E, true)", "E"))
                    .startsWith("error(evaluation_error(int_overflow)"));
        }
    }

    @Nested
    @DisplayName("terms")
    public class Terms extends PrologTestBase
    {
        @Test
        public void univAndFunctor()
        {
            assertEquals("[foo,a,b]", valueOf("foo(a,b) =.. L", "L"));
            assertEquals("foo(a,b)", valueOf("T =.. [foo,a,b]", "T"));
            assertEquals("foo", valueOf("functor(foo(a,b), N, _)", "N"));
            assertEquals("2", valueOf("functor(foo(a,b), _, A)", "A"));
            assertEquals("b", valueOf("arg(2, foo(a,b), X)", "X"));
        }

        @Test
        public void typeChecking()
        {
            assertTrue(succeeds("atom(foo), integer(1), float(1.0), var(_), compound(f(x))"));
            assertTrue(succeeds("atomic(foo), atomic(1), callable(foo), is_list([a,b])"));
            assertFalse(succeeds("atom(f(x))"));
            assertFalse(succeeds("var(foo)"));
        }

        @Test
        public void copyTermRenamesVariables()
        {
            assertTrue(succeeds("copy_term(f(X,Y,X), f(A,B,A)), A \\== X, B \\== Y"));
        }

        @Test
        public void standardOrderComparison()
        {
            assertEquals("[1,a,f(a)]", valueOf("msort([f(a),a,1], L)", "L"));
            assertTrue(succeeds("compare(<, 1, a), compare(=, a, a), compare(>, f(a), a)"));
        }

        @Test
        public void atomManipulation()
        {
            assertEquals("3", valueOf("atom_length(abc, L)", "L"));
            assertEquals("abc", valueOf("atom_concat(ab, c, X)", "X"));
            assertEquals("b", valueOf("sub_atom(abc, 1, 1, _, S)", "S"));
            assertEquals("[a,b]", valueOf("atom_chars(ab, L)", "L"));
            assertEquals("12", valueOf("number_codes(N, \"12\")", "N"));
        }

        @Test
        @DisplayName("write_canonical ignores the operator table")
        public void writeCanonicalIgnoresOperators()
        {
            assertEquals("-(1,2)", canonical("1 - 2"));
            assertEquals("+(a,*(b,c))", canonical("a + b * c"));
            assertEquals("'.'(1,'.'(2,[]))", canonical("[1,2]"));
            assertEquals("'hello world'", canonical("'hello world'"));
        }
    }

    @Nested
    @DisplayName("control constructs")
    public class Control extends PrologTestBase
    {
        @Test
        public void cutCommitsToTheFirstSolution()
        {
            consult("p(1). p(2). p(3).\nq(X) :- p(X), X > 1, !.");
            assertEquals(1, solveAll("q(_)").size());
            assertEquals("2", valueOf("q(X)", "X"));
        }

        @Test
        public void ifThenElse()
        {
            consult("p(1). p(2). p(3).");
            assertEquals("yes", valueOf("( p(2) -> R = yes ; R = no )", "R"));
            assertEquals("no", valueOf("( p(9) -> R = yes ; R = no )", "R"));
        }

        @Test
        public void negationAsFailure()
        {
            consult("p(1).");
            assertTrue(succeeds("\\+ p(2)"));
            assertFalse(succeeds("\\+ p(1)"));
        }

        @Test
        public void catchAndThrow()
        {
            assertEquals("ball", valueOf("catch(throw(ball), E, true)", "E"));
            assertEquals("recovered", valueOf("catch(throw(oops), _, R = recovered)", "R"));
            assertTrue(succeeds("catch(true, _, fail)"));
        }

        @Test
        public void findallBagofSetof()
        {
            consult("p(1). p(2). p(3).");
            assertEquals("[1,2,3]", valueOf("findall(X, p(X), L)", "L"));
            assertEquals("[]", valueOf("findall(X, (p(X), X > 9), L)", "L"));
            assertEquals("[2,3]", valueOf("bagof(X, (p(X), X > 1), L)", "L"));
            assertEquals("[1,2,3]", valueOf("setof(X, p(X), L)", "L"));
            assertFalse(succeeds("bagof(X, (p(X), X > 9), _)"),
                    "bagof/3 must fail, not return [], on an empty collection");
        }

        @Test
        @DisplayName("call/1 on a non-callable raises type_error(callable)")
        public void callTypeError()
        {
            assertTrue(String.valueOf(valueOf("catch(call(1), E, true)", "E"))
                    .startsWith("error(type_error(callable,1)"));
        }
    }

    @Nested
    @DisplayName("database")
    public class Database extends PrologTestBase
    {
        @Test
        public void assertAndRetract()
        {
            assertTrue(succeeds("assertz(fact(1)), assertz(fact(2))"));
            assertEquals("[1,2]", valueOf("findall(X, fact(X), L)", "L"));
            assertTrue(succeeds("retract(fact(1))"));
            assertEquals("[2]", valueOf("findall(X, fact(X), L)", "L"));
        }

        @Test
        public void assertaPrepends()
        {
            assertTrue(succeeds("assertz(fact(2)), asserta(fact(1))"));
            assertEquals("[1,2]", valueOf("findall(X, fact(X), L)", "L"));
        }

        @Test
        @DisplayName("the update view is logical: a clause asserted during a scan is not seen")
        public void logicalUpdateView()
        {
            consult(":- dynamic(item/1).");
            assertTrue(succeeds("assertz(item(1))"));
            List<Map<String, String>> seen =
                    solveAll("item(X), assertz(item(2))");
            assertEquals(1, seen.size(),
                    "the clause asserted inside the loop must not be visible to it");
            assertEquals("[1,2]", valueOf("findall(X, item(X), L)", "L"));
        }

        @Test
        @DisplayName("modifying a kernel predicate is refused")
        public void kernelPredicatesAreProtected()
        {
            assertTrue(String.valueOf(valueOf("catch(assertz(append([],x,x)), E, true)", "E"))
                    .startsWith("error(permission_error(modify,static_procedure"));
        }
    }

    @Nested
    @DisplayName("lists")
    public class Lists extends PrologTestBase
    {
        @Test
        public void membershipAndAppend()
        {
            assertEquals(3, solveAll("member(_, [a,b,c])").size());
            assertEquals("[a,b,c]", valueOf("append([a], [b,c], L)", "L"));
            assertEquals(4, solveAll("append(_, _, [a,b,c])").size());
        }

        @Test
        public void lengthGeneratesAndMeasures()
        {
            assertEquals("3", valueOf("length([a,b,c], N)", "N"));
            assertEquals(3, solveAll("length(L, 3), L = [_,_,_]").size() * 3);
        }

        @Test
        public void sorting()
        {
            assertEquals("[a,b,c]", valueOf("sort([c,a,b,a], L)", "L"));
            assertEquals("[a,a,b,c]", valueOf("msort([c,a,b,a], L)", "L"));
            assertEquals("[1 - a,2 - b]", valueOf("keysort([2-b,1-a], L)", "L"));
        }

        @Test
        public void betweenEnumerates()
        {
            assertEquals(3, solveAll("between(1, 3, _)").size());
            assertEquals("1", valueOf("between(1, 3, X)", "X"));
        }
    }
}
