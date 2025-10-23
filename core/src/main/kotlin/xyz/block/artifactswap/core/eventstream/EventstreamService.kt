package xyz.block.artifactswap.core.eventstream

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

public interface EventstreamService {
  @Headers("X-Square-Gzip: true")
  @POST("/2.0/log/eventstream")
  public fun logEvents(
    @Body request: LogEventStreamV2Request
  ): Call<LogEventStreamV2Response>
}
