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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Runs the ISO conformance suite in {@code test/resources/iso} and fails on any
 * unexpected result.
 *
 * <p>The cases are written from ISO/IEC 13211-1 rather than taken from an
 * existing suite, so that nothing of unclear provenance is vendored into an
 * AGPL tree. That is also the honest caveat about the number this reports: a
 * suite and an implementation checked against each other by the same hand is a
 * weaker signal than an independent one. It has earned its keep - it found
 * {@code sign(0)} returning -1 and {@code =../2} disagreeing with
 * {@code functor/3} about lists - but a run of the real inriasuite would still
 * be worth doing, and would very likely be redder.
 *
 * <p>The suite is expected to be entirely green. A case that starts failing is
 * either a regression, or a real deviation that belongs in CODE_REVIEW.md with
 * the case annotated to say so - not something to quietly delete.
 */
public class IsoConformanceTest extends PrologTestBase
{
    private static final String[] SUITE = {
        "/iso/runner.pl",
        "/iso/cases_control.pl",
        "/iso/cases_terms.pl",
        "/iso/cases_arith.pl",
        "/iso/cases_db.pl",
        "/iso/cases_atoms.pl",
        "/iso/cases_unify.pl",
        "/iso/cases_backtracking.pl",
        "/iso/cases_cut.pl",
        "/iso/cases_exceptions.pl",
        "/iso/cases_modules.pl",
        "/iso/cases_occurs.pl",
        "/iso/cases_intcut.pl",
        "/iso/cases_metacall.pl",
    };

    @Test
    @DisplayName("the ISO conformance suite passes")
    public void suitePasses() throws Exception
    {
        for (String resource : SUITE)
        {
            InputStream in = getClass().getResourceAsStream(resource);
            assertNotNull(in, resource + " is missing from the test classpath");
            try
            {
                engine.consultStream(new InputStreamReader(in, "ISO-8859-1"), resource);
            }
            finally
            {
                in.close();
            }
        }

        // the runner writes one line per failing case; capture it so a failure
        // report reaches the build log rather than stdout of a forked JVM
        OutputStream previous = engine.getUserOutputStream();
        ByteArrayOutputStream report = new ByteArrayOutputStream();
        Map<String, String> counts;
        try
        {
            engine.setUserOutputStream(report);
            counts = solveFirst("run_suite(Passed, Failed)");
        }
        finally
        {
            engine.setUserOutputStream(previous);
        }

        assertNotNull(counts, "the suite runner itself failed to run");

        int passed = Integer.parseInt(counts.get("Passed"));
        int failed = Integer.parseInt(counts.get("Failed"));

        System.out.println("ISO conformance: " + passed + " passed, " + failed
                + " failed, " + (passed + failed) + " cases");

        assertTrue(passed + failed >= 430,
                "the suite shrank to " + (passed + failed) + " cases; was something dropped?");
        assertEquals(0, failed,
                () -> "ISO conformance failures:\n" + report.toString());
    }
}
