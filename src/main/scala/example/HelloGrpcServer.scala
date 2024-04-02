package example

import cats.effect.kernel.Resource
import io.grpc.Metadata
import helloworld.helloworld.GreeterFs2Grpc
import helloworld.helloworld.{HelloReply, HelloRequest}
import org.typelevel.otel4s.trace.Tracer
import cats.effect.std.Console
import cats.Monad
import cats.syntax.all._
import org.typelevel.otel4s.context.propagation.TextMapGetter

class HelloGrpcServer[F[_]: Tracer: Console: Monad]
    extends GreeterFs2Grpc[F, Metadata] {
  override def sayHello(request: HelloRequest, ctx: Metadata): F[HelloReply] =
    Tracer[F].joinOrRoot(ctx) {
      Tracer[F].currentSpanContext.flatTap(ctx =>
        Console[F].println(s"Parent context: $ctx")
      ) >>
        Tracer[F].span("sayHello").surround {
          Console[F].println(s"Received: ${request.name}") *>
            Monad[F].pure(HelloReply(s"Hello, ${request.name}"))
        }
    }

  private implicit val metadataTextMapGetter: TextMapGetter[Metadata] =
    new TextMapGetter[Metadata] {
      import scala.jdk.CollectionConverters._

      def get(carrier: Metadata, key: String): Option[String] =
        Option(
          carrier.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER))
        )

      def keys(carrier: Metadata): Iterable[String] =
        carrier.keys().asScala
    }
}
