package io.janstenpickle.trace4cats.http4s.common

import io.janstenpickle.trace4cats.model.{AttributeValue, TraceHeaders}
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Header, Headers}

object Http4sHeaders {
  def headerFields(
    headers: Headers,
    `type`: String,
    dropWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
  ): List[(String, AttributeValue)] =
    headers.toList.collect {
      case h if !dropWhen(h.name) => s"${`type`}.header.${h.name.value}" -> AttributeValue.stringToTraceValue(h.value)
    }

  def requestFields(
    req: Request_,
    dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
  ): List[(String, AttributeValue)] =
    List[(String, AttributeValue)]("http.method" -> req.method.name, "http.url" -> req.uri.path) ++ headerFields(
      req.headers,
      "req",
      dropHeadersWhen
    )

  def responseFields(
    resp: Response_,
    dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
  ): List[(String, AttributeValue)] =
    List[(String, AttributeValue)](
      "http.status_code" -> resp.status.code,
      "http.status_message" -> resp.status.reason
    ) ++ headerFields(resp.headers, "resp", dropHeadersWhen)

  val converter: TraceHeaders.Converter[Headers] = new TraceHeaders.Converter[Headers] {
    def from(t: Headers): TraceHeaders =
      TraceHeaders(t.toList.map(h => h.name.value -> h.value).toMap)
    def to(h: TraceHeaders): Headers =
      Headers(h.values.map { case (k, v) => Header(k, v) }.toList)
  }
}
