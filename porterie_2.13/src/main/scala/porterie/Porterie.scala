package porterie

import cats.Applicative
import cats.data.Kleisli
import cats.effect.Async
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.{Request, Response}

import scala.concurrent.ExecutionContext

object Porterie {
  def apply[F[_] : Applicative](
    port: Int,
    requestConversion: Kleisli[F, Request[F], Request[F]],
  ) = new Porterie[F](
    port = port,
    requestConversion = requestConversion,
    responseConversion = Kleisli.ask
  )

  def apply[F[_]](
    port: Int,
    requestConversion: Kleisli[F, Request[F], Request[F]],
    responseConversion: Kleisli[F, Response[F], Response[F]]
  ) = new Porterie[F](
    port = port,
    requestConversion = requestConversion,
    responseConversion = responseConversion
  )
}

final class Porterie[F[_]] private (
  val port: Int,
  val requestConversion: Kleisli[F, Request[F], Request[F]],
  val responseConversion: Kleisli[F, Response[F], Response[F]]
) {
  def start(executionContext: ExecutionContext)(implicit F: Async[F]): F[Nothing] =
    BlazeClientBuilder[F](executionContext)
      .withoutUserAgent
      .resource
      .flatMap(client =>
        BlazeServerBuilder[F](executionContext)
          .bindHttp(port, "0.0.0.0")
          .withHttpApp(client
            .toHttpApp
            .compose(requestConversion)
            .andThen(responseConversion))
          .resource
      )
      .useForever
}
