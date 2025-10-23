package xyz.block.artifactswap.core.network

import xyz.block.artifactswap.core.maven.Metadata
import xyz.block.artifactswap.core.maven.Project
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.PUT
import retrofit2.http.Path

interface ArtifactoryEndpoints {

    @GET("{repo}/com/squareup/register/sandbags/{artifact}/maven-metadata.xml")
    suspend fun getMavenMetadata(
        @Path("repo") repo: String,
        @Path("artifact") artifact: String
    ): Response<Metadata>

    @GET("{repo}/com/squareup/register/sandbags/{artifact}/{version}/{artifact}-{version}.pom")
    suspend fun getPom(
        @Path("repo") repo: String,
        @Path("artifact") artifact: String,
        @Path("version") version: String
    ): Response<Project>

    @HEAD("{repo}/com/squareup/register/sandbags/{artifact}/{version}/{artifact}-{version}{packaging}")
    suspend fun headArtifact(
        @Path("repo") repo: String,
        @Path("artifact") artifact: String,
        @Path("version") version: String,
        @Path("packaging") packaging: String,
    ): Response<Void>

    @PUT("{repo}/com/squareup/register/sandbags/{artifact}/maven-metadata.xml")
    suspend fun pushMetadata(
        @Path("repo") repo: String,
        @Path("artifact") artifact: String,
        @Body metadata: Metadata
    ): Response<Unit>

    @PUT("{repo}/com/squareup/register/sandbags/{artifact}/{version}/{filename}")
    suspend fun pushPom(
        @Path("repo") repo: String,
        @Path("artifact") artifact: String,
        @Path("version") version: String,
        @Path("filename") filename: String,
        @Body project: Project
    ): Response<Unit>

    @GET("{repo}/{group}/{artifact}/{version}/{artifact}-{version}{ext}")
    suspend fun getFile(
        @Path("repo") repo: String,
        @Path("group") group: String,
        @Path("artifact") artifact: String,
        @Path("version") version: String,
        @Path("ext") ext: String
    ): Response<ResponseBody>
}
