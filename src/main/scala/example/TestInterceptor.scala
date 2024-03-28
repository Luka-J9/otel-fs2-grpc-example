package example

import io.grpc.ServerInterceptor
import io.grpc.{Metadata, ServerCall, ServerCallHandler}
import io.grpc.ServerCall.Listener
import org.typelevel.otel4s.oteljava.context.Context
import io.opentelemetry.context.{Context => JContext}


//Note that this intercetor runs on a different thread then the server call handler
class TestInterceptor extends ServerInterceptor {

  override def interceptCall[ReqT <: Object, RespT <: Object](
      call: ServerCall[ReqT, RespT],
      headers: Metadata,
      next: ServerCallHandler[ReqT, RespT]
  ): Listener[ReqT] = {

    //Just proving that the interceptor from GRPC is producing a context we can access
    println(s"Current Context ${Context.wrap(JContext.current())}")

    next.startCall(call, headers)
  }

}
