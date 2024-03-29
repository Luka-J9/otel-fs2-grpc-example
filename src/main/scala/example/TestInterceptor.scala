package example

import io.grpc.ServerInterceptor
import io.grpc.{Metadata, ServerCall, ServerCallHandler}
import io.grpc.ServerCall.Listener
import org.typelevel.otel4s.oteljava.context.Context
import io.opentelemetry.context.{Context => JContext}

import com.google.rpc.context.AttributeContext.Peer
import java.util.UUID
import scala.collection.concurrent.TrieMap

//Note that this intercetor runs on a different thread then the server call handler
class TestInterceptor(map: TrieMap[String, Context]) extends ServerInterceptor {

  override def interceptCall[ReqT <: Object, RespT <: Object](
      call: ServerCall[ReqT, RespT],
      headers: Metadata,
      next: ServerCallHandler[ReqT, RespT]
  ): Listener[ReqT] = {
    val id = UUID.randomUUID().toString()
    headers.put(TestInterceptor.key, id)

    val delegate = next.startCall(call, headers)
    map.getOrElseUpdate(id, Context.wrap(JContext.current()))

    new Listener[ReqT] {
      override def onMessage(message: ReqT): Unit = {
        delegate.onMessage(message)
      }

      override def onHalfClose(): Unit = {
        delegate.onHalfClose()
      }

      override def onCancel(): Unit = {
        map.remove(id)
        delegate.onCancel()
      }

      override def onComplete(): Unit = {
        map.remove(id)
        delegate.onComplete()
      }

      override def onReady(): Unit = {
        delegate.onReady()
      }
    }
  }

}

object TestInterceptor {
  val key = Metadata.Key.of("CatsContextId", Metadata.ASCII_STRING_MARSHALLER)
}
