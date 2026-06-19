package com.creatorcontenthub.domain.model

data class MediaNavigationContextVersion(
    val versionId: String,
    val contextId: String,
    val versionNumber: Int,
    val createdAt: Long,
    val mediaRanges: List<MediaRange>
) {

    init {

        require(versionId.isNotBlank()) {
            "versionId must not be blank"
        }

        require(contextId.isNotBlank()) {
            "contextId must not be blank"
        }

        require(versionNumber > 0) {
            "versionNumber must be > 0"
        }

        require(mediaRanges.isNotEmpty()) {
            "mediaRanges must not be empty"
        }

        validateOrderedRanges(mediaRanges)
        validateNonOverlappingRanges(mediaRanges)
    }

    private fun validateOrderedRanges(
        ranges: List<MediaRange>
    ) {

        for (index in 1 until ranges.size) {

            require(
                ranges[index - 1].startTimeMs <=
                        ranges[index].startTimeMs
            ) {
                "mediaRanges must be ordered by startTimeMs"
            }
        }
    }

    private fun validateNonOverlappingRanges(
        ranges: List<MediaRange>
    ) {

        for (index in 1 until ranges.size) {

            val previous =
                ranges[index - 1]

            val current =
                ranges[index]

            require(
                previous.endTimeMs <=
                        current.startTimeMs
            ) {
                "mediaRanges must not overlap"
            }
        }
    }
}
