package com.creatorcontenthub.controller

import com.creatorcontenthub.application.dto.CreateMediaNavigationContextRequest
import com.creatorcontenthub.application.usecase.CreateMediaNavigationContextUseCase
import com.creatorcontenthub.application.usecase.GetMediaNavigationContextByAssetUseCase
import com.creatorcontenthub.application.usecase.GetMediaNavigationContextUseCase
import com.creatorcontenthub.infrastructure.http.respondError
import com.creatorcontenthub.infrastructure.http.respondSuccess
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.routing.*

fun Route.mediaNavigationRoutes(
    getMediaNavigationContextUseCase: GetMediaNavigationContextUseCase,
    getMediaNavigationContextByAssetUseCase: GetMediaNavigationContextByAssetUseCase,
    createMediaNavigationContextUseCase: CreateMediaNavigationContextUseCase
) {

    post("/media-navigation-contexts") {

        val request =
            call.receive<CreateMediaNavigationContextRequest>()

        val response =
            createMediaNavigationContextUseCase.execute(
                request
            )

        call.respondSuccess(response)
    }

    get("/media-navigation-contexts/{contextId}") {

        val contextId =
            call.parameters["contextId"]

        if (contextId.isNullOrBlank()) {

            call.respondError(
                HttpStatusCode.BadRequest,
                "contextId is required"
            )

            return@get
        }

        val response =
            getMediaNavigationContextUseCase.execute(
                contextId
            )

        if (response == null) {

            call.respondError(
                HttpStatusCode.NotFound,
                "Media Navigation Context not found"
            )

            return@get
        }

        call.respondSuccess(response)
    }

    get("/media-navigation-contexts/by-asset/{assetId}") {

        val assetId =
            call.parameters["assetId"]

        if (assetId.isNullOrBlank()) {

            call.respondError(
                HttpStatusCode.BadRequest,
                "assetId is required"
            )

            return@get
        }

        val response =
            getMediaNavigationContextByAssetUseCase.execute(
                assetId
            )

        if (response == null) {

            call.respondError(
                HttpStatusCode.NotFound,
                "Media Navigation Context not found"
            )

            return@get
        }

        call.respondSuccess(
            response
        )
    }
}