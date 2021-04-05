package porterie.internal

import cats.data.NonEmptyList
import cats.instances.all._
import cats.syntax.compose._
import cats.syntax.flatMap._
import cats.syntax.option._
import cats.{FlatMap, Id}
import com.comcast.ip4s.{IpAddress, Ipv4Address, Ipv6Address, SocketAddress}
import org.http4s.Request.Connection
import org.http4s.Uri.Scheme.{http, https}
import org.http4s.headers.Forwarded.Node.{Name, Port}
import org.http4s.headers.Forwarded.{Element, Node}
import org.http4s.headers.{Forwarded, Host}
import org.http4s.{Header, Headers}
import org.typelevel.ci._

private[porterie]
object ForwardedHeaders {
  private val render = Header[Forwarded].value _ <<< Forwarded.apply

  private val forwardedNode: SocketAddress[IpAddress] => Node = {
    case SocketAddress(host: Ipv4Address, port) => Node(Name.Ipv4(host), Port.Numeric(port.value))
    case SocketAddress(host: Ipv6Address, port) => Node(Name.Ipv6(host), Port.Numeric(port.value))
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
      (headers.get[Host] >>= forwardedHost).map(
        host =>
          _.fold(Element.fromHost _)(_.withHost)(host)
      )
    )
    val newElement =
      forwardedPairs.flatten.reduceOption(_ >>> (_.some) >>> _).fold(forUnknown)(_(None))

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
