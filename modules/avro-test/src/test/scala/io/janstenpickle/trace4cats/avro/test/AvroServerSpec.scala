package io.janstenpickle.trace4cats.avro.test

import cats.data.NonEmptyList
import cats.effect.concurrent.Ref
import cats.effect.{Blocker, IO, Resource}
import cats.implicits._
import cats.kernel.Eq
import io.janstenpickle.trace4cats.avro.AvroSpanCompleter
import io.janstenpickle.trace4cats.avro.server.AvroServer
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceProcess}
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalacheck.Shrink
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class AvroServerSpec extends AnyFlatSpec with ScalaCheckDrivenPropertyChecks with ArbitraryInstances {
  implicit val contextShift = IO.contextShift(ExecutionContext.global)
  implicit val timer = IO.timer(ExecutionContext.global)

  val blocker = Blocker.liftExecutionContext(ExecutionContext.global)

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 1, maxDiscardedFactor = 50.0)

  implicit def noShrink[T]: Shrink[T] = Shrink.shrinkAny

  behavior.of("Avro TCP")

  it should "send batches" in {
    forAll { (process: TraceProcess, batch: Batch) =>
      val ref = Ref.unsafe[IO, Option[Batch]](None)

      (for {
        server <- AvroServer.tcp[IO](blocker, _.evalMap { b =>
          ref.set(Some(b))
        })
        _ <- server.compile.drain.background
        _ <- Resource.liftF(timer.sleep(2.seconds))
        completer <- AvroSpanCompleter.tcp[IO](blocker, process)
      } yield completer).use(_.completeBatch(batch) >> timer.sleep(3.seconds)).unsafeRunSync()

      assert(Eq[Option[Batch]].eqv(ref.get.unsafeRunSync(), Some(batch)))
    }
  }

  it should "send indvidual spans in batches" in {
    forAll { (process: TraceProcess, spans: NonEmptyList[CompletedSpan]) =>
      val ref = Ref.unsafe[IO, Option[Batch]](None)

      (for {
        server <- AvroServer.tcp[IO](blocker, _.evalMap { b =>
          ref.set(Some(b))
        }, port = 7778)
        _ <- server.compile.drain.background
        _ <- Resource.liftF(timer.sleep(2.seconds))
        completer <- AvroSpanCompleter.tcp[IO](blocker, process, port = 7778, batchTimeout = 1.second)
      } yield completer).use(c => spans.traverse(c.complete) >> timer.sleep(3.seconds)).unsafeRunSync()

      assert(Eq[Option[Batch]].eqv(ref.get.unsafeRunSync(), Some(Batch(process, spans.toList))))
    }
  }

  behavior.of("Avro UDP")

  it should "send batches" in {
    forAll { (process: TraceProcess, batch: Batch) =>
      val ref = Ref.unsafe[IO, Option[Batch]](None)

      (for {
        server <- AvroServer.udp[IO](blocker, _.evalMap { b =>
          ref.set(Some(b))
        })
        _ <- server.compile.drain.background
        _ <- Resource.liftF(timer.sleep(1.second))
        completer <- AvroSpanCompleter.udp[IO](blocker, process)
      } yield completer).use(_.completeBatch(batch) >> timer.sleep(3.seconds)).unsafeRunSync()

      assert(Eq[Option[Batch]].eqv(ref.get.unsafeRunSync(), Some(batch)))
    }
  }

  it should "send indvidual spans in batches" in {
    forAll { (process: TraceProcess, spans: NonEmptyList[CompletedSpan]) =>
      val ref = Ref.unsafe[IO, Option[Batch]](None)

      (for {
        server <- AvroServer.udp[IO](blocker, _.evalMap { b =>
          ref.set(Some(b))
        }, port = 7778)
        _ <- server.compile.drain.background
        _ <- Resource.liftF(timer.sleep(1.second))
        completer <- AvroSpanCompleter.udp[IO](blocker, process, port = 7778, batchTimeout = 1.second)
      } yield completer).use(c => spans.traverse(c.complete) >> timer.sleep(3.seconds)).unsafeRunSync()

      assert(Eq[Option[Batch]].eqv(ref.get.unsafeRunSync(), Some(Batch(process, spans.toList))))
    }
  }
}
