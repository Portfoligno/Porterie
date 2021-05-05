package porterie.data

import cats.parse.Parser.{anyChar, char, charIn, not, string, until0}
import cats.syntax.compose._
import cats.syntax.option._
import org.http4s.Uri._
import org.http4s.{Uri, headers}
import porterie.experimental.syntax.semigroupk._

import scala.util.{Success, Try}

final case class BaseUri(
  scheme: Scheme,
  authority: Authority,
  path: Path = Path.empty
) {
  def toUri: Uri = Uri(Some(scheme), Some(authority), path)

  override
  def toString: String = toUri.toString
}

object BaseUri {
  // Implement a best-effort reuse of parsing methods from Http4s for now
  // (it looks like they are planning to expose the parsers)
  private
  val hierarchicalPartParser = {
    // Before '@'
    val userInfo = until0(char('@')).mapFilter(s => UserInfo.fromString(s).toOption) <* char('@')

    // Before '/'
    val ipv6Address =
      // The current host string implementation does not output brackets, though
      char('[').? *> until0(char(']')).mapFilter(s => Ipv6Address.fromString(s).toOption) <* char(']').?

    val host = until0(char('/')).mapFilter(
      // `headers.Host.parse` shares the same parsing rules
      headers.Host.parse _ >>> {
        case Left(_) => None
        case Right(headers.Host(host, port)) =>
          // Parse again because the required type information is lost during the conversion to this `host` string
          // (This may need to be updated if the `IPvFuture` case is implemented somehow)
          val ip = Ipv4Address.fromString(host) orElse ipv6Address.parseAll(host)
          Some(port -> ip.fold(_ => RegName(host), identity))
      }
    )

    // Remainder
    val d = charIn("0123456789ABCDEFabcdef")
    val pathCharacter = (char('%') ~ d ~ d) <+> (not(charIn("\"#%/<>?[\\]^`{|}")).with1 ~ charIn('!' to '~'))
    val segment = pathCharacter.rep0.void

    val path = anyChar.rep0.string.mapFilter(s =>
      // Wrap in a `Try` as if `Path.unsafeFromString` can fail, and then cover the validation of path characters
      Try(Path.unsafeFromString(s)) match {
        case Success(p) if p.segments.forall(s => segment.parseAll(s.encoded).isRight) =>
          Some(p)
        case _ =>
          None
      }
    )

    // Putting together
    (string("://") *> userInfo.backtrack.? ~ host ~ path).map {
      case ((userInfo, (port, host)), path) =>
        BaseUri(_, Authority(userInfo, host, port), path)
    }
  }

  def fromString(scheme: Scheme, s: String): Option[BaseUri] =
    hierarchicalPartParser.parseAll(s).fold(_ => None, _(scheme).some)

  def unsafeFromString(scheme: Scheme, s: String): BaseUri =
    hierarchicalPartParser.parseAll(s).fold(throw new IllegalArgumentException(s), _(scheme))
}
