package sfg.wseminar.rover.i18n

import kotlin.test.*
class LocalizationTest {
    @Test fun requiredTermsAndMessagesTranslateWithoutChangingStoredText() {
        val de = AppLanguage.GERMAN
        assertEquals("Raster", translate("Discrete", de))
        assertEquals("Wegpunkte", translate("Continuous", de))
        assertEquals("Lokal", translate("Local", de))
        assertEquals("Global", translate("Global", de))
        assertEquals("Laufzeit", translate("Run time", de))
        assertEquals("12 Knoten", translate("12 nodes", de))
        assertEquals("Algorithmus auf Go to Waypoint geändert", translate("Algorithm changed to Go to Waypoint", de))
        assertEquals("Kosten ab Start (g)", translate("Cost from start (g)", de))
        val message = "Visiting A: path cost 2.5, estimated total 8.0."
        assertEquals("Bearbeite A: Pfadkosten 2.5, geschätzte Gesamtkosten 8.0.", translate(message, de))
        assertEquals(message, translate(message, AppLanguage.ENGLISH))
        assertEquals("Import fehlgeschlagen: Datei konnte nicht gelesen werden.", translate("Import failed: Could not read file.", de))
    }
    @Test fun everyTemplatePreservesItsParameters() {
        val token = Regex("\\{\\d+\\}")
        GermanCatalog.entries.forEach { (source, target) ->
            assertEquals(token.findAll(source).map { it.value }.toSet(), token.findAll(target).map { it.value }.toSet(), source)
        }
    }
}
