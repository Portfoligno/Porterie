package porterie.internal

import org.http4s.Uri.Scheme.{http, https}
import org.http4s.Uri.{Ipv4Address, Ipv6Address, RegName, Scheme}
import org.http4s.util.Renderer.renderString
import org.typelevel.literally.Literally
import porterie.data.BaseUri

object LiteralMacro {
  object httpBaseUri extends LiterallyBaseUri(http)
  object httpsBaseUri extends LiterallyBaseUri(https)

  private[internal]
  class LiterallyBaseUri(scheme: Scheme) extends Literally[BaseUri] {
    override
    def validate(c: Context)(s: String): Either[String, c.Expr[BaseUri]] = {
      import c.universe._

      def tree[A](oa: Option[A])(f: A => Tree): Tree =
        oa.fold[Tree](q"_root_.scala.None")(a => q"_root_.scala.Some(${f(a)})")

      BaseUri.fromString(scheme, s) match {
        case None =>
          Left("Invalid hierarchical part")
        case Some(uri) =>
          Right(c.Expr(q"""
            _root_.porterie.data.BaseUri(
              _root_.org.http4s.Uri.Scheme.unsafeFromString(${scheme.value}),
              _root_.org.http4s.Uri.Authority(
                ${tree(uri.authority.userInfo)(u => q"""
                  _root_.org.http4s.Uri.UserInfo.fromString(${renderString(u)}).fold(
                    throw _, _root_.scala.Predef.identity
                  )
                """)},
                ${uri.authority.host match {
                  case h: Ipv4Address => q"_root_.org.http4s.Uri.Ipv4Address.unsafeFromString(${h.value})"
                  case h: Ipv6Address => q"_root_.org.http4s.Uri.Ipv6Address.unsafeFromString(${h.value})"
                  case h: RegName => q"_root_.org.http4s.Uri.RegName(${h.value})"
                }},
                ${tree(uri.authority.port)(p => q"$p")}
              ),
              _root_.org.http4s.Uri.Path.unsafeFromString(${uri.path.renderString})
            )
          """))
      }
    }

    def make(c: Context)(args: c.Expr[Any]*): c.Expr[BaseUri] =
      apply(c)(args: _*)
  }
}
