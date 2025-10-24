package xyz.block.artifactswap.core.eventstream

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import xyz.block.artifactswap.core.config.EVENTSTREAM_GZIP_HEADER
import xyz.block.artifactswap.core.config.EVENTSTREAM_LOG_EVENTS_PATH

public interface EventstreamService {
  @Headers(EVENTSTREAM_GZIP_HEADER)
  @POST(EVENTSTREAM_LOG_EVENTS_PATH)
  public fun logEvents(
    @Body request: LogEventStreamV2Request
  ): Call<LogEventStreamV2Response>
}
