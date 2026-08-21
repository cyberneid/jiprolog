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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unification, backtracking and cut driven hard, against answers that are known
 * independently of this implementation.
 *
 * <p>The fine-grained cases live in the suite - {@code cases_unify.pl},
 * {@code cases_backtracking.pl}, {@code cases_cut.pl}. What is here is the
 * other half of the argument: whole programs whose correct output is a
 * published fact rather than something this engine decided. Six queens has four
 * solutions and five queens has ten, whoever computes them; naive reverse of
 * 1..20 is 20..1; tak(14,10,4) is 5. A resolution engine that gets those right
 * over hundreds of thousands of inferences, with the backtracking and the cuts
 * that generate-and-test implies, is doing the job.
 *
 * <p>This class exists because the performance work in CODE_REVIEW.md §10
 * changed both the backtracking path and the unification trail, and the
 * coverage at the time was two cut cases and ten trivial unification cases -
 * not enough to change either with confidence.
 */
public class ResolutionTest extends PrologTestBase
{
    private static final String QUEENS =
            "q(N, Qs) :- upto(1, N, Ns), perm(Ns, Qs), safe(Qs).\n"
          + "upto(N, N, [N]) :- !.\n"
          + "upto(I, N, [I|T]) :- I < N, I1 is I+1, upto(I1, N, T).\n"
          + "perm([], []).\n"
          + "perm(Qs, [Q|R]) :- sel(Q, Qs, Qs1), perm(Qs1, R).\n"
          + "sel(X, [X|T], T).\n"
          + "sel(X, [H|T], [H|R]) :- sel(X, T, R).\n"
          + "safe([]).\n"
          + "safe([Q|Qs]) :- noatt(Q, Qs, 1), safe(Qs).\n"
          + "noatt(_, [], _).\n"
          + "noatt(X, [Y|Ys], N) :- X =\\= Y+N, X =\\= Y-N, N1 is N+1, noatt(X, Ys, N1).\n";

    private static final String CLASSICS =
            "nrev([], []).\n"
          + "nrev([H|T], R) :- nrev(T, RT), app(RT, [H], R).\n"
          + "app([], L, L).\n"
          + "app([H|T], L, [H|R]) :- app(T, L, R).\n"
          + "tak(X, Y, Z, A) :- X =< Y, !, Z = A.\n"
          + "tak(X, Y, Z, A) :- X1 is X-1, Y1 is Y-1, Z1 is Z-1,\n"
          + "    tak(X1, Y, Z, A1), tak(Y1, Z, X, A2), tak(Z1, X, Y, A3), tak(A1, A2, A3, A).\n"
          + "upto(N, N, [N]) :- !.\n"
          + "upto(I, N, [I|T]) :- I < N, I1 is I+1, upto(I1, N, T).\n";

    @Nested
    @DisplayName("whole programs, against independently known answers")
    public class KnownAnswers extends PrologTestBase
    {
        @Test
        @DisplayName("six queens has exactly four solutions")
        public void sixQueens()
        {
            consult(QUEENS);

            assertEquals("[2,4,6,1,3,5]", valueOf("q(6, Qs)", "Qs"),
                    "the first solution of six queens in this generation order");
            assertEquals("4", valueOf("findall(Q, q(6, Q), L), length(L, N)", "N"));
        }

        @Test
        @DisplayName("five queens has exactly ten solutions, and they are the right ten")
        public void fiveQueens()
        {
            consult(QUEENS);

            assertEquals(
                    "[[1,3,5,2,4],[1,4,2,5,3],[2,4,1,3,5],[2,5,3,1,4],[3,1,4,2,5],"
                  + "[3,5,2,4,1],[4,1,3,5,2],[4,2,5,3,1],[5,2,4,1,3],[5,3,1,4,2]]",
                    valueOf("findall(Q, q(5, Q), L)", "L"));
        }

        @Test
        @DisplayName("exhausting the search leaves nothing bound behind")
        public void exhaustiveBacktracking()
        {
            consult(QUEENS);

            // drives the whole search space and then checks the variable came
            // back unbound - the failure path has to undo every binding it made
            assertTrue(succeeds("( q(6, Qs), fail ; var(Qs) )"));
        }

        @Test
        @DisplayName("naive reverse and tak")
        public void classics()
        {
            consult(CLASSICS);

            assertEquals("[20,19,18,17,16,15,14,13,12,11,10,9,8,7,6,5,4,3,2,1]",
                    valueOf("upto(1, 20, L0), nrev(L0, R)", "R"));
            // tak(14,10,4) is the largest of the family that stays inside a
            // test budget: tak(18,12,6), the one the benchmark literature
            // quotes, takes minutes at this speed and belongs in bench/, not
            // in a suite that runs on every push.
            assertEquals("5", valueOf("tak(14, 10, 4, A)", "A"));
        }
    }

