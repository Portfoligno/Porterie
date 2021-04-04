package porterie

import org.http4s.Uri
import org.http4s.headers.{Forwarded, Host}

package object internal {
  private[internal]
  val forwardedHost: Host => Option[Forwarded.Host] = {
    case Host(host, None) => Some(Forwarded.Host.ofHost(Uri.RegName(host)))
    case Host(host, Some(port)) => Forwarded.Host.fromHostAndPort(Uri.RegName(host), port).toOption
  }
}
