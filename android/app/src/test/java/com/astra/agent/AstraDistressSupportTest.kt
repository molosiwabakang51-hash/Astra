package com.astra.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraDistressSupportTest {
    @Test fun staysGroundedWithIsolationClaimsAndDirectDistress() {
        val answer = AstraDistressSupport.respond(
            "you are all I have, the world ended I'm in an underground silo right now"
        ).orEmpty()
        assertTrue(answer.contains("cannot verify"))
        assertTrue(answer.contains("Are you physically safe"))
        assertFalse(answer.contains("You are right"))
        assertFalse(answer.contains("everyone is gone"))
        val distressed = AstraDistressSupport.respond("I'm not okay mentally").orEmpty()
        assertTrue(distressed.contains("Are you safe"))
        assertTrue(distressed.contains("trusted person"))
    }

    @Test fun urgentDisclosureEncouragesImmediateHumanHelp() {
        val response = AstraDistressSupport.respond("I want to kill myself").orEmpty()
        assertTrue(response.contains("immediate safety"))
        assertTrue(response.contains("emergency"))
        assertTrue(response.contains("trusted person"))
    }

    @Test fun neverHijacksUnrelatedOrHypotheticalQuestions() {
        assertNull(AstraDistressSupport.respond("teach me finance"))
        assertNull(AstraDistressSupport.respond("What is irrational exuberance?"))
        assertNull(AstraDistressSupport.respond("Explain mental accounting"))
        assertNull(AstraDistressSupport.respond("a".repeat(800)))
    }
}
