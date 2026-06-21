package com.adrianrusu.mediaapp.core.designsystem.tokens

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Element

class PandaWaveResourceContractTest {
    private val root = File(requireNotNull(System.getProperty("pandawave.rootDir")))
    private val baseRes = root.resolve("core/designsystem/src/main/res")
    private val overlayRes = root.resolve("rro/bamboo-grove-overlay/src/main/res")

    @Test
    fun `public resources use final PandaWave names`() {
        assertTrue(publicEntries().all { it.name.startsWith("pandawave_") })
    }

    @Test
    fun `public and overlayable resource contracts are identical`() {
        assertEquals(publicEntries(), overlayableEntries())
    }

    @Test
    fun `reference overlay mirrors the public contract`() {
        assertEquals(publicEntries(), resourceDefinitions(overlayRes))
    }

    @Test
    fun `every public resource has a base definition`() {
        val missing = publicEntries() - resourceDefinitions(baseRes)

        assertTrue(missing.isEmpty(), "Missing base definitions: $missing")
    }

    @Test
    fun `component color resources expose every required interaction state`() {
        requiredSelectorStates.forEach { (resourceName, requiredStates) ->
            val selector = baseRes.resolve("color/$resourceName.xml")
            assertTrue(selector.isFile, "Missing selector resource: $resourceName")

            val actualStates = document(selector)
                .getElementsByTagName("item")
                .asElements()
                .flatMap { item ->
                    (0 until item.attributes.length).mapNotNull { index ->
                        item.attributes.item(index).localName?.takeIf { it.startsWith("state_") }
                    }
                }.toSet()

            assertTrue(
                actualStates.containsAll(requiredStates),
                "$resourceName is missing states: ${requiredStates - actualStates}"
            )
        }
    }

    private fun publicEntries(): Set<ResourceEntry> = document(baseRes.resolve("values/public.xml"))
        .getElementsByTagName("public")
        .asElements()
        .map { ResourceEntry(type = it.attribute("type"), name = it.attribute("name")) }
        .toSet()

    private fun overlayableEntries(): Set<ResourceEntry> = document(baseRes.resolve("values/overlayable.xml"))
        .getElementsByTagName("item")
        .asElements()
        .map { ResourceEntry(type = it.attribute("type"), name = it.attribute("name")) }
        .toSet()

    private fun resourceDefinitions(resDirectory: File): Set<ResourceEntry> = buildSet {
        resDirectory.listFiles().orEmpty().filter(File::isDirectory).forEach { directory ->
            when {
                directory.name.startsWith("values") -> {
                    directory.listFiles { file -> file.extension == "xml" }.orEmpty().forEach { file ->
                        document(file).documentElement.childNodes.asElements().forEach { element ->
                            when (element.tagName) {
                                "public", "overlayable" -> Unit

                                "item" -> add(
                                    ResourceEntry(
                                        type = element.attribute("type"),
                                        name = element.attribute("name")
                                    )
                                )

                                else -> add(
                                    ResourceEntry(
                                        type = element.tagName,
                                        name = element.attribute("name")
                                    )
                                )
                            }
                        }
                    }
                }

                else -> directory.listFiles().orEmpty().filter(File::isFile).forEach { file ->
                    add(ResourceEntry(type = directory.name.substringBefore('-'), name = file.nameWithoutExtension))
                }
            }
        }
    }.filterTo(mutableSetOf()) { it.type.isNotBlank() && it.name.isNotBlank() }

    private fun document(file: File) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
        setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
    }.newDocumentBuilder().parse(file)

    private fun Element.attribute(name: String): String = getAttribute(name)

    private fun org.w3c.dom.NodeList.asElements(): List<Element> = (0 until length).mapNotNull { item(it) as? Element }

    private data class ResourceEntry(val type: String, val name: String)

    private companion object {
        val requiredSelectorStates = mapOf(
            "pandawave_button_colors" to setOf("state_enabled", "state_pressed", "state_focused"),
            "pandawave_icon_button_colors" to setOf("state_enabled", "state_pressed", "state_focused"),
            "pandawave_navigation_item_colors" to
                setOf("state_enabled", "state_pressed", "state_focused", "state_selected"),
            "pandawave_preference_item_colors" to
                setOf("state_enabled", "state_pressed", "state_focused", "state_checked"),
            "pandawave_media_item_colors" to
                setOf("state_enabled", "state_pressed", "state_focused", "state_selected")
        )
    }
}
