package io.janstenpickle.trace4cats.sttp.client

import cats.data.Kleisli
import cats.effect.IO
import cats.{~>, Id}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.sttp.client.Instances._
import io.janstenpickle.trace4cats.sttp.client.syntax._

class TracedBackendSpec
    extends BaseBackendTracerSpec[IO, Kleisli[IO, Span[IO], *], Span[IO]](
      λ[IO ~> Id](_.unsafeRunSync()),
      identity,
      _.liftTrace()
    )
