package com.teknoral.parametrik.data.remote

import com.teknoral.parametrik.data.remote.dto.Geometry3dDto
import com.teknoral.parametrik.data.remote.dto.JobDetailDto
import com.teknoral.parametrik.data.remote.dto.JobDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming

/**
 * Sunucu sözleşmesi. OkHttp'de yönlendirme takibi kapalı olduğu için tüm
 * uç noktalar ham [Response] döndürür; sonuç `Location` başlığından okunur.
 */
interface ParametricApi {

    @FormUrlEncoded
    @POST("auth/login")
    suspend fun login(
        @Field("email") email: String,
        @Field("password") password: String
    ): Response<ResponseBody>

    @POST("auth/logout")
    suspend fun logout(): Response<ResponseBody>

    /** Oturum testi: 200 → açık, 302 /login → kapalı. */
    @GET("parametric")
    suspend fun sessionProbe(): Response<ResponseBody>

    @Multipart
    @POST("parametric/upload")
    suspend fun uploadMesh(
        @Part("name") name: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<ResponseBody>

    @Multipart
    @POST("parametric/upload-image")
    suspend fun uploadImage(
        @Part("name") name: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("parametric/{jobId}/slice")
    suspend fun slice(
        @Path("jobId") jobId: Long,
        @FieldMap fields: Map<String, String>
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("parametric/{jobId}/slice-image")
    suspend fun sliceImage(
        @Path("jobId") jobId: Long,
        @FieldMap fields: Map<String, String>
    ): Response<ResponseBody>

    @GET("parametric/{jobId}/3d")
    suspend fun geometry(@Path("jobId") jobId: Long): Response<Geometry3dDto>

    @GET("parametric/api/jobs")
    suspend fun jobs(): Response<List<JobDto>>

    @GET("parametric/api/jobs/{jobId}")
    suspend fun job(@Path("jobId") jobId: Long): Response<JobDetailDto>

    @Streaming
    @GET("parametric/{jobId}/dxf")
    suspend fun dxf(@Path("jobId") jobId: Long): Response<ResponseBody>

    @Streaming
    @GET("parametric/{jobId}/zip")
    suspend fun zip(@Path("jobId") jobId: Long): Response<ResponseBody>

    @POST("parametric/{jobId}/delete")
    suspend fun delete(@Path("jobId") jobId: Long): Response<ResponseBody>
}
