function buildAssetCollection(job) {

    return {
        collectionId: null,
        version: 1,
        assets: [
            buildThumbnailAsset(job),
            buildTranscriptAsset(job),
            buildSummaryAsset(job),
            buildAudioAsset(job)
        ]
            .filter(Boolean)
            .map(enrichAssetWithActions)
    };
}

function buildThumbnailAsset(job) {

    if (!job.thumbnailUrl) {
        return null;
    }

    return {
        assetId: "thumbnail",
        type: "thumbnail",
        category: "image",

        downloadable: false,
        viewable: true,
        playable: false,
        editable: false,

        available: true
    };
}

function buildTranscriptAsset(job) {

    if (!job.transcription) {
        return null;
    }

    return {
        assetId: "transcript",
        type: "transcript",
        category: "text",

        downloadable: true,
        viewable: true,
        playable: false,
        editable: false,

        available: true
    };
}

function buildSummaryAsset(job) {

    if (!job.summary) {
        return null;
    }

    return {
        assetId: "summary",
        type: "summary",
        category: "text",

        downloadable: true,
        viewable: true,
        playable: false,
        editable: false,

        available: true
    };
}

function buildAudioAsset(job) {

    if (!job.audioAvailable) {
        return null;
    }

    return {
        assetId: "mp3",
        type: "mp3",
        category: "audio",

        downloadable: true,
        viewable: false,
        playable: true,
        editable: true,

        available: true
    };
}

function enrichAssetWithActions(asset) {

    return {
        ...asset,

        actions:
            window.AssetActionFactory
                .buildActions(asset)
    };
}

window.AssetCollectionFactory = {

    buildAssetCollection
};