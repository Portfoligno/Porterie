package porterie.internal

import cats.Show
import cats.instances.all._
import cats.syntax.alternative._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.semigroupk._
import com.comcast.ip4s.IpAddress
import org.http4s.Header.Raw
import org.http4s.Request.Connection
import org.http4s.Uri.Scheme.{http, https}
import org.http4s.headers.Forwarded.Node.Name.{Ipv4, Ipv6, Unknown}
import org.http4s.headers.Forwarded.Node.{Name, Obfuscated}
import org.http4s.headers.{Forwarded, Host}
import org.http4s.{Header, Headers}
import org.typelevel.ci._

import scala.collection.View
import scala.collection.immutable.ListSet

private[porterie]
object XForwardedHeaders {
  private val ipAddressToString: Show[IpAddress] = _.toString // No brackets in X-Forwarded headers

  private val nodeNameString: Name => Option[String] = {
    case Ipv4(address) => Some(ipAddressToString show address)
    case Ipv6(address) => Some(ipAddressToString show address)
    case Unknown => None
    case _: Obfuscated => None
  }

  private val xForwardedHeaderNames = ListSet(
    ci"X-Forwarded-By",
    ci"X-Forwarded-For",
    ci"X-Forwarded-Proto",
    ci"X-Forwarded-Host",
    ci"X-Forwarded-Port"
  )

  def prependElements(headers: Headers, connection: Option[Connection]): List[Header.Raw] = {
    val hostHeader = headers.get[Host]

    val newElements = xForwardedHeaderNames.view zip Seq(
      connection.fold("unknown")(ipAddressToString show _.local.host),
      connection.fold("unknown")(ipAddressToString show _.remote.host),
      connection.fold("unknown")(c => if (!c.secure) "http" else "https"),
      hostHeader.fold("unknown")(_.host),
      (
        (hostHeader >>= (_.port)) <+> connection.map(c => if (!c.secure) 80 else 443)
      )
        .fold("unknown")(Show[Int].show)
    )

    val (others, candidates) = headers.headers.partitionMap {
      case Raw(ci"Forwarded", value) =>
        Forwarded.parse(value).bimap(_ => Raw(ci"X-Porterie-Bad-Forwarded", value), _.values.toList.asLeft)

      case Raw(name, value) if xForwardedHeaderNames(name) =>
        Right(Right(name -> value))

      case header =>
        Left(header)
    }
    val (forwardedHeaders, oldElements) = candidates.separateFoldable

    val convertedElements = forwardedHeaders.view.flatten.flatMap(e => Seq(
      ci"X-Forwarded-By" ->
        (e.maybeBy >>= (nodeNameString apply _.nodeName)).getOrElse("unknown"),
      ci"X-Forwarded-For" ->
        (e.maybeFor >>= (nodeNameString apply _.nodeName)).getOrElse("unknown"),
      ci"X-Forwarded-Proto" ->
        e.maybeProto
          .collect {
            case `http` => "http"
            case `https` => "https"
          }
          .getOrElse("unknown"),
      ci"X-Forwarded-Host" ->
        e.maybeHost.fold("unknown")(_.host.value),
      ci"X-Forwarded-Port" ->
        (
          (e.maybeHost >>= (_.port)) <+> e.maybeProto.collect {
            case `http` => 80
            case `https` => 443
          }
        )
          .fold("unknown")(Show[Int].show)
    ))

    others ++ (newElements ++ convertedElements ++ oldElements).groupMap(_._1)(_._2).view.map {
      case (name, values) =>
        new Header.Raw(name, (values: View[String]).mkString(","))
    }
  }
}
