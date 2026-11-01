package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.SearchQuery
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

/** [SearchQuery]가 이름에 들어 있는 검색어를 대소문자·한글 조합 방식과 무관하게 찾고, 빈 검색어는 만들지 않는지 검증하는 단위 테스트. */
class SearchQueryTest {

    @Test
    fun `이름에 검색어가 들어 있으면 대소문자와 무관하게 맞는다`() {
        val query = SearchQuery.of("Rep")!!

        assertTrue(query.matches("report.PDF"))
        assertTrue(query.matches("my_REPORT"))
        assertFalse(query.matches("rp.txt"))
    }

    @Test
    fun `조합형으로 저장된 한글 이름도 완성형 검색어와 맞는다`() {
        val decomposedName = Normalizer.normalize("사진 모음", Normalizer.Form.NFD)

        assertTrue(SearchQuery.of("사진")!!.matches(decomposedName))
    }

    @Test
    fun `조합형 검색어도 완성형으로 저장된 이름과 맞는다`() {
        val decomposedQuery = Normalizer.normalize("사진", Normalizer.Form.NFD)

        assertTrue(SearchQuery.of(decomposedQuery)!!.matches("사진 모음"))
    }

    @Test
    fun `비어 있거나 공백뿐인 검색어는 만들지 않는다`() {
        assertNull(SearchQuery.of(""))
        assertNull(SearchQuery.of("   "))
    }
}
