package porterie.syntax

import porterie.data.BaseUri
import porterie.internal.LiteralMacro

import scala.language.experimental.macros

trait LiteralSyntax {
  implicit final def porterieSyntaxLiterals(sc: StringContext): LiteralOps =
    new LiteralOps(sc)
}

final class LiteralOps(val sc: StringContext) extends AnyVal {
  def http(args: Any*): BaseUri =
    macro LiteralMacro.httpBaseUri.make

  def https(args: Any*): BaseUri =
    macro LiteralMacro.httpsBaseUri.make
}
