package com.calendarmods

/** User-owned membership and order; the catalog itself is independent of installation. */
internal data class ThemeLibrary(
    val order: List<String> = CalendarTheme.entries.map { it.id },
    val addedAt: Map<String, Long> = order.associateWith { 0L },
    val favorites: Set<String> = emptySet(),
    val resetStyles: Set<String> = emptySet(),
) {
    init {
        require(order.isNotEmpty() && order.distinct().size == order.size)
        require(addedAt.keys == order.toSet())
    }

    fun add(id: String, now: Long): ThemeLibrary = if (id in order) this else
        copy(order = order + id, addedAt = addedAt + (id to now), resetStyles = resetStyles - id)

    fun remove(id: String): ThemeLibrary {
        if (id !in order) return this
        require(order.size > 1) { "至少保留一个主题" }
        return copy(order = order - id, addedAt = addedAt - id, resetStyles = resetStyles + id)
    }

    fun favorite(id: String): ThemeLibrary = copy(favorites =
        if (id in favorites) favorites - id else favorites + id)

    /** Explicit values make delayed batches idempotent, including retries after rotation. */
    fun setFavorites(values: Map<String, Boolean>): ThemeLibrary = copy(favorites =
        (favorites - values.filterValues { !it }.keys) + values.filterValues { it }.keys)

    fun reorder(ids: List<String>): ThemeLibrary {
        require(ids.size == order.size && ids.toSet() == order.toSet()) { "主题列表已变化，请重试" }
        return copy(order = ids.toList())
    }

    fun selectionAfterRemoval(selected: String, id: String): String =
        if (selected != id) selected else order.getOrNull(order.indexOf(id) + 1)
            ?: order.getOrNull(order.indexOf(id) - 1) ?: order.first()
}