    @Nested
    @DisplayName("bindings are undone on every failure path")
    public class BindingRestoration extends PrologTestBase
    {
        @Test
        @DisplayName("a failed unification leaves its operands untouched")
        public void failedUnification()
        {
            assertTrue(succeeds("\\+ f(X, a) = f(1, b), var(X)"));
            assertTrue(succeeds("\\+ [A, B, C] = [1, 2], var(A), var(B), var(C)"));
            assertTrue(succeeds("X = Y, \\+ f(X, b) = f(a, c), var(X), var(Y), X == Y"));
        }

        @Test
        @DisplayName("a built-in that binds and then fails undoes its own bindings")
        public void builtInThatBindsThenFails()
        {
            // integer_bounds/2 unifies both arguments with &&, so a wrong second
            // argument fails only after the first has already been bound. This is
            // the case that a trail held by the caller does not cover, because
            // those bindings never reach the caller's trail.
            assertTrue(succeeds("\\+ integer_bounds(X, 999), var(X)"));
            assertTrue(succeeds("\\+ functor(foo(a), F, 9), var(F)"));
            assertTrue(succeeds("\\+ foo(a,b) =.. [foo, Y, c], var(Y)"));
        }

        @Test
        @DisplayName("a failure-driven loop leaves nothing bound")
        public void failureDrivenLoop()
        {
            consult("r(1). r(2). r(3).\n");

            assertTrue(succeeds("( r(X), fail ; var(X) )"));
            assertTrue(succeeds("( r(X), r(Y), fail ; var(X), var(Y) )"));
            assertTrue(succeeds("( T = f(X), r(X), fail ; var(T) )"));
        }
    }

    @Nested
    @DisplayName("cut")
    public class Cut extends PrologTestBase
    {
        @Test
        @DisplayName("commits to the clause and to the goals on its left")
        public void commits()
        {
            consult("c(1). c(2). c(3).\n"
                  + "first(X) :- c(X), !.\n"
                  + "two(a) :- !.\n"
                  + "two(b).\n");

            assertEquals("[1]", valueOf("findall(X, first(X), L)", "L"));
            assertEquals("[a]", valueOf("findall(X, two(X), L)", "L"));
            assertTrue(succeeds("two(b)"), "the second clause is still reachable by a matching call");
        }

        @Test
        @DisplayName("does not touch the goals on its right")
        public void leavesTheRightAlone()
        {
            consult("c(1). c(2). c(3).\n"
                  + "pair(X-Y) :- c(X), !, c(Y).\n");

            assertEquals("[1 - 1,1 - 2,1 - 3]", valueOf("findall(P, pair(P), L)", "L"));
        }

        @Test
        @DisplayName("is transparent to ;/2 and to the branches of ->/2")
        public void transparentToControlConstructs()
        {
            // the cut removes the alternative branch, so fail then fails outright
            assertTrue(!succeeds("(!, fail ; true)"));
            assertTrue(succeeds("(! ; true)"));
        }

        @Test
        @DisplayName("is opaque to call/1, \\+/1 and findall/3")
        public void opaqueToMetaCalls()
        {
            consult("c(1). c(2). c(3).\n");

            // call(!) cuts inside the call only, so c(X) keeps its choice points
            assertEquals("[1,2,3]", valueOf("findall(X, (c(X), call(!)), L)", "L"));
            assertEquals("[1,2,3]", valueOf("findall(X, (c(X), \\+ (!, fail)), L)", "L"));
            // but a cut inside call does still commit within it
            assertEquals("[1]", valueOf("findall(X, call((c(X), !)), L)", "L"));
        }

        @Test
        @DisplayName("if-then-else commits to the condition, soft cut does not")
        public void ifThenElseAndSoftCut()
        {
            consult("c(1). c(2). c(3).\n");

            assertEquals("[1]", valueOf("findall(X, ( c(X) -> true ; X = none ), L)", "L"));
            assertEquals("[none]", valueOf("findall(X, ( c(X), X > 9 -> true ; X = none ), L)", "L"));

            assertEquals("[1,2,3]", valueOf("findall(X, ( c(X) *-> true ; X = none ), L)", "L"));
            assertEquals("[none]", valueOf("findall(X, ( c(X), X > 9 *-> true ; X = none ), L)", "L"));
        }
    }
}
