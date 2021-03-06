package io.janstenpickle.trace4cats.strackdriver

import cats.Foldable
import cats.effect.{Blocker, Concurrent, ConcurrentEffect, Resource, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.foldable._
import io.chrisdavenport.log4cats.Logger
import io.janstenpickle.trace4cats.`export`.HttpSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import io.janstenpickle.trace4cats.strackdriver.oauth.{
  CachedTokenProvider,
  InstanceMetadataTokenProvider,
  OAuthTokenProvider,
  TokenProvider
}
import io.janstenpickle.trace4cats.strackdriver.project.{
  InstanceMetadataProjectIdProvider,
  ProjectIdProvider,
  StaticProjectIdProvider
}
import org.http4s.Uri
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

import scala.collection.mutable.ListBuffer

object StackdriverHttpSpanExporter {
  private final val base = "https://cloudtrace.googleapis.com/v2/projects"

  def blazeClient[F[_]: ConcurrentEffect: Timer: Logger, G[_]: Foldable](
    blocker: Blocker,
    projectId: String,
    serviceAccountPath: String,
  ): Resource[F, SpanExporter[F, G]] =
    BlazeClientBuilder[F](blocker.blockingContext).resource.evalMap(apply[F, G](projectId, serviceAccountPath, _))

  def blazeClient[F[_]: ConcurrentEffect: Timer: Logger, G[_]: Foldable](
    blocker: Blocker,
    serviceAccountName: String = "default"
  ): Resource[F, SpanExporter[F, G]] =
    BlazeClientBuilder[F](blocker.blockingContext).resource.evalMap(apply[F, G](_, serviceAccountName))

  def apply[F[_]: Concurrent: Timer: Logger, G[_]: Foldable](
    projectId: String,
    serviceAccountPath: String,
    client: Client[F]
  ): F[SpanExporter[F, G]] =
    OAuthTokenProvider[F](serviceAccountPath, client).flatMap { tokenProvider =>
      apply[F, G](StaticProjectIdProvider(projectId), tokenProvider, client)
    }

  def apply[F[_]: Concurrent: Timer: Logger, G[_]: Foldable](
    client: Client[F],
    serviceAccountName: String = "default"
  ): F[SpanExporter[F, G]] =
    apply[F, G](
      InstanceMetadataProjectIdProvider(client),
      InstanceMetadataTokenProvider(client, serviceAccountName),
      client
    )

  def apply[F[_]: Concurrent: Timer, G[_]: Foldable](
    projectIdProvider: ProjectIdProvider[F],
    tokenProvider: TokenProvider[F],
    client: Client[F]
  ): F[SpanExporter[F, G]] =
    for {
      cachedTokenProvider <- CachedTokenProvider(tokenProvider)
      projectId <- projectIdProvider.projectId
      exporter <- HttpSpanExporter[F, G, model.Batch](
        client,
        s"$base/$projectId/traces:batchWrite",
        (batch: Batch[G]) =>
          model.Batch(
            batch.spans
              .foldLeft(ListBuffer.empty[model.Span]) { (buf, span) =>
                buf += model.Span.fromCompleted(projectId, span)
              }
              .toList
          ),
        (uri: Uri) =>
          cachedTokenProvider.accessToken.map { token =>
            uri.withQueryParam("access_token", token.accessToken)
          }
      )
    } yield exporter
}
