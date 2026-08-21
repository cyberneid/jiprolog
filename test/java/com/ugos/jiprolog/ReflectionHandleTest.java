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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The handles that the Prolog-to-Java bridge hands out for Java objects.
 *
 * <p>Covers CODE_REVIEW.md section 5: handles used to be "#" + hashCode(), and
 * hash codes are not unique. Two empty java.util.ArrayLists both hash to 1, so
 * both got handle #1, the second overwrote the first in the table, and Prolog
 * code holding the first handle silently started operating on the second
 * object.
 */
public class ReflectionHandleTest extends PrologTestBase
{
    @Test
    @DisplayName("distinct Java objects get distinct handles, even when they hash alike")
    public void objectsThatHashAlikeGetDistinctHandles()
    {
        Map<String, String> bindings = solveFirst(
                "create_object('java.util.ArrayList', [], H1),"
                        + " create_object('java.util.ArrayList', [], H2)");

        assertNotEquals(bindings.get("H1"), bindings.get("H2"),
                "two empty ArrayLists both hash to 1 and must still get distinct handles");
    }

    @Test
    @DisplayName("mutating one object is not visible through another object's handle")
    public void objectsStayIndependent()
    {
        Map<String, String> sizes = solveFirst(
                "create_object('java.util.ArrayList', [], H1),"
                        + " create_object('java.util.ArrayList', [], H2),"
                        + " invoke(H1, add('java.lang.Object'), [only_in_list_1], _),"
                        + " invoke(H1, size, [], S1),"
                        + " invoke(H2, size, [], S2)");

        assertEquals("1", sizes.get("S1"));
        assertEquals("0", sizes.get("S2"),
                "the second list must not see the first list's mutation");
    }

    @Test
    @DisplayName("the same object marshalled out twice keeps the same handle")
    public void handlesAreStableForOneObject()
    {
        // getClass() returns the identical Class object both times, so the two
        // handles must be equal - handle identity has to follow object identity.
        Map<String, String> bindings = solveFirst(
                "create_object('java.util.ArrayList', [], H),"
                        + " invoke(H, getClass, [], C1),"
                        + " invoke(H, getClass, [], C2)");

        assertEquals(bindings.get("C1"), bindings.get("C2"),
                "one object must map to one handle");
    }

    @Test
    @DisplayName("a released handle is no longer resolvable")
    public void releaseObjectDropsTheHandle()
    {
        assertTrue(succeeds(
                "create_object('java.util.ArrayList', [], H), invoke(H, size, [], _)"));

        assertFalse(succeeds(
                "create_object('java.util.ArrayList', [], H),"
                        + " release_object(H),"
                        + " catch(invoke(H, size, [], _), _, fail)"),
                "invoking through a released handle must not succeed");
    }
}
