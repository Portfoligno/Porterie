
import cats.Applicative
import cats.data.Kleisli
import org.http4s.Request.Connection
import org.http4s.Request.Keys.ConnectionInfo
import org.http4s.{Header, Headers, Request, Uri}
import porterie.internal.{ForwardedHeaders, XForwardedHeaders}

package object porterie {
  private
  def convertUriAndHeaders[F[_] : Applicative](
    uriConversion: Uri => Uri,
    headersConversion: (Headers, Option[Connection]) => List[Header.Raw]
  ): Kleisli[F, Request[F], Request[F]] =
    Kleisli.fromFunction(
      r => r
        .withUri(uriConversion(r.uri))
        .withHeaders(new Headers(
          headersConversion(r.headers, r.attributes lookup ConnectionInfo)
        ))
    )

  def xForwarded[F[_] : Applicative](uriConversion: Uri => Uri): Kleisli[F, Request[F], Request[F]] =
    convertUriAndHeaders(uriConversion, XForwardedHeaders.appendElements)

  def forwarded[F[_] : Applicative](uriConversion: Uri => Uri): Kleisli[F, Request[F], Request[F]] =
    convertUriAndHeaders(uriConversion, ForwardedHeaders.appendElement)
}
