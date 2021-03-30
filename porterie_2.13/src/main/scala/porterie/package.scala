
import cats.Applicative
import cats.data.Kleisli
import org.http4s.{Header, Headers, Request, Uri}
import org.typelevel.ci._

import scala.collection.View

package object porterie {
  private
  def joinWithCommas(strings: View[String]) = strings.mkString(",")

  private
  def prepended(headers: Headers, key: String, value: String): Header.ToRaw =
    key -> joinWithCommas(
      View(value) ++ headers.get(CIString(key)).view.flatMap(_.iterator).map(_.value)
    )


  def xForwarded[F[_] : Applicative](targetUri: Uri): Kleisli[F, Request[F], Request[F]] =
    Kleisli.fromFunction(r => r
      .withUri(targetUri)
      .putHeaders(
        prepended(r.headers, "X-Forwarded-For", r.remote.fold("unknown")(_.host.toString))
      ))
}
