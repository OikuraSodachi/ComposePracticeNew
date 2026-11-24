package com.todokanai.composepracticenew.model

/**
 * 압축 파일 안의 항목 하나. 내용을 풀지 않고 읽은 목록 정보이며, 압축 파일 안의 경로는 갖지 않는다 — 위치는 폴더 트리([ArchiveFolder])가 나타낸다.
 */
sealed interface ArchiveEntry : SortableEntry {

    /** 이 항목을 가리키는 키. 이름이 같은 항목끼리도 다르다. */
    val key: ArchiveEntryKey

    /**
     * 폴더. 폴더 항목이 없는 형식은 안쪽 항목의 경로로 만든 폴더이며, 그때 수정 시각이 없다.
     * @param name 폴더 이름
     * @param lastModifiedMillis 수정 시각. 모르면 null
     * @param contents 바로 아래 항목들
     * @param key 루트부터의 폴더 이름으로 만든 키
     */
    data class Folder(override val name: String, override val lastModifiedMillis: Long?, val contents: ArchiveFolder, override val key: ArchiveEntryKey.Folder) : ArchiveEntry {
        override val isDirectory: Boolean get() = true
        override val sizeBytes: Long? get() = null
    }

    /**
     * 파일.
     * @param name 파일 이름
     * @param sizeBytes 압축 해제 후 크기. 압축 파일이 알려주지 않으면 null
     * @param lastModifiedMillis 수정 시각. 모르면 null
     * @param isEncrypted 내용이 암호화된 파일이면 true
     * @param key 압축 파일 항목 목록에서의 순번으로 만든 키
     */
    data class File(override val name: String, override val sizeBytes: Long?, override val lastModifiedMillis: Long?, val isEncrypted: Boolean, override val key: ArchiveEntryKey.Item) : ArchiveEntry {
        override val isDirectory: Boolean get() = false
    }

    /**
     * 링크(심볼릭·하드 링크). 압축 해제는 만들지 않고 실패로 알린다(지원 범위 밖).
     * @param name 링크 이름
     * @param lastModifiedMillis 수정 시각. 모르면 null
     * @param key 압축 파일 항목 목록에서의 순번으로 만든 키
     */
    data class Link(override val name: String, override val lastModifiedMillis: Long?, override val key: ArchiveEntryKey.Item) : ArchiveEntry {
        override val isDirectory: Boolean get() = false
        override val sizeBytes: Long? get() = null
    }

    /**
     * 압축 해제 위치 밖을 가리키는 경로(`..`·절대 경로)의 항목. 압축 해제는 풀지 않고 실패로 알린다. 숨기지 않고 루트에 원래 경로로 보인다.
     * @param name 압축 파일에 적힌 원래 경로
     * @param lastModifiedMillis 수정 시각. 모르면 null
     * @param key 압축 파일 항목 목록에서의 순번으로 만든 키
     */
    data class UnsafePath(override val name: String, override val lastModifiedMillis: Long?, override val key: ArchiveEntryKey.Item) : ArchiveEntry {
        override val isDirectory: Boolean get() = false
        override val sizeBytes: Long? get() = null
    }
}

/**
 * 압축 파일 안 항목 하나를 가리키는 키. 압축 파일에는 이름이 같은 파일이 여럿 있을 수 있어 이름으로는 항목을 가리킬 수 없다.
 * 한 번 읽은 목록 안에서만 뜻이 있다 — 압축 파일이 바뀌면 같은 키가 다른 항목을 가리킬 수 있다.
 */
sealed interface ArchiveEntryKey {

    /**
     * 압축 파일에 담긴 항목(파일·링크·풀 수 없는 경로).
     * @param index 압축 파일 항목 목록에서의 순번(0부터, 담긴 순서)
     */
    data class Item(val index: Int) : ArchiveEntryKey

    /**
     * 폴더. 폴더 항목이 없는 형식은 경로로 폴더를 만들므로 순번이 아니라 루트부터의 이름으로 가리킨다 — 같은 이름의 폴더는 합쳐 있어 이름 경로마다 하나다.
     * 바깥 폴더의 키를 이어 붙여 만든다 — 폴더마다 루트부터의 이름 목록을 따로 두면 깊이 중첩된 압축 파일에서 메모리가 깊이의 제곱으로 늘기 때문이다.
     * 같음 비교는 이름 경로로 하며, 재귀하지 않는다(깊이 중첩된 폴더에서 스택이 넘치지 않도록).
     * @param parent 바깥 폴더의 키. 루트 바로 아래 폴더면 null
     * @param name 폴더 이름
     */
    class Folder(val parent: Folder?, val name: String) : ArchiveEntryKey {

        /** 루트부터의 깊이(루트 바로 아래 폴더가 1). */
        private val depth: Int = (parent?.depth ?: 0) + 1

        /** 이름 경로로 만든 해시. 만들 때 한 번 계산해 같음 비교와 해시가 바깥 폴더를 다시 훑지 않게 한다. */
        private val hash: Int = 31 * (parent?.hash ?: 0) + name.hashCode()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Folder || hash != other.hash || depth != other.depth) return false
            var a: Folder? = this
            var b: Folder? = other
            while (a != null && b != null) {
                if (a === b) return true
                if (a.name != b.name) return false
                a = a.parent
                b = b.parent
            }
            return true
        }

        override fun hashCode(): Int = hash

        override fun toString(): String = "Folder(depth=$depth, name=$name)"
    }
}

/**
 * 압축 파일 안 폴더 하나의 바로 아래 항목들. 루트도 이 타입이다.
 * data class가 아니다 — 같음 비교가 트리 전체를 재귀로 비교하면 깊이 중첩된 압축 파일에서 스택이 넘친다. 같은 인스턴스만 같다.
 * @param entries 바로 아래 항목들(압축 파일에 담긴 순서). 같은 이름의 폴더는 하나로 합쳐 있고, 폴더가 아닌 항목은 이름이 같아도 모두 있다
 */
class ArchiveFolder(val entries: List<ArchiveEntry>) {

    /**
     * 이 폴더에서 [names]의 폴더를 차례로 들어간 폴더를 찾는다.
     * @param names 들어갈 폴더 이름들(바깥부터)
     * @return 찾은 폴더. 이름이 가리키는 폴더가 없으면 null
     */
    fun folderAt(names: List<String>): ArchiveFolder? =
        names.fold(this as ArchiveFolder?) { folder, name -> folder?.childFolder(name) }

    /**
     * 바로 아래 폴더 중 이름이 [name]인 것을 찾는다.
     * @param name 폴더 이름
     * @return 폴더의 항목들. 없으면 null
     */
    private fun childFolder(name: String): ArchiveFolder? =
        entries.firstNotNullOfOrNull { entry -> (entry as? ArchiveEntry.Folder)?.takeIf { it.name == name }?.contents }
}
