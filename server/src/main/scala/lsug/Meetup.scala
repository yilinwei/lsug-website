package lsug

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import scala.collection.mutable
import cats.implicits._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.implicits._
import org.http4s.Method._
import org.http4s.headers._
import org.http4s._
import cats.effect._

import lsug.protocol.Meetup.{MeetupDotCom => P}
import lsug.protocol.Link
import scala.util.matching.Regex
import cats.Monad

object Meetup {
  val eventMap: mutable.Map[String, (Int, LocalDateTime)] =
    mutable.Map.empty
  def apply[F[_]: Sync](group: P.Group.Id, client: Client[F]): Meetup[F] = {
    val dsl = new Http4sClientDsl[F] {}
    import dsl._
    new Meetup[F] {
      val baseUri = uri"https://www.meetup.com"
      def event(id: P.Event.Id) = {
        //println(s"Event Id show: ${id.show}")
        val now = LocalDateTime.now()
        val uri: Uri = baseUri
          .withPath(s"/${group.show}/events/${id.show}")
        eventMap.get(id.show) match {
          case Some(e) if (ChronoUnit.SECONDS.between(e._2, now) <= 60) =>
            F(Option((P.Event(new Link(uri.renderString), e._1))))
          case _ => {
            val c: F[String] = client
              .expect[String](
                GET(
                  uri,
                  Accept(MediaRange.`*/*`)
                )
              )
            val r: F[Option[lsug.protocol.Meetup.MeetupDotCom.Event]] = c.map {
              page =>
                val reg: Regex = "(?<=Attendees \\()[0-9]+(?=\\))".r
                reg
                  .findFirstIn(page)
                  .map(_.toInt)
                  .map { at =>
                    eventMap.update(id.show, (at, now))
                    P.Event(new Link(uri.renderString), at)
                  }
            }
            r
          }
        }
      }
    }
  }

}

trait Meetup[F[_]] {
  def event(id: P.Event.Id): F[Option[P.Event]]
}
