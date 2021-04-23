package porterie.experimental.syntax

import cats.{Eval, SemigroupK}

private[porterie]
trait SemigroupKSyntax extends SemigroupK.ToSemigroupKOps {
  implicit final def porterieSyntaxSemigroupKNonStrict[F[_] : SemigroupK, A](
    target: F[A]
  ): SemigroupKNonStrictOps[F, A] =
    new SemigroupKNonStrictOps(target)
}

private[porterie]
final class SemigroupKNonStrictOps[F[_], A](private val target: F[A]) extends AnyVal {
  def <+>(y: => F[A])(implicit F: SemigroupK[F]): F[A] =
    F.combineKEval(target, Eval.always(y)).value

  def <+>[B, FB >: F[A]](y: => FB)(implicit F: SemigroupK[F], ev: FB <:< F[B]): F[B] =
    F.combineKEval[B](target, Eval.always(y)).value
}
