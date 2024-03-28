package example

import cats.effect.kernel.Resource
import io.grpc.Metadata
import helloworld.helloworld.GreeterFs2Grpc
import helloworld.helloworld.{HelloReply, HelloRequest}
import org.typelevel.otel4s.trace.Tracer
import cats.effect.std.Console
import cats.Monad
import cats.syntax.all._

class HelloGrpcServer[F[_]: Tracer: Console: Monad]
    extends GreeterFs2Grpc[F, Metadata] {
  override def sayHello(request: HelloRequest, ctx: Metadata): F[HelloReply] =
    Tracer[F].span("sayHello").surround {
      Console[F].println(s"Received: ${request.name}") *>
        Monad[F].pure(HelloReply(s"Hello, ${request.name}"))
    }
}
