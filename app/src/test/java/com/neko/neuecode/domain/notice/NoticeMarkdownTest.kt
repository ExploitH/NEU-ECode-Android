package com.neko.neuecode.domain.notice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeMarkdownTest {

    private val sample = """
        # 开屏标题
        ## 二级说明
        这是 **粗体** 和 *斜体*，还有 [官网](https://ecode.exploith.com)。
        - 无序列表甲
        - 无序列表乙
        1. 有序第一步
        2. 有序第二步
        ![示意图](https://ecode.exploith.com/og.png)
    """.trimIndent()

    @Test
    fun parse_keepsPlainParagraphsForOldClients() {
        val blocks = NoticeMarkdown.parse("考试小组件已修复，日期不再带 00:00:00。")
        assertEquals(1, blocks.size)
        val paragraph = blocks.single() as NoticeMarkdown.Block.Paragraph
        assertEquals("考试小组件已修复，日期不再带 00:00:00。", paragraph.plainText)
    }

    @Test
    fun parse_readsHeadingsBoldItalicListsLinksAndImages() {
        val blocks = NoticeMarkdown.parse(sample)
        assertEquals(NoticeMarkdown.Block.Heading(1, "开屏标题"), blocks[0])
        assertEquals(NoticeMarkdown.Block.Heading(2, "二级说明"), blocks[1])

        val paragraph = blocks[2] as NoticeMarkdown.Block.Paragraph
        assertTrue(paragraph.spans.any { it is NoticeMarkdown.Span.Bold && it.text == "粗体" })
        assertTrue(paragraph.spans.any { it is NoticeMarkdown.Span.Italic && it.text == "斜体" })
        assertTrue(
            paragraph.spans.any {
                it is NoticeMarkdown.Span.Link &&
                    it.text == "官网" &&
                    it.url == "https://ecode.exploith.com"
            },
        )

        val bullets = blocks[3] as NoticeMarkdown.Block.BulletList
        assertEquals(listOf("无序列表甲", "无序列表乙"), bullets.items.map { it.plainText })

        val ordered = blocks[4] as NoticeMarkdown.Block.OrderedList
        assertEquals(listOf("有序第一步", "有序第二步"), ordered.items.map { it.plainText })

        val image = blocks[5] as NoticeMarkdown.Block.Image
        assertEquals("示意图", image.alt)
        assertEquals("https://ecode.exploith.com/og.png", image.url)
    }

    @Test
    fun parse_rejectsJavascriptAndNonHttpImages() {
        val blocks = NoticeMarkdown.parse(
            """
            [坏链](javascript:alert(1))
            ![本地](file:///sdcard/x.png)
            [官网](https://ecode.exploith.com)
            """.trimIndent(),
        )
        val paragraph = blocks.filterIsInstance<NoticeMarkdown.Block.Paragraph>().single()
        assertTrue(paragraph.spans.none { it is NoticeMarkdown.Span.Link && it.url.startsWith("javascript:") })
        assertTrue(paragraph.spans.any { it is NoticeMarkdown.Span.Link && it.url.startsWith("https://") })
        assertTrue(blocks.none { it is NoticeMarkdown.Block.Image })
    }

    @Test
    fun parse_headingLevelsStopAtThree() {
        val blocks = NoticeMarkdown.parse("### 三级\n#### 四级当三级")
        assertEquals(NoticeMarkdown.Block.Heading(3, "三级"), blocks[0])
        assertEquals(NoticeMarkdown.Block.Heading(3, "四级当三级"), blocks[1])
    }

    @Test
    fun inboxList_showsTitleOnlyUntilOpened() {
        assertEquals("点击查看详情", NoticeMarkdown.listHint)
        assertTrue(NoticeMarkdown.listShowsTitleOnly)
        assertTrue(NoticeMarkdown.detailRendersRichText)
    }
}
