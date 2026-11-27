package com.manhwatranslate.core.scan

import com.manhwatranslate.core.geometry.RectI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PaddleOcrParserTest {

    @Test
    fun `reads the classic results envelope`() {
        val raw = """
            {"msg":"","status":"000","results":[[
              {"confidence":0.98,"text":"WH-WHAT'S","text_region":[[210,5644],[346,5644],[346,5671],[210,5671]]},
              {"confidence":0.97,"text":"GOING ON?","text_region":[[205,5668],[350,5668],[350,5699],[205,5699]]}
            ]]}
        """.trimIndent()
        val regions = PaddleOcrParser.parse(raw)
        assertEquals(2, regions.size)
        assertEquals(RectI(210, 5644, 346, 5671), regions[0].box)
        assertEquals("WH-WHAT'S", regions[0].text)
        assertEquals(0.98f, regions[0].confidence)
    }

    @Test
    fun `reads the data envelope and honours the tile offset`() {
        val raw = """
            {"results":[{"data":[
              {"text":"HELLO, SEINA.","confidence":0.99,"text_region":[[10,20],[180,20],[180,60],[10,60]]}
            ]}]}
        """.trimIndent()
        val regions = PaddleOcrParser.parse(raw, offsetX = 0, offsetY = 5000)
        assertEquals(1, regions.size)
        assertEquals(RectI(10, 5020, 180, 5060), regions[0].box)
    }

    @Test
    fun `skips blank lines and broken boxes`() {
        val raw = """
            {"results":[[
              {"text":"   ","confidence":0.5,"text_region":[[1,1],[2,1],[2,2],[1,2]]},
              {"text":"OK","confidence":0.9,"text_region":[]},
              {"text":"REAL LINE","confidence":0.9,"text_region":[[5,5],[50,5],[50,25],[5,25]]}
            ]]}
        """.trimIndent()
        val regions = PaddleOcrParser.parse(raw)
        assertEquals(1, regions.size)
        assertEquals("REAL LINE", regions[0].text)
    }

    @Test
    fun `a non json answer yields nothing instead of throwing`() {
        assertTrue(PaddleOcrParser.parse("<html>502 bad gateway</html>").isEmpty())
    }

    @Test
    fun `reads the PaddleOCR 3 parallel arrays`() {
        val raw = """
            {"input_path":"page.jpg","page_index":null,
             "rec_texts":["AMEGASCANS","145","WH-WHAT'S"],
             "rec_scores":[0.956,0.999,0.988],
             "rec_polys":[[[170,86],[546,86],[546,134],[170,134]],
                          [[521,319],[604,319],[604,370],[521,370]],
                          [[210,5644],[346,5644],[346,5671],[210,5671]]],
             "rec_boxes":[[170,86,546,134],[521,319,604,370],[210,5644,346,5671]]}
        """.trimIndent()
        val regions = PaddleOcrParser.parse(raw, offsetY = 1000)
        assertEquals(3, regions.size)
        assertEquals(RectI(170, 1086, 546, 1134), regions[0].box)
        assertEquals("145", regions[1].text)
        assertEquals("WH-WHAT'S", regions[2].text)
        assertEquals(0.988f, regions[2].confidence)
    }

    @Test
    fun `falls back to rec_boxes when polygons are missing`() {
        val raw = """
            {"rec_texts":["HELLO, SEINA."],"rec_scores":[0.99],"rec_boxes":[[10,20,180,60]]}
        """.trimIndent()
        val regions = PaddleOcrParser.parse(raw, offsetX = 4, offsetY = 0)
        assertEquals(1, regions.size)
        assertEquals(RectI(14, 20, 184, 60), regions[0].box)
    }

    @Test
    fun `reads the PaddleX serving envelope`() {
        val raw = """
            {"result":{"rec_texts":["AND HE WANTED TO"],"rec_scores":[0.95],
                       "rec_polys":[[[206,13284],[520,13284],[520,13319],[206,13319]]]}}
        """.trimIndent()
        val regions = PaddleOcrParser.parse(raw)
        assertEquals(1, regions.size)
        assertEquals(RectI(206, 13284, 520, 13319), regions[0].box)
        assertEquals("AND HE WANTED TO", regions[0].text)
    }
}
