package ph.merd.akma.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import ph.merd.akma.domain.ActionCatalog
import ph.merd.akma.ui.theme.AkmaTokens

class UiKitConsistencyTest {
    @Test
    fun everyCatalogCategoryHasPresentation() {
        assertEquals(ActionCatalog.categoryIds, CategoryPresentation.categoryIds)
        ActionCatalog.categoryIds.forEach { assertNotNull(it, CategoryPresentation.category(it)) }
    }

    @Test
    fun colorsXmlMatchesKotlinTokens() {
        val file = File("src/main/res/values/colors.xml")
        assertTrue("run from the app module", file.isFile)
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("color")
        val xml = (0 until nodes.length).associate {
            val element = nodes.item(it) as Element
            element.getAttribute("name") to element.textContent.trim().removePrefix("#").toLong(16)
        }
        assertEquals(AkmaTokens.colorResources, xml)
    }

    @Test
    fun previewFixturesStayInsidePreviewPackage() {
        val root = File("src/main/java")
        assertTrue("run from the app module", root.isDirectory)
        val previewDir = File(root, "ph/merd/akma/ui/preview").canonicalFile
        val leaks = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.canonicalFile.startsWith(previewDir) }
            .filter { it.readText().contains("PreviewFixtures") }
            .toList()
        assertTrue("PreviewFixtures referenced outside ui/preview: $leaks", leaks.isEmpty())
    }
}
