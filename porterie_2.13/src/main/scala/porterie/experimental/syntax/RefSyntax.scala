package porterie.experimental.syntax

import cats.FlatMap
import cats.effect.Ref
import cats.syntax.flatMap._
import cats.syntax.functor._

trait RefSyntax {
  implicit final def porterieSyntaxRef[F[_], A](ref: Ref[F, A]): RefOps[F, A] =
    new RefOps(ref)
}

final class RefOps[F[_], A](private val ref: Ref[F, A]) extends AnyVal {
  def weakGetAndUpdate(f: A => A)(implicit F: FlatMap[F]): F[A] =
    ref.access >>= {
      case (state, update) => update(f(state)).as(state)
    }
}
