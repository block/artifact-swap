package xyz.block.artifactswap.core.eventstream

import kotlinx.coroutines.Dispatchers

/** Single reusable fake EventStreamAdapter for all tests that tracks sent events. */
class FakeEventStreamAdapter(catalogName: String = "test") :
  EventStreamAdapter(
    eventstream = Eventstream(FakeEventstreamService()),
    ioDispatcher = Dispatchers.Unconfined,
    catalogName = catalogName,
  ) {

  /** Tracks all events sent through this adapter for test assertions */
  val receivedEvents = mutableListOf<Any>()

  override suspend fun sendEvents(events: List<Any>): Boolean {
    receivedEvents.addAll(events)
    return true
  }
}

private class FakeEventstreamService : EventstreamService {
  override fun logEvents(request: LogEventStreamV2Request) =
    throw UnsupportedOperationException("Not used in tests")
}
