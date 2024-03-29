package example

import cats.effect._
import cats.mtl.Local
import cats.syntax.functor._
import fs2.grpc.syntax.all._
import org.typelevel.otel4s.instances.local._
import org.typelevel.otel4s.oteljava.context.Context
import org.typelevel.otel4s.oteljava.OtelJava
import io.opentelemetry.api.GlobalOpenTelemetry
import io.grpc.ServerServiceDefinition
import io.grpc.Metadata
import helloworld.helloworld.GreeterFs2Grpc
import org.typelevel.otel4s.trace.Tracer
import cats.effect.kernel.syntax.resource
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import io.grpc.Server
import io.grpc.ServerInterceptor
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry
import java.util.concurrent.ConcurrentHashMap
import scala.collection.concurrent.TrieMap

/** Attempts that were made:
  *   1. Updated IOLocal on line 65 - extracting the traceparent and doing an
  *      update if one is found 2. Attempted to lift the Context out of the
  *      (Test)Interceptor - still prefered but I'm not sure how to pass to the
  *      handler of the server call. Best I could do is leverage strategy 1 with
  *      a concurrent map that generates a unique identifier per request which
  *      is injected into the header, this header is fetched and applied on line
  *      58. Not super clean but it appears to work. 3. Seems like
  *      TraceMapPropigator is the way to go if I don't want to leverage the
  *      GRPC interceptor otel provides and wish to extract the headers manually
  *      within the interceptor. Doable but not ideal as this might deviate from
  *      how the people in general expect so see in a GRPC trace (attributes and
  *      such). I can attempt to write this if there is no recorse with the
  *      TestInterceptor
  */
object Main extends IOApp {

  /** Execute the test by running the following command grpcurl -plaintext -d
    * '{"name":"bob"}' -H "traceparent:
    * 00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01" 0.0.0.0:7170
    * helloworld.Greeter/SayHello
    *
    * Traces should be logged - just ignore the server reflection one - that is
    * a function of how grpcurl works
    * @param args
    * @return
    */
  override def run(args: List[String]): IO[ExitCode] =
    IO.println("Program Starting....") *>
      service
        .flatMap(binding)
        .evalMap(server => IO(server.start()))
        .useForever
        .as(ExitCode.Success)

  def createOtel4s[F[_]: Async](implicit L: Local[F, Context]): F[OtelJava[F]] =
    Async[F].delay(GlobalOpenTelemetry.get).map(OtelJava.local[F])

  val service: Resource[IO, Settings] = {
    val serverImpl = for {
      implicit0(ioLocal: IOLocal[Context]) <- IOLocal(Context.root)
      otel <- createOtel4s[IO]
      implicit0(tracer: Tracer[IO]) <- otel.tracerProvider.get("hello.world")
    } yield (new HelloGrpcServer[IO](), otel, ioLocal)

    Resource.eval(serverImpl).flatMap { case (server, otel, ioLocal) =>
      val hashMap = new TrieMap[String, Context]()
      GreeterFs2Grpc
        .serviceResource[IO, Metadata](
          server,
          { metadata =>
            ioLocal.get.map(println) *>
              ioLocal.update { original =>
                Option(metadata.get(TestInterceptor.key))
                  .flatMap { id =>
                    hashMap.get(id)
                  }
                  .getOrElse(original)
              } *>
              ioLocal.get.map(println) *> IO.pure(metadata)
          }
        )
        .map {
          Settings(
            _,
            List(
              GrpcTelemetry.create(otel.underlying).newServerInterceptor(),
              new TestInterceptor(hashMap)
            )
          )
        }
    }
  }

  def binding(serviceDef: Settings): Resource[IO, Server] = {
    serviceDef.interceptors
      .foldRight(
        NettyServerBuilder
          .forPort(7170)
          .addService(serviceDef.serviceDef)
          .addService(ProtoReflectionService.newInstance())
      ) { (interceptor, builder) =>
        builder.intercept(interceptor)
      }
      .resource[IO]
      .evalTap(_ => IO.println("Server started on port 7170"))

  }

  case class Settings(
      serviceDef: ServerServiceDefinition,
      interceptors: List[ServerInterceptor]
  )
}
