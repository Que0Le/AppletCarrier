package com.example.applet_carrier.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProxmoxParsingTest {

    @Test
    fun nodes_extractsNames() {
        val body = """{"data":[{"node":"pve","status":"online"},{"node":"pve2","status":"online"}]}"""
        assertEquals(listOf("pve", "pve2"), parseNodeNames(body))
    }

    @Test
    fun nodes_emptyOrMalformed() {
        assertTrue(parseNodeNames("""{"data":[]}""").isEmpty())
        assertTrue(parseNodeNames("not json").isEmpty())
    }

    @Test
    fun qemu_parsesAndConvertsUnits() {
        // cpu 0.25 → 25%, mem 1 GiB → 1024 MB, maxmem 4 GiB → 4096 MB
        val body = """
            {"data":[
              {"vmid":100,"name":"web","status":"running","cpu":0.25,"mem":1073741824,"maxmem":4294967296},
              {"vmid":101,"name":"db","status":"stopped","cpu":0.0,"mem":0,"maxmem":2147483648}
            ]}
        """.trimIndent()
        val res = parseResources(body, "pve", ResourceType.VM)
        assertEquals(2, res.size)

        val web = res.first { it.vmid == 100L }
        assertEquals("web", web.name)
        assertEquals(ResourceType.VM, web.type)
        assertEquals("pve", web.node)
        assertEquals("running", web.status)
        assertEquals(25.0, web.cpuPercent)
        assertEquals(1024L, web.memUsedMb)
        assertEquals(4096L, web.memTotalMb)

        val db = res.first { it.vmid == 101L }
        assertEquals("stopped", db.status)
        assertEquals(0L, db.memUsedMb)
        assertEquals(2048L, db.memTotalMb)
    }

    @Test
    fun lxc_typeIsCt_andNameFallsBackToVmid() {
        val body = """{"data":[{"vmid":200,"status":"running","cpu":0.5,"mem":536870912,"maxmem":1073741824}]}"""
        val res = parseResources(body, "pve", ResourceType.CT)
        assertEquals(1, res.size)
        assertEquals(ResourceType.CT, res[0].type)
        assertEquals("200", res[0].name) // no name field → falls back to vmid
        assertEquals(50.0, res[0].cpuPercent)
        assertEquals(512L, res[0].memUsedMb)
    }

    @Test
    fun qmpstatus_paused_takesPrecedenceOverRunning() {
        // A paused VM: status=running (process alive) but qmpstatus=paused.
        val body = """{"data":[{"vmid":100,"name":"web","status":"running","qmpstatus":"paused","cpu":0.0,"mem":0,"maxmem":0}]}"""
        assertEquals("paused", parseResources(body, "pve", ResourceType.VM).first().status)
    }

    @Test
    fun status_usedWhenNoQmpstatus() {
        val body = """{"data":[{"vmid":100,"status":"stopped","cpu":0.0,"mem":0,"maxmem":0}]}"""
        assertEquals("stopped", parseResources(body, "pve", ResourceType.VM).first().status)
    }

    @Test
    fun qmpStatus_fromStatusCurrent() {
        assertEquals("paused", parseQmpStatus("""{"data":{"status":"running","qmpstatus":"paused","cpu":0.0}}"""))
        assertEquals("running", parseQmpStatus("""{"data":{"status":"running","qmpstatus":"running"}}"""))
    }

    @Test
    fun resources_skipsEntriesWithoutVmid() {
        val body = """{"data":[{"name":"ghost","status":"running"}]}"""
        assertTrue(parseResources(body, "pve", ResourceType.VM).isEmpty())
    }
}
