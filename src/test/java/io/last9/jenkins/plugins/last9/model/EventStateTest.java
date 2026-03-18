package io.last9.jenkins.plugins.last9.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class EventStateTest {

    @Test
    public void fromStringStart() {
        assertEquals(EventState.START, EventState.fromString("start"));
        assertEquals(EventState.START, EventState.fromString("START"));
        assertEquals(EventState.START, EventState.fromString("Start"));
        assertEquals(EventState.START, EventState.fromString(" start "));
    }

    @Test
    public void fromStringStop() {
        assertEquals(EventState.STOP, EventState.fromString("stop"));
        assertEquals(EventState.STOP, EventState.fromString("STOP"));
    }

    @Test
    public void fromStringNullDefaultsToStop() {
        assertEquals(EventState.STOP, EventState.fromString(null));
        assertEquals(EventState.STOP, EventState.fromString(""));
        assertEquals(EventState.STOP, EventState.fromString("  "));
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromStringInvalidThrows() {
        EventState.fromString("invalid");
    }

    @Test
    public void getValue() {
        assertEquals("start", EventState.START.getValue());
        assertEquals("stop", EventState.STOP.getValue());
    }
}
