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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ugos.jiprolog.engine.JIPErrorEvent;
import com.ugos.jiprolog.engine.JIPEvent;
import com.ugos.jiprolog.engine.JIPEventListener;
import com.ugos.jiprolog.engine.JIPTraceEvent;
import com.ugos.jiprolog.engine.JIPTraceListener;

/**
 * Registration and deregistration of the engine's listeners.
 *
 * <p>These cover CODE_REVIEW.md sections 4 and 12, both now fixed:
 * removeEventListener tested its condition the wrong way round and so never
 * removed anything, and the getters handed out the notifier's live internal
 * Vector.
 */
public class ListenerApiTest extends PrologTestBase
{
    @Test
    @DisplayName("an event listener can be added and removed again")
    public void eventListenerRoundTrip()
    {
        JIPEventListener listener = new NullEventListener();

        engine.addEventListener(listener);
        assertEquals(1, engine.getEventListeners().size());

        engine.removeEventListener(listener);
        assertEquals(0, engine.getEventListeners().size(),
                "removeEventListener must actually deregister the listener");
    }

    @Test
    @DisplayName("a trace listener can be added and removed again")
    public void traceListenerRoundTrip()
    {
        JIPTraceListener listener = new NullTraceListener();

        engine.addTraceListener(listener);
        assertEquals(1, engine.getTraceListeners().size());

        engine.removeTraceListener(listener);
        assertEquals(0, engine.getTraceListeners().size());
    }

    @Test
    @DisplayName("registering the same listener twice registers it once")
    public void registrationIsIdempotent()
    {
        JIPEventListener listener = new NullEventListener();

        engine.addEventListener(listener);
        engine.addEventListener(listener);

        assertEquals(1, engine.getEventListeners().size());
    }

    @Test
    @DisplayName("removing a listener that was never added is a no-op")
    public void removingAnUnregisteredListenerIsHarmless()
    {
        engine.addEventListener(new NullEventListener());
        engine.removeEventListener(new NullEventListener());

        assertEquals(1, engine.getEventListeners().size());
    }

    @Test
    @DisplayName("the returned listener collections are copies, not the live ones")
    public void listenerCollectionsAreNotExposed()
    {
        engine.addEventListener(new NullEventListener());
        engine.addTraceListener(new NullTraceListener());

        engine.getEventListeners().clear();
        engine.getTraceListeners().clear();

        assertEquals(1, engine.getEventListeners().size(),
                "clearing the returned collection must not deregister the listener");
        assertEquals(1, engine.getTraceListeners().size(),
                "clearing the returned collection must not deregister the listener");
    }

    private static final class NullEventListener implements JIPEventListener
    {
        public void openNotified(JIPEvent event) { }
        public void closeNotified(JIPEvent event) { }
        public void solutionNotified(JIPEvent event) { }
        public void moreNotified(JIPEvent event) { }
        public void endNotified(JIPEvent event) { }
        public void errorNotified(JIPErrorEvent event) { }
        public void termNotified(JIPEvent event) { }
    }

    private static final class NullTraceListener implements JIPTraceListener
    {
        public void callNotified(JIPTraceEvent event) { }
        public void redoNotified(JIPTraceEvent event) { }
        public void foundNotified(JIPTraceEvent event) { }
        public void bindNotified(JIPTraceEvent event) { }
        public void failNotified(JIPTraceEvent event) { }
        public void exitNotified(JIPTraceEvent event) { }
        public void startNotified(JIPTraceEvent event) { }
        public void stopNotified(JIPTraceEvent event) { }
    }
}
