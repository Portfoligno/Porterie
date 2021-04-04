package porterie.internal

import cats.arrow.Compose
import cats.data.{Cokleisli, NonEmptyList}
import cats.instances.all._
import cats.{FlatMap, Id}
import com.comcast.ip4s.{IpAddress, Ipv4Address, Ipv6Address, SocketAddress}
import org.http4s.Request.Connection
import org.http4s.Uri.Scheme.{http, https}
import org.http4s.headers.Forwarded.Node.{Name, Port}
import org.http4s.headers.Forwarded.{Element, Node}
import org.http4s.headers.{Forwarded, Host}
import org.http4s.{Header, Headers, Uri}
import org.typelevel.ci._

import java.lang.IllegalStateException
import scala.math.floorMod

private[porterie]
object ForwardedHeaders {
  private val render = (Header[Forwarded].value _).compose(Forwarded.apply)

  private val forwardedNode: SocketAddress[IpAddress] => Node = {
    case SocketAddress(host: Ipv4Address, port) => Node(Name.Ipv4(host), Port.Numeric(port.value))
    case SocketAddress(host: Ipv6Address, port) => Node(Name.Ipv6(host), Port.Numeric(port.value))
  }
  private val forwardedHost: Host => Forwarded.Host = {
    case Host(host, None) => Forwarded.Host.ofHost(Uri.RegName(host))
    case Host(host, Some(port)) => Forwarded.Host
      .fromHostAndPort(Uri.RegName(host), floorMod(port, 65536))
      .getOrElse(throw new IllegalStateException(port.toString)) // Should not happen
  }
  private val forUnknown = Element.fromFor(Node(Name.Unknown))

  def prependElement(headers: Headers, connection: Option[Connection]): List[Header.Raw] = {
    val forwardedPairs = Seq[Option[Option[Element] => Element]](
      connection.map {
        case Connection(local, remote, secure) =>
          _
            .fold(Element.fromBy _)(_.withBy)(forwardedNode(local))
            .withFor(forwardedNode(remote))
            .withProto(if (!secure) http else https)
      },
      headers.get[Host].map(
        host =>
          _.fold(Element.fromHost _)(_.withHost)(forwardedHost(host))
      )
    )
    val newElement =
      Compose[({ type L[A, B] = Cokleisli[Option, A, B] })#L]
        .algebra[Element]
        .combineAllOption(forwardedPairs.flatten map Cokleisli.apply)
        .fold(forUnknown)(_ run None)

    val (prefix, (forwarded, suffix)) =
      FlatMap[Id].tailRecM(List.empty[Header.Raw] -> headers.headers) {
        case (prefix, Nil) =>
          Right(prefix -> (None -> Nil))

        case (prefix, suffix @ Header.Raw(ci"Forwarded", value) :: tail) =>
          Right(prefix -> Forwarded.parse(value).fold(
            _ => None -> suffix,
            forwarded => Some(forwarded.values) -> tail
          ))

        case (prefix, h :: tail) =>
          Left((h :: prefix) -> tail)
      }

    val combinedElements =
      Header.Raw(ci"Forwarded", render(forwarded.fold(NonEmptyList.one(newElement))(newElement :: _)))

    prefix reverse_::: combinedElements :: suffix
  }
}
