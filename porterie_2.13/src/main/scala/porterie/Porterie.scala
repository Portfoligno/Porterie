package porterie

import cats.Applicative
import cats.data.Kleisli
import cats.effect.Async
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.{Request, Response, Uri}

import scala.concurrent.ExecutionContext

object Porterie {
  def apply[F[_]](
    port: Int,
    requestMutator: Kleisli[F, Request[F], Request[F]],
    responseMutator: Kleisli[F, Response[F], Response[F]]
  ) = new Porterie[F](
    port = port,
    requestMutator = requestMutator,
    responseMutator = responseMutator
  )

  def apply[F[_] : Applicative](
    port: Int,
    requestMutator: Kleisli[F, Request[F], Request[F]],
  ) = new Porterie[F](
    port = port,
    requestMutator = requestMutator,
    responseMutator = Kleisli.ask
  )

  def apply[F[_] : Applicative](
    port: Int,
    targetUri: Uri
  ) = new Porterie[F](
    port = port,
    requestMutator = xForwarded(targetUri),
    responseMutator = Kleisli.ask
  )
}

final class Porterie[F[_]](
  port: Int,
  requestMutator: Kleisli[F, Request[F], Request[F]],
  responseMutator: Kleisli[F, Response[F], Response[F]]
) {
  def start(executionContext: ExecutionContext)(implicit F: Async[F]): F[Nothing] =
    BlazeClientBuilder[F](executionContext)
      .resource
      .flatMap(client =>
        BlazeServerBuilder[F](executionContext)
          .bindHttp(port, "0.0.0.0")
          .withHttpApp(client
            .toHttpApp
            .compose(requestMutator)
            .andThen(responseMutator))
          .resource
      )
      .useForever
}
