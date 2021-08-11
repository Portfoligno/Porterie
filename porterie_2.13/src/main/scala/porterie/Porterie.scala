package porterie

import cats.Applicative
import cats.data.Kleisli
import cats.effect.Async
import cats.syntax.compose._
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.{Request, Response}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, _}

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
  val responseConversion: Kleisli[F, Response[F], Response[F]],
  val proxyTimeout: Duration = 30.seconds
) {
  def withProxyTimeout(proxyTimeout: Duration) =
    new Porterie[F](port, requestConversion, responseConversion, proxyTimeout)

  def start(executionContext: ExecutionContext)(implicit F: Async[F]): F[Nothing] =
    BlazeClientBuilder[F](executionContext)
      .withoutUserAgent
      .withIdleTimeout(30.seconds + proxyTimeout)
      .withRequestTimeout(proxyTimeout)
      .resource
      .flatMap(client =>
        BlazeServerBuilder[F](executionContext)
          .withIdleTimeout(30.seconds + proxyTimeout)
          .withResponseHeaderTimeout(proxyTimeout)
          .bindHttp(port, "0.0.0.0")
          .withHttpApp(
            responseConversion <<< client.toHttpApp <<< requestConversion
          )
          .resource
      )
      .useForever
}
