package porterie.syntax

import cats.syntax.semigroup._
import org.http4s.Uri
import porterie.data.BaseUri

trait UriSyntax {
  implicit final def porterieSyntaxUri(uri: Uri): UriOps = new UriOps(uri)
}

final class UriOps(private val uri: Uri) extends AnyVal {
  def withBaseUri(baseUri: BaseUri): Uri =
    uri.copy(
      scheme = Some(baseUri.scheme),
      authority = Some(baseUri.authority),
      path = baseUri.path |+| uri.path
    )
}
